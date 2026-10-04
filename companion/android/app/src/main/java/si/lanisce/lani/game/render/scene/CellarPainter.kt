package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V kleti: the wine cellar of a farmhouse (Vinar Marko keeps his wine there) as a diorama, the kitchen's room
 * ([RoomPainter]) built of rough stone. The barrels lie on their rack along the left wall, their heads to the room, the
 * first with its tap and the jug under it; in the middle the wine press, its slatted basket and its great screw; a small
 * table of a plank on two stumps with the glass and the candle; the heavy door high in the right wall with stone steps
 * down from it, a small barred window, the shelf of bottles between them; the crate by the press, the demijohn in its
 * wicker. It is dim: the candle and the window are its light.
 *
 * By the house's level ([si.lanisce.lani.game.scene.SceneFixtures]): at level 1 one barrel, the press, the steps, the
 * door and the window, the candle, the jug and the glass, an empty crate, on a beaten earth floor; at level 2 stone flags,
 * a second barrel, the shelf of bottles, the grapes in the crate, the demijohn of wine; at level 3 a third barrel, the
 * pršut hanging on the wall and a wheel of cheese on the table. Its effects: `tap` (wine running from the tap into the
 * jug, filling it), `candle` (0 out; lit, its flame flaring up the higher, as when it has just been lit; not set: lit,
 * calm).
 *
 * At night ([beds]: "bench") Marko sleeps on a plank bench before the barrels, keeping an eye on the fermenting wine, his
 * head on a folded sack by the first barrel, under a wine-red wool blanket. Čebelar Anton, who lodges with him (his honey
 * and his wax keep in the cool cellar too), sleeps on a trestle bed between the press and the front ("trestle"), his head
 * toward the steps, on a straw mattress under a honey-coloured wool blanket with a brown check. Each is there only while
 * someone is on it, and the candle burns low unless a dialog set it.
 */
internal class CellarPainter : RoomPainter() {
    override val art = "cellar"

    override val winA = 3.6f
    override val winB = 4.6f
    override val winZ0 = fl + 15.5f
    override val winZ1 = fl + 20.5f
    override val doorA = 6.9f
    override val doorB = 8.35f

    // the barrels: along the left wall, their heads to the room at BX1, lying on the rack at their middle BZ, RY cells across
    // and RZ px high; the tasting table's top
    private val bx0 = x0 + 0.15f; private val bx1 = x0 + 2.2f; private val bz = fl + 8.4f; private val ry = 0.95f; private val rz = 7f
    private val barrels = floatArrayOf(2.95f, 5.25f, 7.55f)
    // the first barrel's tap, high enough on its head for the jug to stand under it
    private val tapZ = bz - rz * 0.12f
    private val doorFoot = fl + 4f
    private val tabX = 4.5f; private val tabY = 7.0f; private val tabZ = fl + 5.4f

    private val stoneL = Col.hex(0x9A8E7E); private val stoneM = Col.hex(0x807466); private val stoneD = Col.hex(0x625848); private val mortar = Col.hex(0x3E362E)

    override fun soot(d: Float, z: Float): Float = 0f

    /** A cellar is dim: the candle and the window light it. */
    override fun ambient(): FloatArray {
        val a = super.ambient()
        return floatArrayOf(a[0] * 0.8f, a[1] * 0.8f, a[2] * 0.82f)
    }

    /** Rough stones in lime mortar, damp and greenish low down, a darker band under the top. */
    override fun inner(along: Float, z: Float, px: Int, py: Int, dim: Float, sootF: Float): Int {
        val h = z - fl
        val course = floor(h / 2.6f).toInt()
        val u = (along + if (course % 2 == 0) 0f else 0.31f) / 0.62f
        val stone = floor(u).toInt()
        val fu = u - stone; val fv = h / 2.6f - course
        val n = Noise.rnd(px, py, 61)
        var col = when {
            z > hw - 0.6f -> Col.hex(0xB0A490)
            fv < 0.12f || fu < 0.08f -> mortar
            fv > 0.88f -> stoneD
            else -> {
                val tone = Noise.rnd(stone, course, 63)
                val base = if (tone < 0.3f) stoneL else if (tone < 0.7f) stoneM else Col.hex(0x8C7E6C)
                if (fu < 0.2f && fv > 0.2f) Col.mix(base, Col.hex(0xFFFFFF), 0.12f) else if (n > 0.95f) Col.scale(base, 0.85f) else base
            }
        }
        if (h < 3.5f && Dither.at(px, py) < (3.5f - h) / 5f) col = Col.mix(col, Col.hex(0x5E6A48), 0.35f)
        if (z > hw - 4f) col = Col.scale(col, 0.88f)
        return Col.scale(col, dim)
    }

