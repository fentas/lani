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
import kotlin.math.sin

/**
 * V delavnici: the workshop of a farmhouse (Mlinar France's, where he mends what the farm breaks) as a diorama, the
 * kitchen's room ([RoomPainter]) furnished otherwise. Under the window on the right wall the long workbench with its vice,
 * the hammer, a tin of nails and shavings on it; the tool board beside the window, the saw hanging over the bench; the
 * boards leaning on the left wall, the ladder and the rake beside them, the broom in the front corner; on the floor the
 * chopping block with the axe in it and the wheelbarrow; a storm lantern hanging over the bench. The walls are plain
 * whitewash, greyed low down where things lean on them.
 *
 * By the house's level ([si.lanisce.lani.game.scene.SceneFixtures]): at level 1 the workbench, the hammer and nails, the
 * boards, the axe in its block and the broom on a beaten earth floor; at level 2 a plank floor with shavings, the tool
 * board, the saw, the ladder and the rake; at level 3 the wheelbarrow, the wooden horse France carves for Tine on the
 * bench, and the lantern. Its effect: `dust` (sawdust flying off the vice as a board is sawn).
 *
 * At night ([beds]) France sleeps on a straw mattress on the floor before the bench ("mattress"), under a grey horse
 * blanket with a red stripe; Tine on his apprentice's cot by the door ("cot", a child's: a low frame on legs, a headboard,
 * a straw mattress), under the coarse brown blanket. They are there only while someone sleeps on them; the lantern is
 * turned low.
 */
internal class WorkshopPainter : RoomPainter() {
    override val art = "workshop"

    override val winA = 3.2f
    override val winB = 4.9f

    // the workbench along the right wall under the window, its top at WZ
    private val wa = 1.55f; private val wb = 5.45f; private val wy = 2.1f; private val wz = fl + 7.4f

    override fun soot(d: Float, z: Float): Float = 0f

    /** Plain whitewash, greyed and scuffed low down where boards and tools lean on it; no dado and no border. */
    override fun inner(along: Float, z: Float, px: Int, py: Int, dim: Float, sootF: Float): Int {
        val h = z - fl
        val n = Noise.rnd(px, py, 7)
        val scuff = h < 6f && Noise.v2(along * 2f, h * 0.4f, 13) > 0.62f - (6f - h) * 0.03f
        var col = when {
            z > hw - 0.6f -> limeL
            h < 1.1f -> Col.mix(lime, Pal.STONE_M, 0.5f)
            scuff -> Col.mix(lime, Col.hex(0xA89A86), 0.45f)
            n < 0.03f -> limeL
            n > 0.95f -> limeD
            else -> lime
        }
        if (Noise.rnd(px, py, 17) > 0.992f) col = Col.scale(col, 0.85f)
        return Col.scale(col, dim)
    }

    override fun floor() {
        if (lv < 2) { super.floor(); return }
        floorId = s.newObject(Pal.OUTLINE)
        planks(x0, y0, x1, y1, fl, 0.55f)
        c.penId = 0
        // shavings and sawdust under the bench and round the block
        prop(outline = 0) {
            for (k in 0 until 26) {
                val x = if (k < 16) wa + Noise.rnd(k, 31) * (wb - wa) else 3f + Noise.rnd(k, 32) * 1.4f
                val y = if (k < 16) wy + 0.1f + Noise.rnd(k, 33) * 1.1f else 6.6f + Noise.rnd(k, 34) * 1.2f
                val sx = iso.ix(x, y); val sy = iso.iy(x, y, fl)
                val curl = Col.hex(if (k % 3 == 0) 0xE8C88A else 0xD8B474)
                if (P > 1) { arcCurl(sx.toFloat(), sy.toFloat(), curl) } else { c.set(sx, sy, curl); c.set(sx + 1, sy, curl) }
            }
        }
    }

    /** A shaving curled on the floor closer up: a little open ring of pale wood. */
    private fun arcCurl(x: Float, y: Float, col: Int) {
        val p = P.toFloat()
        arc(x, y, 1.2f * p, 0.7f * p, col, 0.3f, 2f * PI.toFloat() - 0.6f)
    }

    /** At night France sleeps on a straw mattress on the floor, Tine on his apprentice's cot by the door. */
    override val beds = listOf(MATTRESS, COT)
    override val childBeds = setOf(COT)