    /** Beaten earth at level 1; stone flags from level 2, worn and uneven, a drain in the middle. */
    override fun floor() {
        if (lv < 2) { super.floor(); return }
        floorId = s.newObject(Pal.OUTLINE)
        onFlat(fl, x0, y0, x1, y1) { x, y, px, py ->
            val row = floor((y - y0) / 1.1f).toInt()
            val u = (x - x0 + (if (row % 2 == 0) 0f else 0.6f)) / 1.3f
            val slab = floor(u).toInt()
            val fu = u - slab; val fv = (y - y0) / 1.1f - row
            val n = Noise.rnd(px, py, 67)
            when {
                hypot(x - 5.2f, y - 4.6f) < 0.18f -> if (((px + py) / max(1, P)) % 2 == 0) Col.hex(0x2A2622) else Col.hex(0x4A443C)
                fu < 0.05f || fv < 0.06f -> Col.hex(0x3E3830)
                else -> {
                    val tone = Noise.rnd(slab, row, 69)
                    val base = if (tone < 0.35f) Col.hex(0x8A8272) else if (tone < 0.7f) Col.hex(0x7A7264) else Col.hex(0x968C7C)
                    if (fu < 0.12f || fv < 0.14f) Col.mix(base, Col.hex(0xFFFFFF), 0.1f) else if (n > 0.97f) Col.scale(base, 0.85f) else base
                }
            }
        }
        c.penId = 0
    }

    /** At night Marko sleeps on a plank bench before the barrels, keeping an eye on the wine as it ferments; Anton on a trestle bed. */
    override val beds = listOf(BENCH, TRESTLE)

    override fun paint() {
        fit()
        vignette()
        lv = frame.world.hereLevel.coerceIn(1, 3)
        // the candle: out at 0, lit otherwise; set, its flame flares up (just lit), not set it burns calm, low while Marko sleeps
        val candle = fx("candle")?.let { it > 0.02f } ?: true
        val flare = if (candle) fxOn("candle").coerceIn(0f, 1f) else 0f
        val low = candle && fx("candle") == null && asleepHere
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
        rugId = floorId
        walls()
        thing("window") { slit() }
        thing("door") { cellarDoor() }
        if (there("shelf")) thing("shelf") { bottleShelf() }
        if (there("bottle")) thing("bottle", slop = 1) { bottles() }
        if (there("ham")) thing("ham", slop = 1) { ham() }
        thing("stairs") { steps() }
        for (p in peopleAt("stairs")) { footShadow(7.6f, 2.6f); personAt(p, 7.6f, 2.6f, fl, flip = true) }

        // the rack and the barrels on it, the first with its tap, the jug under it
        prop { rack() }
        if (lv >= 3) prop { barrel(barrels[2], tap = false) }
        if (lv >= 2) prop { barrel(barrels[1], tap = false) }
        thing("barrel") { barrel(barrels[0], tap = true) }
        wine(fxOn("tap"))
        thing("jug", slop = 1) { jug(bx1 + 0.35f, barrels[0] + 0.15f, fl) }
        // Marko asleep on his bench before the barrels, his head on a folded sack, under the wine-red blanket
        peopleAt(BENCH).firstOrNull()?.let { p ->
            val top = plankBench()
            sleeperOn(p, BEN_A, BEN_C, BEN_B, BEN_D, top, alongY = true, WINE_WOOL)
        }
        for (p in peopleAt("barrel")) { footShadow(3.9f, 4.8f); personAt(p, 3.9f, 4.8f, fl, flip = true) }

        // the press, Anton asleep on his trestle bed beside it, the crate and the grapes in it, the demijohn
        thing("press") { press(6.55f, 4.55f) }
        peopleAt(TRESTLE).firstOrNull()?.let { p ->
            val top = trestleBed()
            sleeperOn(p, TR_A, TR_C, TR_B, TR_D, top, alongY = true, HONEY_WOOL)
        }
        if (there("wine")) thing("wine", slop = 1) { demijohn(8.1f, 6.4f) }
        for (p in peopleAt("press")) { footShadow(7.55f, 5.35f); personAt(p, 7.55f, 5.35f, fl, flip = true) }
        thing("crate") { crate(6.0f, 7.2f) }
        if (there("grapes")) thing("grapes", slop = 1) { grapes(6.0f, 7.2f) }
        // the small table with the glass, the candle and the cheese
        prop { tastingTable() }
        if (there("cheese")) thing("cheese", slop = 1) { cheese(tabX - 0.35f, tabY - 0.1f, tabZ) }
        thing("glass", slop = 2) { wineGlass(tabX + 0.25f, tabY + 0.2f, tabZ) }
        thing("candle", slop = 2) { candleOn(tabX + 0.45f, tabY - 0.3f, tabZ, candle, flare, low) }

        stubs()
        prop { gardenBench() }
        if (lv >= 2) prop { pots() }
        prop { vegetables() }
        if (env.dark < 0.5f) prop { hen(henX, henY) }
        fireflies(iso.ix(0f, 12f), iso.iy(12f, 12f, 20f), iso.ix(12f, 0f), iso.iy(12f, 12f, 0f), 12, 5)

        if (candle) s.light(iso.sx(tabX + 0.45f, tabY - 0.3f), iso.sy(tabX + 0.45f, tabY - 0.3f, tabZ + 4f), if (low) 22f else 34f * (1f + 0.5f * flare), if (low) 0.6f else 0.85f + 0.15f * flare)
    }