    override fun paint() {
        fit()
        vignette()
        lv = frame.world.hereLevel.coerceIn(1, 3)
        val lit = env.windows > 0.35f && there("lantern")
        // turned low for anyone asleep
        val low = lit && asleepHere
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
        thing("window") { window() }
        thing("door") { door() }
        if (there("tools")) thing("tools", slop = 1) { toolBoard() }
        if (there("saw")) thing("saw", slop = 1) { saw() }
        for (p in peopleAt("door")) { footShadow(7.85f, 1.95f); personAt(p, 7.85f, 1.95f, fl, flip = true) }

        // the left wall: the boards, the ladder, the rake, the broom
        thing("boards") { boards() }
        if (there("ladder")) thing("ladder", slop = 1) { ladder() }
        if (there("rake")) thing("rake", slop = 1) { rake() }
        // the workbench and what is on it, the lantern over it
        thing("workbench") { workbench() }
        thing("nails", slop = 2) { nails(1.95f, 1.45f, wz) }
        thing("hammer", slop = 2) { hammer(2.65f, 1.65f, wz) }
        if (there("horse")) thing("horse", slop = 2) { horse(3.95f, 1.5f, wz) }
        dust(fxOn("dust"))
        if (there("lantern")) thing("lantern", slop = 1) { lantern(lit, low) }
        for (p in peopleAt("bench")) { footShadow(4.65f, 2.95f); personAt(p, 4.65f, 2.95f, fl) }
        // France on his straw mattress on the floor before the bench, under a grey horse blanket
        peopleAt(MATTRESS).firstOrNull()?.let { p ->
            prop { mattress(MAT_A, MAT_C, MAT_B, MAT_D, alongY = false) }
            sleeperOn(p, MAT_A, MAT_C, MAT_B, MAT_D, fl + MAT_H, alongY = false, GREY_WOOL)
        }
        // Tine on his cot by the door, under the coarse brown blanket
        peopleAt(COT).firstOrNull()?.let { p ->
            val top = cot()
            sleeperOn(p, COT_A, COT_C, COT_B, COT_D, top, alongY = true, COARSE)
        }
        // the floor: the wheelbarrow, the chopping block with the axe, the broom in the front corner
        if (there("wheelbarrow")) thing("wheelbarrow") { wheelbarrow() }
        for (p in peopleAt("floor")) { footShadow(5.6f, 5.3f); personAt(p, 5.6f, 5.3f, fl, flip = true) }
        thing("axe", slop = 1) { block(); axe() }
        thing("broom", slop = 1) { broom() }

        stubs()
        prop { gardenBench() }
        if (lv >= 2) prop { pots() }
        prop { vegetables() }
        if (env.dark < 0.5f) prop { hen(henX, henY) }
        fireflies(iso.ix(0f, 12f), iso.iy(12f, 12f, 20f), iso.ix(12f, 0f), iso.iy(12f, 12f, 0f), 12, 5)

        if (lit) s.light(iso.sx(3.9f, 3.1f), iso.sy(3.9f, 3.1f, fl + 17f), if (low) 24f else 44f, (if (low) 0.5f else 0.95f) * env.windows)
        sunPatch()
    }