    /** Marko's bench for the night: a thick plank on four legs before the barrels, a folded sack on it for a pillow. Returns its top. */
    private fun plankBench(): Float {
        val top = fl + 3.6f
        prop {
            for (ly in floatArrayOf(BEN_C + 0.2f, BEN_D - 0.2f)) for (lx in floatArrayOf(BEN_A + 0.12f, BEN_B - 0.12f)) iso.post(lx, ly, fl, top - 0.9f, Pal.WOOD_D)
            iso.box(BEN_A, BEN_C, top - 0.9f, BEN_B - BEN_A, BEN_D - BEN_C, 0.9f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
            if (P > 1) grain(BEN_A, BEN_C, BEN_B, BEN_D, top, 67, alongY = true)
            iso.line(BEN_B, BEN_C, top, BEN_B, BEN_D, top, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.25f))
            // the sack folded under his head, a seam across it
            iso.box(BEN_A + 0.1f, BEN_C + 0.08f, top, BEN_B - BEN_A - 0.2f, 0.62f, 1.1f, Col.hex(0xC8A870), Col.hex(0xA88A58), Col.hex(0x7A6240))
            iso.line(BEN_A + 0.1f, BEN_C + 0.4f, top + 1.1f, BEN_B - 0.1f, BEN_C + 0.4f, top + 1.1f, Col.hex(0x8A7048))
        }
        return top
    }

    /**
     * Anton's trestle bed for the night: planks on two trestles (their legs splayed), a straw mattress on them, a pillow at
     * the head toward the steps. Returns the mattress's top.
     */
    private fun trestleBed(): Float {
        val pz = fl + 2.4f; val mh = 1.3f
        prop {
            // the trestles: a beam across under each end on two splayed legs, the far one first
            for (ty in floatArrayOf(TR_C + 0.3f, TR_D - 0.3f)) {
                for (lx in floatArrayOf(TR_A + 0.1f, TR_B - 0.1f)) {
                    val out = if (lx < (TR_A + TR_B) / 2f) -0.12f else 0.12f
                    rod(lx + out, ty, fl, lx, ty, pz - 0.6f, Pal.WOOD_D, Pal.WOOD_M, Pal.WOOD_D, Pal.WOOD_X)
                }
                iso.box(TR_A, ty - 0.1f, pz - 0.9f, TR_B - TR_A, 0.2f, 0.4f, Pal.WOOD_M, Pal.WOOD_D, Pal.WOOD_X)
            }
            // the planks over them, lit along the edge we see
            iso.box(TR_A, TR_C, pz - 0.5f, TR_B - TR_A, TR_D - TR_C, 0.5f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
            if (P > 1) grain(TR_A, TR_C, TR_B, TR_D, pz, 71, alongY = true)
            iso.line(TR_B, TR_C, pz, TR_B, TR_D, pz, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.25f))
            mattress(TR_A + 0.04f, TR_C + 0.04f, TR_B - 0.04f, TR_D - 0.04f, alongY = true, z0 = pz, h = mh)
        }
        return pz + mh
    }

    private companion object {
        /** A plank bench, where Marko sleeps at night by the barrels ("bench"). */
        const val BENCH = "bench"

        /** Anton's trestle bed, planks on two trestles by the steps ("trestle"). */
        const val TRESTLE = "trestle"

        /** Marko's bench before the barrels' heads, along y, between the jug and the table. */
        const val BEN_A = 3.3f; const val BEN_B = 4.15f; const val BEN_C = 3.85f; const val BEN_D = 6.25f

        /** Anton's trestle bed between the press and the front, along y, its head toward the steps (clear of them). */
        const val TR_A = 7.8f; const val TR_B = 8.75f; const val TR_C = 3.4f; const val TR_D = 5.75f

        /** Marko's blanket: wine-red wool, an ochre check. */
        val WINE_WOOL = intArrayOf(Col.hex(0xA04E5C), Col.hex(0x7C2E3E), Col.hex(0x4E1A26), Col.hex(0xD8A850))

        /** Anton's blanket: wool the colour of chestnut honey, a brown check. */
        val HONEY_WOOL = intArrayOf(Col.hex(0xE0A83E), Col.hex(0xB8822A), Col.hex(0x7A5418), Col.hex(0x6A4426))
    }

    // ------------------------------------------------------------------ the right wall

    /** The small window high in the wall: a deep stone reveal, three iron bars, the day (or the night) outside. */
    private fun slit() {
        onY(y0, winA, winB, winZ0, winZ1) { _, _, _, _ -> Col.scale(stoneL, 0.78f) }
        onY(y0 - 0.3f, winA, winB, winZ0, winZ1) { x, z, px, py ->
            val fx = wallYx(px, y0); val fz = wallYz(px, py, y0)
            if (fx < winA || fx > winB || fz < winZ0 || fz > winZ1) 0 else {
                val u = (x - winA) / (winB - winA); val v = (z - winZ0) / (winZ1 - winZ0)
                val bar = abs(u * 4f - floor(u * 4f + 0.5f)) < 0.09f && u > 0.1f && u < 0.9f
                if (bar) { c.penEmissive = false; if (u * 4f - floor(u * 4f + 0.5f) < 0f) ironL else ironD }
                else { c.penEmissive = true; view(u, v, px, py) }
            }
        }
        c.penEmissive = false
        iso.box(winA - 0.1f, y0 - 0.1f, winZ0 - 0.6f, winB - winA + 0.2f, 0.45f, 0.6f, stoneL, stoneM, stoneD)
    }