    /**
     * Tine's cot for the night, an apprentice's: a low frame of boards on four legs, a headboard at its back end, the straw
     * mattress on it; drawn while he sleeps in it. Returns the mattress's top.
     */
    private fun cot(): Float {
        val fz = fl + 2.4f
        prop {
            for ((lx, ly) in listOf(COT_B - 0.1f to COT_D - 0.1f, COT_A + 0.1f to COT_D - 0.1f, COT_B - 0.1f to COT_C + 0.1f)) iso.post(lx, ly, fl, fz, Pal.WOOD_D)
            iso.box(COT_A, COT_C - 0.12f, fl, COT_B - COT_A, 0.12f, 6f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
            iso.box(COT_A, COT_C, fz - 0.9f, COT_B - COT_A, COT_D - COT_C, 0.9f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
            if (P > 1) grain(COT_A, COT_C, COT_B, COT_D, fz, 61, alongY = true)
            mattress(COT_A + 0.05f, COT_C + 0.02f, COT_B - 0.05f, COT_D - 0.05f, alongY = true, z0 = fz, h = 1.3f)
        }
        return fz + 1.3f
    }

    private companion object {
        /** France's straw mattress on the floor before the bench, along x; Tine's cot by the door, along y. */
        const val MAT_A = 2.0f; const val MAT_B = 5.0f; const val MAT_C = 3.55f; const val MAT_D = 4.55f
        const val COT_A = 7.55f; const val COT_B = 8.55f; const val COT_C = 3.0f; const val COT_D = 5.1f

        /** France's horse blanket: grey wool, a red stripe. */
        val GREY_WOOL = intArrayOf(Col.hex(0x9A968A), Col.hex(0x767268), Col.hex(0x4A4640), Col.hex(0xB0503C))
    }

    // ------------------------------------------------------------------ the bench and what is on it

    /**
     * The workbench: a thick top on square legs, a shelf under it with offcuts, the vice at its right end (a wooden jaw, the
     * iron screw and its bar), scratches and a few shavings on the top.
     */
    private fun workbench() {
        val ya = y0 + 0.05f
        for (lx in floatArrayOf(wa + 0.15f, wb - 0.15f)) iso.box(lx - 0.1f, wy - 0.24f, fl, 0.2f, 0.2f, wz - fl - 1.2f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        iso.box(wa + 0.1f, ya + 0.1f, fl + 1.6f, wb - wa - 0.2f, wy - ya - 0.3f, 0.5f, Pal.WOOD_M, Pal.WOOD_D, Pal.WOOD_X)
        iso.box(wa + 0.5f, ya + 0.3f, fl + 2.1f, 0.9f, 0.4f, 0.6f, Col.hex(0xD8B07A), Col.hex(0xB88A54), Col.hex(0x8A6038))
        iso.box(wa + 1.7f, ya + 0.35f, fl + 2.1f, 0.5f, 0.5f, 1.1f, Col.hex(0xD8B07A), Col.hex(0xB88A54), Col.hex(0x8A6038))
        iso.box(wa, ya, wz - 1.2f, wb - wa, wy - ya, 1.2f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        onFlat(wz, wa, ya, wb, wy) { x, y, px, py ->
            val g = Noise.v2(x * 1.4f, y * 7f, 41)
            when {
                P > 1 && abs(g - 0.5f) < 0.02f -> Col.mix(Pal.WOOD_L, Pal.WOOD_M, 0.6f)
                Noise.rnd(px, py, 43) > 0.985f -> Col.hex(0x6A4426)
                hypot(x - 4.4f, (y - 1.5f) * 2f) < 0.35f && Dither.at(px, py) < 0.4f -> Col.hex(0xE8C88A)
                else -> 0
            }
        }
        iso.line(wa, wy, wz, wb, wy, wz, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.25f))
        // the vice
        iso.box(wb - 0.55f, wy, wz - 2.6f, 0.45f, 0.16f, 2.6f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        rod(wb - 0.33f, wy + 0.16f, wz - 1.3f, wb - 0.33f, wy + 0.6f, wz - 1.3f, ironL, ironL, iron, ironD)
        rod(wb - 0.6f, wy + 0.6f, wz - 1.3f, wb - 0.05f, wy + 0.6f, wz - 1.3f, iron, ironL, iron, ironD)
        knob(wb - 0.62f, wy + 0.6f, wz - 1f, ironL, Col.hex(0x9C98A2), ironD)
    }

    /** A little tin of nails, their heads catching the light. */
    private fun nails(x: Float, y: Float, z: Float) {
        val cx = iso.sx(x, y); val by = iso.sy(x, y, z)
        val p = P.toFloat()
        drum(cx, by, by - 3.2f * p, 2.2f * p, 1.1f * p, Col.hex(0xA8A8B4), Col.hex(0x7C7C88), Col.hex(0x54545E), Col.hex(0xD8D8E0))
        c.fillEllipse(cx, by - 3.2f * p, 2.2f * p, 1.1f * p, Col.hex(0x3A3A44))
        for (k in 0 until 5 * P) c.set(floor(cx + (Noise.rnd(k, 47) - 0.5f) * 3f * p).toInt(), floor(by - 3.2f * p + (Noise.rnd(k, 48) - 0.5f) * 1.4f * p).toInt(), if (k % 2 == 0) Col.hex(0xC8C8D2) else Col.hex(0x8C8C98))
    }

    /** The hammer lying on the bench: a wooden handle and the iron head, its claw toward the wall. */
    private fun hammer(x: Float, y: Float, z: Float) {
        rod(x, y + 0.35f, z + 0.3f, x + 0.9f, y - 0.05f, z + 0.3f, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        iso.box(x + 0.75f, y - 0.25f, z, 0.22f, 0.5f, 0.8f, ironL, iron, ironD)
        iso.px(x + 0.86f, y + 0.24f, z + 0.8f, Col.hex(0x9C98A2))
    }

    /** The little wooden horse France carves for Tine (level 3): on the bench, a round body, a curved neck, a painted red saddle. */
    private fun horse(x: Float, y: Float, z: Float) {
        val cx = iso.sx(x, y); val by = iso.sy(x, y, z)
        val p = P.toFloat()
        val w = Col.hex(0xE0B880); val wd = Col.hex(0xB8884E)
        for (lx in floatArrayOf(-1.8f, -0.8f, 0.9f, 1.9f)) c.fillRect(floor(cx + lx * p).toInt(), floor(by - 2.4f * p).toInt(), max(1, P), floor(2.4f * p).toInt(), wd)
        c.fillEllipse(cx, by - 3.2f * p, 2.8f * p, 1.3f * p, wd)
        c.fillEllipse(cx - 0.3f, by - 3.4f * p, 2.6f * p, 1.1f * p, w)
        c.line(floor(cx + 2f * p).toInt(), floor(by - 3.5f * p).toInt(), floor(cx + 3f * p).toInt(), floor(by - 5.6f * p).toInt(), w)
        c.fillEllipse(cx + 3.4f * p, by - 5.9f * p, 1.2f * p, 0.7f * p, w)
        c.set(floor(cx + 3.6f * p).toInt(), floor(by - 6.1f * p).toInt(), Pal.OUTLINE)
        c.fillRect(floor(cx - 0.7f * p).toInt(), floor(by - 4.4f * p).toInt(), floor(1.4f * p).toInt(), max(1, P), red)
        c.line(floor(cx - 2.6f * p).toInt(), floor(by - 3.4f * p).toInt(), floor(cx - 3.4f * p).toInt(), floor(by - 2.2f * p).toInt(), Col.hex(0x5E3A20))
    }

    /** Sawdust flying off the vice as a board is sawn there ([level]: how much), falling to the floor. */
    private fun dust(level: Float) {
        if (level <= 0.02f) return
        val x = iso.sx(wb - 0.3f, wy + 0.3f); val y = iso.sy(wb - 0.3f, wy + 0.3f, wz + 1f)
        s.fx {
            // closer up as thick a cloud: a mote for every pixel it covers
            val n = ((10 * level).toInt() + 3) * P * P
            for (j in 0 until n) {
                val ph = ((t * 0.8 + j.toFloat() / n + Noise.rnd(j, 51) * 0.3) % 1.0).toFloat()
                val dx = (Noise.rnd(j, 52) - 0.3f) * 14f * P * ph
                val dy = -4f * P * sin(ph * PI.toFloat()) + ph * ph * 10f * P
                c.blend(floor(x + dx).toInt(), floor(y + dy).toInt(), Col.hex(0xF0DCA8), 0.8f * (1f - ph))
            }
        }
    }

    /**
     * The storm lantern hanging over the bench on a chain (level 3): an iron frame round the glass, the flame lit from dusk;
     * turned [low] for someone asleep, a small flame behind dim glass.
     */
    private fun lantern(lit: Boolean, low: Boolean = false) {
        val lx = 3.9f; val ly = 3.1f
        val cx = iso.ix(lx, ly); val hook = iso.iy(lx, ly, hw); val top = iso.iy(lx, ly, fl + 19f)
        val p = P
        if (p > 1) chain(cx + p / 2, hook, top, ironL, ironD) else for (y in hook until top) c.set(cx, y, if ((y - hook) % 2 == 0) ironD else ironL)
        val w = 3 * p; val hh = 7 * p
        c.fillRect(cx - w, top, 2 * w + 1, p, iron)
        c.fillRect(cx - w + p, top + p, 2 * w + 1 - 2 * p, hh, if (low) Col.hex(0x6A4A2A) else if (lit) Pal.FLAME[1] else Col.hex(0xB8C8D4))
        if (low) glow {
            c.fillEllipse(cx + 0.5f, top + p + hh * 0.72f, 0.8f * p, 1.1f * p, Pal.FLAME[1])
            c.fillEllipse(cx + 0.5f, top + p + hh * 0.75f, 0.4f * p, 0.6f * p, Pal.FLAME[0])
        } else if (lit) glow {
            c.fillRect(cx - w + p, top + p, 2 * w + 1 - 2 * p, hh, Pal.WINDOW_LIT)
            c.fillEllipse(cx + 0.5f, top + p + hh * 0.55f, 1.1f * p, 2f * p, Pal.FLAME[0])
        } else c.vline(cx - w + p, top + p, top + p + hh - 1, Col.hex(0xE8F0F6))
        for (xx in intArrayOf(cx - w, cx + w)) c.vline(xx, top, top + hh + p, iron)
        c.vline(cx, top + p, top + p + hh - 1, iron)
        c.fillRect(cx - w - p, top + hh + p, 2 * w + 2 * p + 1, 2 * p, ironD)
        c.hline(cx - w - p, cx + w + p, top + hh + p, ironL)
        if (lit) s.fx {
            c.penEmissive = true
            c.ditherCircle(cx + 0.5f, top + hh / 2f, (if (low) 4f else 8f) * K, Pal.FLAME[1], (if (low) 0.08f else 0.14f) * env.windows)
            c.penEmissive = false
        }
    }

    // ------------------------------------------------------------------ the walls

    /** The tool board beside the window (level 2): a board of planks, pliers, a chisel, a try square and a brace on their pegs. */
    private fun toolBoard() {
        val xa = 5.2f; val xb = 6.6f; val za = fl + 8.5f; val zb = fl + 17f; val yb = y0 + 0.1f
        onY(yb, xa, xb, za, zb) { x, z, px, py ->
            val u = (x - xa) / (xb - xa); val v = (z - za) / (zb - za)
            val board = floor(u * 4f).toInt()
            when {
                u < 0.03f || u > 0.97f || v < 0.04f || v > 0.96f -> Pal.WOOD_D
                u * 4f - board < 0.04f -> Pal.WOOD_X
                // the pliers: two handles and the jaws
                abs(u - 0.2f) < 0.035f && v in 0.35f..0.8f -> if (v > 0.72f) ironL else red
                abs(u - 0.29f) < 0.035f && v in 0.35f..0.72f -> red
                abs(u - 0.245f) < 0.05f && v in 0.72f..0.86f -> iron
                // the chisel: its handle and blade
                abs(u - 0.45f) < 0.03f && v in 0.3f..0.55f -> Col.hex(0xC8A060)
                abs(u - 0.45f) < 0.02f && v in 0.55f..0.85f -> ironL
                // the try square: an L of steel and wood
                u in 0.58f..0.64f && v in 0.3f..0.82f -> Col.hex(0x6A4426)
                u in 0.58f..0.86f && v in 0.3f..0.36f -> Col.hex(0xB8BCC6)
                // the brace: a crank of iron
                abs(u - 0.78f) < 0.025f && v in 0.5f..0.9f -> iron
                abs(u - 0.84f) < 0.025f && v in 0.55f..0.75f -> iron
                v in 0.6f..0.64f && u in 0.78f..0.84f -> iron
                v in 0.2f..0.23f -> Pal.WOOD_D
                else -> if (Noise.v2(board * 3.1f, z * 0.3f, 53) > 0.7f) Col.scale(Pal.WOOD_M, 0.92f) else Pal.WOOD_M
            }
        }
    }

    /** The saw hanging on its nail over the bench (level 2): a steel blade toothed along its lower edge, the wooden handle. */
    private fun saw() {
        val yb = y0 + 0.08f; val xa = 1.35f; val xb = 2.95f; val za = fl + 11.5f
        onY(yb, xa, xb, za, za + 3.2f) { x, z, px, _ ->
            val u = (x - xa) / (xb - xa); val v = (z - za) / 3.2f
            val bladeTop = 0.55f + u * 0.35f
            when {
                u > 0.8f -> if (v in 0.2f..0.95f && !(u in 0.86f..0.94f && v in 0.45f..0.75f)) (if (u > 0.95f) Pal.WOOD_D else Col.hex(0xB0703E)) else 0
                v < bladeTop -> if (v < 0.12f && (px / max(1, P)) % 2 == 0) Col.hex(0x5A5A64) else if (v > bladeTop - 0.1f) Col.hex(0xE0E2EA) else Col.hex(0xB8BCC6)
                else -> 0
            }
        }
        iso.px(2.6f, yb + 0.02f, za + 3.4f, ironD)
    }

    /** Boards leaning on the left wall, standing on their ends, four of them, one shorter. */
    private fun boards() {
        for (k in 0 until 4) {
            val ya = 2.55f + k * 0.36f; val len = if (k == 2) 13f else 16f + k * 0.4f
            val col = if (k % 2 == 0) Col.hex(0xD8B07A) else Col.hex(0xC89C64)
            iso.quad(x0 + 0.6f, ya, fl, x0 + 0.6f, ya + 0.3f, fl, x0 + 0.08f, ya + 0.3f, fl + len, x0 + 0.08f, ya, fl + len, col)
            iso.line(x0 + 0.6f, ya + 0.3f, fl, x0 + 0.08f, ya + 0.3f, fl + len, Col.scale(col, 0.72f))
            iso.line(x0 + 0.6f, ya, fl, x0 + 0.08f, ya, fl + len, Col.mix(col, Col.hex(0xFFFFFF), 0.25f))
            if (P > 1) iso.line(x0 + 0.45f, ya + 0.15f, fl + 2f, x0 + 0.16f, ya + 0.12f, fl + len - 2f, Col.scale(col, 0.88f))
        }
    }

    /** The ladder leaning on the left wall (level 2): two rails and their rungs. */
    private fun ladder() {
        val y1a = 5.05f; val y1b = 5.8f; val foot = x0 + 1.35f; val headZ = fl + 20f
        for (k in 1..7) {
            val f = k / 8f
            val x = foot + (x0 + 0.1f - foot) * f; val z = fl + (headZ - fl) * f
            rod(x, y1a, z, x, y1b, z, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        }
        for (ry in floatArrayOf(y1a, y1b)) rod(foot, ry, fl, x0 + 0.1f, ry, headZ, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
    }

    /** The rake leaning head up on the left wall (level 2), a tooth out of its head: France is mending it. */
    private fun rake() {
        val ry = 6.6f
        rod(x0 + 0.9f, ry, fl, x0 + 0.15f, ry, fl + 17f, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        iso.box(x0 + 0.1f, ry - 0.55f, fl + 16.6f, 0.16f, 1.1f, 0.7f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        for (k in 0 until 7) {
            if (k == 4) continue
            val ty = ry - 0.48f + k * 0.16f
            iso.line(x0 + 0.26f, ty, fl + 16.9f, x0 + 0.55f, ty, fl + 16.9f, Col.hex(0xE0C8A0))
        }
    }

    /** The broom leaning in the front corner of the left wall: its handle and the straw of its head. */
    private fun broom() {
        val by = 8.55f
        rod(x0 + 0.7f, by, fl + 2.8f, x0 + 0.1f, by, fl + 16f, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        val cx = iso.sx(x0 + 0.75f, by); val cy = iso.sy(x0 + 0.75f, by, fl)
        val p = P.toFloat()
        c.polyBegin(); c.polyAdd(cx - 1.2f * p, cy - 6f * p); c.polyAdd(cx + 1.2f * p, cy - 6f * p); c.polyAdd(cx + 3f * p, cy); c.polyAdd(cx - 2.6f * p, cy)
        c.polyFill(Col.hex(0xD8BC6A))
        for (k in -2..2) c.line(floor(cx + k * 0.3f * p).toInt(), floor(cy - 5.6f * p).toInt(), floor(cx + k * 1.1f * p).toInt(), floor(cy).toInt() - 1, Col.hex(0xB0943E))
        c.hline(floor(cx - 1.3f * p).toInt(), floor(cx + 1.3f * p).toInt(), floor(cy - 4.6f * p).toInt(), red)
    }

    // ------------------------------------------------------------------ the floor

    /** The chopping block: a round of beech, its end grain on top, chips about it. */
    private fun block() {
        val x = 3.6f; val y = 7.25f; val sh = 3f
        val cx = iso.sx(x, y); val yb = iso.sy(x, y, fl); val yt = iso.sy(x, y, fl + sh)
        val rx = 5.4f * detail; val ry = 2.7f * detail
        c.fillEllipse(cx, yb, rx * 1.06f, ry * 1.08f, Pal.LOG_D)
        for (px in floor(cx - rx).toInt()..(cx + rx).toInt()) {
            val dx = (px + 0.5f - cx) / rx
            if (abs(dx) > 1f) continue
            val e = kotlin.math.sqrt(1f - dx * dx) * ry
            c.vline(px, yt.toInt(), (yb + e).toInt(), if (dx < -0.45f) Pal.LOG_L else if (dx > 0.45f) Pal.LOG_D else if (Noise.rnd(px / detail, 57) < 0.3f) Pal.LOG_D else Pal.LOG_M)
        }
        c.fillEllipse(cx, yt, rx, ry, Col.hex(0xCFA46C))
        for (r in 1..3) arc(cx, yt, rx * r / 4f, ry * r / 4f, Col.hex(0xA87E4C))
        for (k in 0 until 6) {
            val a = Noise.rnd(k, 59) * 2f * PI.toFloat()
            val px = iso.ix(x + cos(a) * 0.9f, y + sin(a) * 0.9f); val py = iso.iy(x + cos(a) * 0.9f, y + sin(a) * 0.9f, fl)
            c.fillRect(px, py, detail * 2, detail, Col.hex(0xD8B07A))
        }
    }

    /** The axe stuck in the block: its head in the wood, the handle slanting up. */
    private fun axe() {
        val x = 3.5f; val y = 7.2f; val z = fl + 3f
        rod(x, y, z + 0.4f, x + 0.9f, y + 0.5f, z + 6.2f, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        iso.box(x - 0.25f, y - 0.15f, z - 0.2f, 0.5f, 0.28f, 1.4f, Col.hex(0xB8BCC6), Col.hex(0x7C7C88), Col.hex(0x54545E))
        iso.line(x - 0.25f, y + 0.13f, z + 1.2f, x + 0.25f, y + 0.13f, z + 1.2f, Col.hex(0xE8EAF0))
    }

    /** The wheelbarrow (level 3): a wooden box on a wheel, its two handles back on the floor, its legs. */
    private fun wheelbarrow() {
        val xa = 5.9f; val xb = 6.95f; val ya = 5.8f; val yb = 6.55f; val za = fl + 1.9f; val zb = fl + 4.3f
        // the wheel in front, the far handle and leg first
        val wx = iso.sx(xb + 0.35f, (ya + yb) / 2f); val wy = iso.sy(xb + 0.35f, (ya + yb) / 2f, fl + 1.7f)
        val p = P.toFloat()
        rod(xa, ya + 0.1f, za + 1f, xa - 1.1f, ya + 0.05f, fl + 2.6f, Pal.WOOD_D, Pal.WOOD_M, Pal.WOOD_D, Pal.WOOD_X)
        c.fillEllipse(wx, wy, 1.6f * p, 3.4f * p, Pal.WOOD_D)
        c.fillEllipse(wx - 0.4f, wy, 1.2f * p, 3f * p, Pal.WOOD_M)
        c.fillEllipse(wx - 0.4f, wy, 0.5f * p, 0.9f * p, ironD)
        iso.box(xa, ya, za, xb - xa, yb - ya, zb - za, Col.hex(0x6A8A4A), Col.hex(0x5A7A3E), Col.hex(0x44602E))
        onFlat(zb, xa + 0.08f, ya + 0.08f, xb - 0.08f, yb - 0.08f) { _, _, _, _ -> Col.hex(0x3A4E28) }
        iso.line(xa, yb, zb, xb, yb, zb, Col.hex(0x88A864))
        for (lx in floatArrayOf(xa + 0.15f)) iso.post(lx, yb - 0.1f, fl, za, Pal.WOOD_D)
        rod(xa, yb - 0.1f, za + 1f, xa - 1.1f, yb - 0.05f, fl + 2.6f, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
    }
}