    /** The heavy door high in the wall, its sill up where the yard is: planks under iron bands and nails, a rounded head, a ring. */
    private fun cellarDoor() {
        val top = fl + 19.5f
        onY(y0 + 0.02f, doorA - 0.25f, doorB + 0.25f, doorFoot, top + 1.2f) { x, z, _, _ ->
            val mid = (doorA + doorB) / 2f; val half = (doorB - doorA) / 2f
            val arch = top - 2.4f + 2.4f * sqrt(max(0f, 1f - ((x - mid) / half) * ((x - mid) / half)))
            val inDoor = x in doorA..doorB && z < arch
            val inFrame = x in (doorA - 0.25f)..(doorB + 0.25f) && z < arch + 1f
            when {
                inDoor -> {
                    val a = x - doorA; val h = z - doorFoot
                    val board = floor(a / 0.29f).toInt()
                    when {
                        abs(h - 3f) < 0.45f || abs(h - (arch - doorFoot - 3.5f)) < 0.45f -> if (Math.floorMod(floor(a * 8f).toInt(), 3) == 0) ironL else iron
                        abs(a - (doorB - doorA - 0.25f)) < 0.1f && abs(h - 7f) < 0.8f -> ironL
                        a - board * 0.29f < 0.05f -> Col.hex(0x4A3020)
                        else -> if (Noise.rnd(board, 71) < 0.5f) Col.hex(0x6A4428) else Col.hex(0x5E3C24)
                    }
                }
                inFrame -> if (z > arch + 0.5f || x < doorA - 0.12f || x > doorB + 0.12f) stoneL else stoneD
                else -> 0
            }
        }
    }

    /** Stone steps from the door down to the floor, three of them, worn in the middle. */
    private fun steps() {
        val xa = doorA - 0.1f; val xb = doorB + 0.1f
        val n = 3
        for (k in 0 until n) {
            val top = doorFoot - (doorFoot - fl) * k / n
            val ya = y0 + k * 0.42f
            iso.box(xa, ya, fl, xb - xa, 0.42f + (if (k == n - 1) 0.05f else 0f), top - fl, stoneL, stoneM, stoneD)
            iso.line(xa, ya + 0.42f, top, xb, ya + 0.42f, top, Col.mix(stoneL, Col.hex(0xFFFFFF), 0.2f))
            val cx = iso.sx((xa + xb) / 2f, ya + 0.2f); val cy = iso.sy((xa + xb) / 2f, ya + 0.2f, top)
            c.fillEllipse(cx, cy, 5f * P, 1.2f * P, Col.mix(stoneL, stoneM, 0.5f))
        }
    }

    /** The shelf of bottles between the window and the door (level 2): two planks on brackets. */
    private fun bottleShelf() {
        val xa = 4.95f; val xb = 6.55f
        for (z in floatArrayOf(fl + 7.5f, fl + 12f)) {
            for (bx in floatArrayOf(xa + 0.25f, xb - 0.25f)) rod(bx, y0, z - 2.6f, bx, y0 + 0.6f, z - 0.5f, Pal.WOOD_D, Pal.WOOD_M, Pal.WOOD_D, Pal.WOOD_X)
            iso.box(xa, y0, z - 0.5f, xb - xa, 0.75f, 0.5f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        }
    }

    /** The bottles on the shelf (level 2): dark green glass, their shoulders and necks, a glint, corks. */
    private fun bottles() {
        val p = P.toFloat()
        for ((row, z) in listOf(0 to fl + 7.5f, 1 to fl + 12f)) for (k in 0 until 5) {
            val x = 5.15f + k * 0.3f + row * 0.1f; val y = y0 + 0.4f
            val cx = iso.sx(x, y); val by = iso.sy(x, y, z)
            val g = if ((k + row) % 3 == 0) Col.hex(0x5A2E2A) else Col.hex(0x2E4A2E)
            c.fillRect(floor(cx - 1f * p).toInt(), floor(by - 5f * p).toInt(), max(2, floor(2f * p).toInt()), floor(5f * p).toInt(), g)
            c.fillRect(floor(cx - 0.5f * p).toInt(), floor(by - 7f * p).toInt(), max(1, P), floor(2f * p).toInt(), g)
            c.fillRect(floor(cx - 0.5f * p).toInt(), floor(by - 7.8f * p).toInt(), max(1, P), max(1, P), Col.hex(0xC8A070))
            c.vline(floor(cx - 0.8f * p).toInt(), floor(by - 4.6f * p).toInt(), floor(by - 1.4f * p).toInt(), Col.mix(g, Col.hex(0xFFFFFF), 0.4f))
        }
    }

    /** The pršut hanging on its peg in the wall (level 3): the leg, dark red under a white rind of fat, the hoof on its string. */
    private fun ham() {
        val x = 2.5f; val y = y0 + 0.25f
        val cx = iso.sx(x, y); val top = iso.sy(x, y, fl + 20f)
        val p = P.toFloat()
        iso.px(x, y0 + 0.05f, fl + 20.5f, ironD)
        c.vline(floor(cx).toInt(), floor(top).toInt(), floor(top + 2f * p).toInt(), Col.hex(0xD8C8A0))
        c.fillEllipse(cx, top + 3f * p, 0.9f * p, 1.4f * p, Col.hex(0x3A2418))
        c.fillEllipse(cx, top + 8.5f * p, 3.2f * p, 5.2f * p, Col.hex(0xE8DCC4))
        c.fillEllipse(cx + 0.4f * p, top + 9.2f * p, 2.6f * p, 4.4f * p, Col.hex(0x9A3A2E))
        c.fillEllipse(cx + 0.8f * p, top + 10.2f * p, 1.6f * p, 2.8f * p, Col.hex(0xB84A3A))
        c.fillEllipse(cx - 1.4f * p, top + 7f * p, 0.8f * p, 2.6f * p, Col.hex(0xF4EEDC))
    }

    // ------------------------------------------------------------------ the barrels

    /** The rack the barrels lie on: two squared timbers along the left wall, a chock under each barrel. */
    private fun rack() {
        for (rx in floatArrayOf(x0 + 0.45f, x0 + 1.75f)) iso.box(rx - 0.18f, 1.8f, fl, 0.36f, 7.2f, 1.3f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
    }

    /**
     * A barrel lying on the rack, its middle at [yc], its head to the room: staves bulging to the middle, bound with iron
     * hoops, lit along its top; the head of boards with a rim, chalked with the year; the first ([tap]) with its brass tap.
     */
    private fun barrel(yc: Float, tap: Boolean) {
        val n = 8 * K
        val m = 16 // a polygon of the canvas takes at most 16 points
        val light = Col.hex(0xB07A48); val mid = Col.hex(0x8A5A34); val dark = Col.hex(0x5E3A20)
        for (i in 0 until n) {
            val f = i / (n - 1f)
            val xs = bx0 + (bx1 - bx0) * f
            val bulge = 1f + 0.08f * sin(f * PI.toFloat())
            val hoop = abs(f - 0.12f) < 0.035f || abs(f - 0.33f) < 0.03f || abs(f - 0.67f) < 0.03f || abs(f - 0.88f) < 0.035f
            val cx = iso.sx(xs, yc); val cy = iso.sy(xs, yc, bz)
            c.polyBegin()
            for (j in 0 until m) {
                val a = j * 2f * PI.toFloat() / m
                val u = cos(a) * ry * bulge; val w = sin(a) * rz * bulge
                c.polyAdd(cx - 4f * K * u, cy + 2f * K * u - K * w)
            }
            c.polyFill(if (hoop) iron else mid)
            // the rim of this slice: lit on top, dark below; what the next slice leaves of it is the barrel's side
            for (j in 0 until m) {
                val a = j * 2f * PI.toFloat() / m
                val u = cos(a) * ry * bulge; val w = sin(a) * rz * bulge
                val shadeUp = sin(a) - 0.35f * cos(a)
                val col = when {
                    hoop -> if (shadeUp > 0.3f) ironL else if (shadeUp < -0.4f) ironD else iron
                    shadeUp > 0.55f -> light
                    shadeUp < -0.45f -> dark
                    P > 1 && Math.floorMod(j, 4) == 0 -> Col.mix(mid, dark, 0.45f)
                    else -> mid
                }
                val x = (cx - 4f * K * u).toInt(); val y = (cy + 2f * K * u - K * w).toInt()
                c.fillRect(x - P / 2, y - P / 2, max(1, P), max(1, P), col)
            }
        }
        // the head
        val bulge = 1f
        onX(bx1 + 0.02f, yc - ry * 1.1f, yc + ry * 1.1f, bz - rz * 1.1f, bz + rz * 1.1f) { y, z, px, py ->
            val u = (y - yc) / (ry * bulge); val v = (z - bz) / (rz * bulge)
            val r = u * u + v * v
            when {
                r > 1f -> 0
                r > 0.8f -> if (v > 0.2f) Col.hex(0x9A6A40) else dark
                P > 1 && abs(u * 3.4f - floor(u * 3.4f + 0.5f)) < 0.06f -> Col.hex(0x6A4428)
                abs(u - 0.1f) < 0.25f && abs(v - 0.25f) < 0.12f && (px / max(1, P) + py / max(1, P)) % 3 != 0 -> Col.hex(0xECE6D8) // the chalked year
                else -> if (Noise.v2(u * 3f, v * 6f, 73) > 0.66f) Col.scale(Col.hex(0x9A6A40), 0.9f) else Col.hex(0x9A6A40)
            }
        }
        if (!tap) return
        // the tap on the head, over the jug's mouth: brass, its key on top
        val tx = bx1 + 0.12f; val tz = tapZ
        rod(tx, yc, tz, tx + 0.3f, yc, tz, brassD, brass, brass, brassD)
        rod(tx + 0.3f, yc, tz, tx + 0.3f, yc, tz - 1.2f, brassD, brass, brass, brassD)
        knob(tx + 0.18f, yc, tz + 0.8f, brass, brassL, brassD)
    }

    /**
     * The wine running from the first barrel's tap into the jug under it ([level] > 0), dark red, rippling, and the jug
     * filling: the wine's surface in its mouth, rings spreading where the stream falls in.
     */
    private fun wine(level: Float) {
        if (level <= 0.02f) return
        val x = iso.sx(bx1 + 0.42f, barrels[0]); val top = iso.sy(bx1 + 0.42f, barrels[0], tapZ - 1.2f)
        val bot = iso.sy(bx1 + 0.42f, barrels[0], fl + 5f)
        val jx = iso.sx(bx1 + 0.35f, barrels[0] + 0.15f); val jy = iso.sy(bx1 + 0.35f, barrels[0] + 0.15f, fl + 5f)
        val p = P.toFloat()
        s.fx {
            // the jug's mouth full of wine, a ring spreading from where the stream falls in
            c.fillEllipse(jx, jy, 1.8f * p, 0.5f * p, Col.hex(0x5A1620))
            val ring = ((t * 2.5) % 1.0).toFloat()
            arc(x + 0.5f * p, jy, (0.6f + 1.2f * ring) * p, (0.2f + 0.35f * ring) * p, Col.hex(0xB0303A))
            for (y in top.toInt() until bot.toInt()) {
                val wob = (sin(t * 20.0 + y * 0.7).toFloat() * 0.5f * level).toInt()
                for (o in 0 until max(1, P)) c.set(x.toInt() + wob + o, y, if ((y + (t * 30).toInt()) % 5 == 0) Col.hex(0xB0303A) else Col.hex(0x7A1E28))
            }
        }
    }

    /** The jug under the tap: brown glazed clay, a cream band, a handle. */
    private fun jug(x: Float, y: Float, z: Float) {
        val cx = iso.sx(x, y); val by = iso.sy(x, y, z)
        val p = P.toFloat()
        val gl = Col.hex(0xB47A4A); val g = Col.hex(0x8E5A34); val gd = Col.hex(0x5E3A20)
        loop(cx + 3.2f * p, by - 3.2f * p, 1.6f * p, 2.2f * p, max(1f, 0.8f * p), true, gd, g)
        lathe(cx, by, 5f, { h -> if (h < 3.2f) 2.3f + 0.4f * sin(h / 3.2f * PI.toFloat()) else 1.7f + (h - 3.2f) * 0.2f }) { u, du, h, _, _ ->
            if (abs(h - 2.2f) < 0.4f) Col.hex(0xE8D8B0) else glaze(u, du, gl, g, gd, Col.hex(0xD8A070))
        }
        c.fillEllipse(cx, by - 5f * p, 2.1f * p, 0.6f * p, Col.hex(0x2E1C10))
    }

    // ------------------------------------------------------------------ the floor

    /**
     * The wine press at ([x], [y]): a square tray on the floor with its spout, the round basket of slats with two iron hoops,
     * the pressing board on top, the great screw and the long bar through its head.
     */
    private fun press(x: Float, y: Float) {
        val tz = fl + 1.4f
        iso.box(x - 0.95f, y - 0.95f, fl, 1.9f, 1.9f, 1.4f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        iso.box(x + 0.95f, y - 0.12f, fl + 0.7f, 0.35f, 0.24f, 0.4f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        onFlat(tz, x - 0.85f, y - 0.85f, x + 0.85f, y + 0.85f) { _, _, _, _ -> if (lv >= 2) Col.hex(0x5A2A30) else Col.hex(0x6A4428) }
        val cx = iso.sx(x, y); val base = iso.sy(x, y, tz)
        val p = K.toFloat()
        val rx = 0.62f * 4f * p * 1.4f; val rry = rx * 0.5f
        val top = base - 6.5f * p
        // the basket: slats with gaps, two hoops
        for (xx in floor(cx - rx).toInt()..(cx + rx).toInt()) {
            val u = (xx + 0.5f - cx) / rx
            if (abs(u) > 1f) continue
            val e = sqrt(1f - u * u) * rry
            val slat = Math.floorMod(floor((u + 1f) * 7f * 1.5f).toInt(), 3) != 2
            val col = if (!slat) Col.hex(0x2E1E14) else if (u < -0.4f) Pal.WOOD_L else if (u > 0.45f) Pal.WOOD_D else Pal.WOOD_M
            c.vline(xx, (top + e).toInt(), (base + e).toInt(), col)
            for (hz in floatArrayOf(0.25f, 0.75f)) c.fillRect(xx, (base + e - (base - top) * hz).toInt(), 1, max(1, P), if (u < -0.3f) ironL else iron)
        }
        c.fillEllipse(cx, top, rx, rry, Col.hex(0x3A2418))
        // the pressing board and the screw with its bar
        c.fillEllipse(cx, top - 1f * p, rx * 0.85f, rry * 0.85f, Pal.WOOD_M)
        c.fillEllipse(cx - 0.5f, top - 1.3f * p, rx * 0.75f, rry * 0.7f, Pal.WOOD_L)
        val sTop = top - 9f * p
        for (o in 0 until max(1, P)) c.vline(cx.toInt() + o - P / 2, sTop.toInt(), (top - p).toInt(), if (o == 0) ironL else iron)
        for (k in 0 until 6) c.hline(cx.toInt() - P / 2, cx.toInt() + P / 2, (sTop + k * 1.3f * p).toInt(), ironD)
        c.fillEllipse(cx, sTop, 1.4f * p * 0.6f, 1.4f * p * 0.4f, ironD)
        rod(x - 1.3f, y + 0.4f, fl + 1.4f + 6.5f + 9f - 0.2f, x + 1.3f, y - 0.4f, fl + 1.4f + 6.5f + 9f + 0.2f, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
    }

    /** The demijohn in its wicker basket (level 2): the round glass, the red wine through its neck, the wicker's weave. */
    private fun demijohn(x: Float, y: Float) {
        val cx = iso.sx(x, y); val by = iso.sy(x, y, fl)
        val p = P.toFloat()
        val w = Col.hex(0xC89A5A); val wd = Col.hex(0x9A7240)
        lathe(cx, by, 9f, { h -> 3.6f * sqrt(max(0f, 1f - ((h - 4.2f) / 4.6f) * ((h - 4.2f) / 4.6f))) + 0.3f }) { u, _, h, px, py ->
            if (((px / max(1, P)) + (py / max(1, P))) % 2 == 0) (if (u > 0.4f) wd else w) else (if (u > 0.4f) Col.scale(wd, 0.85f) else Col.hex(0xB08448))
        }
        // the neck: glass, the wine in it, the cork
        c.fillRect(floor(cx - 1f * p).toInt(), floor(by - 12f * p).toInt(), max(2, floor(2f * p).toInt()), floor(3.4f * p).toInt(), Col.hex(0x7A1E28))
        c.vline(floor(cx - 1f * p).toInt(), floor(by - 12f * p).toInt(), floor(by - 8.8f * p).toInt(), Col.hex(0xE8C8D0))
        c.fillRect(floor(cx - 1f * p).toInt(), floor(by - 13f * p).toInt(), max(2, floor(2f * p).toInt()), max(1, P), Col.hex(0xC8A070))
        c.fillEllipse(cx, by - 8.6f * p, 1.6f * p, 0.6f * p, Col.hex(0x9A2E3A))
    }

    /** A wooden crate by the press: slats, a handle hole in its end; empty with a little straw at level 1. */
    private fun crate(x: Float, y: Float) {
        iso.box(x - 0.55f, y - 0.4f, fl, 1.1f, 0.8f, 3.2f, Col.hex(0xD8B07A), Col.hex(0xB88A54), Col.hex(0x8A6038))
        for (k in 1..2) {
            val z = fl + k * 1.07f
            iso.line(x - 0.55f, y + 0.4f, z, x + 0.55f, y + 0.4f, z, Col.hex(0x6A4428))
            iso.line(x + 0.55f, y - 0.4f, z, x + 0.55f, y + 0.4f, z, Col.hex(0x6A4428))
        }
        onFlat(fl + 3.2f, x - 0.47f, y - 0.32f, x + 0.47f, y + 0.32f) { _, _, px, py -> if (Noise.rnd(px, py, 79) < 0.2f) Col.hex(0xD8C07A) else Col.hex(0x5A3E24) }
    }

    /** The grapes heaped in the crate (level 2): dark blue bunches with their bloom, a green leaf. */
    private fun grapes(x: Float, y: Float) {
        val p = P.toFloat()
        for (k in 0 until 14) {
            val gx = x - 0.4f + Noise.rnd(k, 81) * 0.8f; val gy = y - 0.28f + Noise.rnd(k, 82) * 0.56f
            val cx = iso.sx(gx, gy); val cy = iso.sy(gx, gy, fl + 3.4f + Noise.rnd(k, 83) * 1.2f)
            c.fillCircle(cx, cy, 1.1f * p, Col.hex(0x3A2A5A))
            c.fillCircle(cx - 0.3f * p, cy - 0.3f * p, 0.7f * p, Col.hex(0x5A4A8A))
            if (P > 1) c.set(floor(cx - 0.5f * p).toInt(), floor(cy - 0.6f * p).toInt(), Col.hex(0xA8A0C8))
        }
        val lx = iso.sx(x + 0.3f, y - 0.1f); val ly = iso.sy(x + 0.3f, y - 0.1f, fl + 4.6f)
        c.fillEllipse(lx, ly, 2.2f * p, 1.2f * p, Col.hex(0x4E8A3A))
        c.line(floor(lx - 1.8f * p).toInt(), floor(ly).toInt(), floor(lx + 1.8f * p).toInt(), floor(ly).toInt(), Col.hex(0x3A6A2A))
    }

    /** The small table: a plank on two stumps. */
    private fun tastingTable() {
        for (sy in floatArrayOf(tabY - 0.4f, tabY + 0.4f)) {
            val cx = iso.sx(tabX, sy); val yb = iso.sy(tabX, sy, fl); val yt = iso.sy(tabX, sy, tabZ - 0.7f)
            val rx = 2.6f * detail
            for (px in floor(cx - rx).toInt()..(cx + rx).toInt()) {
                val dx = (px + 0.5f - cx) / rx
                if (abs(dx) > 1f) continue
                c.vline(px, yt.toInt(), yb.toInt(), if (dx < -0.4f) Pal.LOG_L else if (dx > 0.4f) Pal.LOG_D else Pal.LOG_M)
            }
        }
        iso.box(tabX - 0.55f, tabY - 0.7f, tabZ - 0.7f, 1.1f, 1.4f, 0.7f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        if (P > 1) grain(tabX - 0.55f, tabY - 0.7f, tabX + 0.55f, tabY + 0.7f, tabZ, 29, alongY = true)
    }

    /** A glass of red wine on the table. */
    private fun wineGlass(x: Float, y: Float, z: Float) {
        val cx = iso.sx(x, y); val by = iso.sy(x, y, z)
        val p = P.toFloat()
        c.fillEllipse(cx, by - 0.3f * p, 1.6f * p, 0.6f * p, Col.hex(0xC8DCE8))
        c.vline(floor(cx).toInt(), floor(by - 2.4f * p).toInt(), floor(by).toInt(), Col.hex(0xD8E8F0))
        lathe(cx, by - 2.4f * p, 3.4f, { h -> 0.8f + h * 0.35f }) { u, _, h, _, _ ->
            when {
                u < -0.75f -> Col.hex(0xFFFFFF)
                h < 2.2f -> if (u > 0.5f) Col.hex(0x5A1620) else Col.hex(0x8A2230)
                else -> Col.hex(0xDCEAF2)
            }
        }
    }

    /**
     * The candle on the table in a tin holder: its flame, lit ([lit]) unless a dialog puts it out; [flare] (0..1) the flame
     * taller and its glow wider, as when it has just been lit; [low] (someone asleep) a small flame, a faint glow.
     */
    private fun candleOn(x: Float, y: Float, z: Float, lit: Boolean, flare: Float, low: Boolean = false) {
        val cx = iso.sx(x, y); val by = iso.sy(x, y, z)
        val p = P.toFloat()
        c.fillEllipse(cx, by - 0.4f * p, 2f * p, 0.8f * p, Col.hex(0x8C8C98))
        c.fillEllipse(cx, by - 0.6f * p, 1.6f * p, 0.55f * p, Col.hex(0xB8BCC6))
        c.fillRect(floor(cx - 0.8f * p).toInt(), floor(by - 5f * p).toInt(), max(2, floor(1.6f * p).toInt()), floor(4.4f * p).toInt(), Col.hex(0xF4EEDC))
        c.vline(floor(cx - 0.8f * p).toInt(), floor(by - 5f * p).toInt(), floor(by - 0.6f * p).toInt(), Col.hex(0xFFFFFF))
        c.fillEllipse(cx + 0.3f * p, by - 3f * p, 0.5f * p, 1.2f * p, Col.hex(0xE8DCC0))
        if (!lit) return
        val big = if (low) 0.55f else 1f + 0.9f * flare
        glow {
            val fl2 = sin(t * (9 + 8 * flare)).toFloat() * (0.3f + 0.4f * flare) * p
            c.fillEllipse(cx + fl2 * 0.3f, by - (5.6f + 0.8f * big) * p, 0.8f * p * (1f + 0.3f * flare), 1.4f * p * big, Pal.FLAME[2])
            c.fillEllipse(cx, by - (5.4f + 0.6f * big) * p, 0.5f * p, 0.8f * p * big, Pal.FLAME[0])
        }
        s.fx {
            c.penEmissive = true
            c.ditherCircle(cx, by - 6f * p, 6f * K * big, Pal.FLAME[1], if (low) 0.08f else 0.12f + 0.06f * flare)
            c.penEmissive = false
        }
    }

    /** A wheel of cheese on the table (level 3), a wedge cut out of it. */
    private fun cheese(x: Float, y: Float, z: Float) {
        val cx = iso.sx(x, y); val by = iso.sy(x, y, z)
        val p = P.toFloat()
        drum(cx, by, by - 2.4f * p, 3.2f * p, 1.4f * p, Col.hex(0xE8C870), Col.hex(0xD8B060), Col.hex(0xB08A40), Col.hex(0xF8E0A0))
        c.fillEllipse(cx, by - 2.4f * p, 3.2f * p, 1.4f * p, Col.hex(0xF4DC8A))
        c.polyBegin(); c.polyAdd(cx, by - 2.4f * p); c.polyAdd(cx + 3.2f * p, by - 2.4f * p); c.polyAdd(cx + 2.4f * p, by - 1.2f * p); c.polyFill(Col.hex(0xC8A050))
        for (k in 0 until 3) c.set(floor(cx - 1.5f * p + k * 1.2f * p).toInt(), floor(by - 2.6f * p).toInt(), Col.hex(0xC8A858))
    }
}
