package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.Stance
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * V hiši: the living room of a farmhouse (the "hiša" of an old Slovene house, Stari Janez's) as a diorama, the kitchen's
 * room ([RoomPainter]) furnished otherwise. In the back corner the kmečka peč, the big stove fed from the kitchen side,
 * with the bench round its two faces (the zapeček), where Muri the dog sleeps curled up; on the right wall the window and
 * the door; on the left wall the painted chest, the clock over it, the corner bench running to the front corner, where the
 * table stands under the holy corner (bohkov kot) and the lamp; the family photo on the wall.
 *
 * By the house's level ([si.lanisce.lani.game.scene.SceneFixtures]): at level 1 a plastered stove, a beaten earth floor,
 * plain lime, a bare table and a plain chest; at level 2 the stove green-tiled with a rail of drying socks, a plank floor
 * and a rag rug, the painted dado, the chair, the curtains, the clock, the lamp, a cloth on the table and the chest
 * painted; at level 3 the stencilled border, the holy corner, the wedding photo, Janez's book and his glasses on the table.
 *
 * Its effects: `stove` (fired up: its tiles warm and a glow on the bench), `dog` (Muri awake, sitting up; at night he
 * sleeps whatever).
 *
 * At night Janez sleeps on the zapeček ([beds]: "zapecek"), along its front run, his head on a pillow in the warm corner,
 * his hat off, under a loden-green wool blanket with a cream check; Muri curls up at the wall end, at his feet. Lovec Jože,
 * who lodges with him (the two old hunters), sleeps on its other run along the stove's side ("sidebench"), his head at the
 * wall end on the embroidered cushion (a pillow at level 1), his feet toward the warm corner, under a brown horse blanket
 * with a green stripe. The lamp is out and the stove glows a little.
 */
internal class LivingRoomPainter : RoomPainter() {
    override val art = "livingroom"

    override val winA = 4.95f
    override val winB = 6.6f
    override val doorA = 7.35f
    override val doorB = 8.7f
    override val clockY = 5.2f
    override val lampX = 3.05f
    override val lampY = 7.2f

    // the stove in the corner out to (SX1, SY1), its body SZ high, the cap on it out to (CX1, CY1); the bench round it
    private val sx1 = 3.9f; private val sy1 = 3.6f; private val sz = fl + 11f
    private val cx1 = 3.35f; private val cy1 = 3.05f
    private val bz = fl + 4.6f; private val bd = 0.72f
    // the chest by the left wall, the corner bench from it to the front corner, the table in the corner
    private val chA = 4.55f; private val chB = 5.85f
    private val cbA = 6.05f; private val cbB = 8.95f
    private val qa = 1.95f; private val qb = 4.2f; private val qc = 6.1f; private val qd = 8.3f; private val qz = fl + 7.2f

    /**
     * The stage (companion/SCENES.md, "Stage directions"): by the stove, at the table and at the door, where the people
     * stand; on the zapeček, sitting; and two places to hide, each with where one stands when out of it: under the table
     * (its top and cloth over them, the shoes and a bit of their clothes showing under it) and behind the door (it stands
     * open, the leaf into the room; a braid, a sleeve and the shoes show past it).
     */
    override val marks = mapOf(
        "door" to Mark(8.05f, 1.95f, fl, flip = true),
        "stove" to Mark(5.8f, 3.9f, fl, flip = true),
        "table" to Mark(5.35f, 7.7f, fl, flip = true),
        // the lap on the zapeček's plank along the stove's right face
        "bench" to Mark(sx1 + bd / 2f, 2.4f, bz + 0.9f - 2.5f, layer = "stove"),
        "under-table" to Mark(4.85f, 6.5f, fl, flip = true, layer = "table", hidden = "under-table"),
        "behind-door" to Mark(6.8f, 2.95f, fl, layer = "door", hidden = "behind-door"),
    )

    /** No soot: the stove is fed from the kitchen. */
    override fun soot(d: Float, z: Float): Float = 0f

    override fun floor() {
        if (lv < 2) { super.floor(); return }
        floorId = s.newObject(Pal.OUTLINE)
        planks(x0, y0, x1, y1, fl, 0.5f)
        c.penId = 0
    }

    /** At night Janez sleeps on the zapeček's front-left run, his head in the warm corner, Muri curled at his feet; Jože on its other run. */
    override val beds = listOf(BED, SIDE)

    override fun paint() {
        fit()
        vignette()
        lv = frame.world.hereLevel.coerceIn(1, 3)
        val asleep = peopleAt(BED).firstOrNull()
        // the lamp over the table burns from dusk, and is put out for whoever is asleep
        val lit = env.windows > 0.35f && there("lamp") && !asleepHere
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
        if (there("rug")) thing("rug") { rug(4.8f, 7.6f, 4.4f, 6.4f) } else rugId = floorId
        walls()
        thing("window") { window() }
        if (there("curtains")) thing("curtains") { curtains() }
        // the door stands open while someone hides behind it (and until they are out): they are between it and the wall
        val ajar = at("behind-door", Stance.HIDE, Stance.PEEK)
        thing("door") { door(open = ajar) }
        if (ajar) {
            cast("behind-door")
            thing("door") { leaf() }
        }
        if (there("clock")) thing("clock", slop = 1) { clock() }
        if (there("photo")) thing("photo", slop = 1) { photo() }
        if (there("corner")) thing("corner", slop = 1) { corner() }
        cast("door")

        // the stove and the bench round it, Muri on it; Jože asleep on its side run, behind the front one
        thing("stove") { pec() }
        val joze = peopleAt(SIDE).firstOrNull()
        thing("bench") { sideRun(joze != null) }
        joze?.let { p ->
            val top = bz + 0.9f
            if (lv < 2) prop { pillowBox(sx1 + 0.08f, y0 + 0.12f, sx1 + bd - 0.08f, y0 + 0.74f, top) }
            sleeperOn(p, sx1, y0 + 0.05f, sx1 + bd, sy1, top, alongY = true, BROWN_WOOL, foot = 0.45f)
        }
        thing("bench") { frontRun() }
        // Janez asleep along the front run, his head on a pillow in the warm corner, the green wool blanket over him; Muri
        // then curls up at the wall end, at his feet
        asleep?.let { p ->
            val top = bz + 0.9f; val end = sx1 + bd
            prop { pillowBox(end - 0.68f, sy1 + 0.08f, end - 0.08f, sy1 + bd - 0.08f, top) }
            sleeperOn(p, JANEZ_FEET, sy1, end, sy1 + bd, top, alongY = false, GREEN_WOOL, foot = 0f, far = true)
        }
        thing("dog", slop = 1) { dog(if (asleep != null) DOG_ASLEEP_X else 3.6f, sy1 + bd / 2f, bz + 0.9f) }
        thing("chest") { chest() }
        prop { cornerBench() }
        cast("stove")

        // the table in the corner, the chair at its far side, whoever hides under it, what lies on it, the lamp over it
        if (there("chair")) thing("chair") { chair(3.05f, 5.3f) }
        cast("under-table")
        thing("table") { cornerTable() }
        val top = qz + 0.15f
        if (there("book")) thing("book", slop = 1) { book(2.35f, 6.7f, top) }
        if (there("glasses")) thing("glasses", slop = 2) { glasses(3.35f, 7.45f, top) }
        if (there("lamp")) thing("lamp", slop = 1) { lamp(lit) }
        cast("table")

        stubs()
        prop { gardenBench() }
        if (lv >= 2) prop { pots() }
        prop { vegetables() }
        if (env.dark < 0.5f) prop { hen(henX, henY) }
        fireflies(iso.ix(0f, 12f), iso.iy(12f, 12f, 20f), iso.ix(12f, 0f), iso.iy(12f, 12f, 0f), 12, 5)

        // light: the lamp from dusk, the stove's warmth when it is fired up (and a little while Janez sleeps by it, the
        // lamp out), the sun through the window
        if (lit) s.light(iso.sx(lampX, lampY), iso.sy(lampX, lampY, fl + 16f), 50f, 1.05f * env.windows)
        val warm = max(fxOn("stove"), if (asleepHere) SLEEP_WARM else 0f)
        if (warm > 0.02f) s.light(iso.sx(sx1 + 0.3f, sy1 + 0.3f), iso.sy(sx1 + 0.3f, sy1 + 0.3f, fl + 6f), 30f * warm + 10f, 0.55f * warm)
        sunPatch()
    }

    // ------------------------------------------------------------------ the stove and its bench

    /**
     * The kmečka peč in the back corner: a big block on a dark plinth, plastered white at level 1 (a crack or two), green
     * tiles from level 2, a cornice round its top and a smaller cap set back on it; from level 2 a rail along its front with
     * socks drying. Fired up ([fxOn] "stove") its faces warm.
     */
    private fun pec() {
        val tiled = lv >= 2
        val warm = fxOn("stove")
        onY(sy1, x0, sx1, fl, sz) { x, z, px, py -> face(x - x0, z, px, py, 1f, tiled, warm) }
        onX(sx1, y0, sy1, fl, sz) { y, z, px, py -> face(y - y0 + 2.9f, z, px, py, 0.8f, tiled, warm) }
        // the cornice round its top, a little proud of it
        val (cl, cm, cd) = if (tiled) Triple(tileL, tileM, tileD) else Triple(limeL, lime, limeD)
        iso.box(x0, y0, sz, sx1 - x0 + 0.12f, sy1 - y0 + 0.12f, 0.9f, cl, cm, cd)
        iso.line(x0, sy1 + 0.12f, sz + 0.9f, sx1 + 0.12f, sy1 + 0.12f, sz + 0.9f, Col.mix(cl, Col.hex(0xFFFFFF), 0.3f))
        // the cap set back on it, its top edge rounded off
        val ch = 3.2f; val cz = sz + 0.9f
        onY(cy1, x0, cx1, cz, cz + ch) { x, z, px, py -> cap(x - x0, z - cz, ch, px, py, 1f, tiled) }
        onX(cx1, y0, cy1, cz, cz + ch) { y, z, px, py -> cap(y - y0 + 2.4f, z - cz, ch, px, py, 0.8f, tiled) }
        onFlat(cz + ch, x0, y0, cx1, cy1) { x, y, px, py ->
            val edge = min(cx1 - x, cy1 - y)
            if (edge < 0.2f) Col.mix(cl, Col.hex(0xFFFFFF), 0.2f) else if (Noise.rnd(px, py, 91) < 0.04f) cd else cm
        }
        if (!tiled) return
        // the rail along its front on two brackets, a pair of red socks and a towel drying on it
        val ry = sy1 + 0.32f; val rz = sz - 1.2f
        for (bx in floatArrayOf(x0 + 0.3f, sx1 - 0.25f)) rod(bx, sy1, rz, bx, ry, rz, Pal.WOOD_D, Pal.WOOD_M, Pal.WOOD_D, Pal.WOOD_X)
        rod(x0 + 0.2f, ry, rz, sx1 - 0.1f, ry, rz, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        onY(ry + 0.02f, 1.55f, 2.95f, rz - 4.4f, rz + 0.1f) { x, z, px, _ ->
            val sock = (x - 1.55f) / 0.32f
            val k = floor(sock).toInt(); val f = sock - k
            when {
                k == 0 || k == 1 -> if (f in 0.15f..0.85f && z > rz - 3.6f + (if (z < rz - 2.6f) (f - 0.15f) * 2.2f else 0f)) {
                    if (abs(z - (rz - 0.8f)) < 0.3f) linen else if (P > 1 && (px / P) % 2 == 0) Col.hex(0xB02A24) else red
                } else 0
                x > 2.25f -> if (z > rz - 4.2f + sin((x - 2.25f) * 20f) * 0.2f) (if (abs(z - (rz - 2.8f)) < 0.3f) chinaBlue else linen) else 0
                else -> 0
            }
        }
    }

    /** A face of the stove at [a] cells along and height [z]: tiles on an iron plinth, or plaster over a grey plinth; warmed by [warm]. */
    private fun face(a: Float, z: Float, px: Int, py: Int, dim: Float, tiled: Boolean, warm: Float): Int {
        val col = if (tiled) tiles(a, z, px, py, dim) else {
            val h = z - fl
            val n = Noise.rnd(px, py, 93)
            val crack = abs(Noise.v2(a * 2.2f, h * 0.2f, 95) - 0.5f) < 0.012f && h > 3f
            Col.scale(when {
                h < 1.2f -> if (n < 0.4f) Pal.STONE_D else Pal.STONE_M
                crack -> limeD
                n < 0.05f -> limeL
                n > 0.96f -> limeD
                else -> lime
            }, dim)
        }
        return if (warm > 0.02f) Col.mix(col, Col.hex(0xFFA060), 0.14f * warm) else col
    }

    /** The cap on the stove at [a] cells along and [h] up its [ch]: its upper edge rounded (lighter), tiles or plaster. */
    private fun cap(a: Float, h: Float, ch: Float, px: Int, py: Int, dim: Float, tiled: Boolean): Int {
        val round = h > ch - 0.8f
        val col = when {
            tiled && !round -> tiles(a, fl + 0.9f + h * 0.9f, px, py, 1f)
            tiled -> tileL
            round -> limeL
            else -> if (Noise.rnd(px, py, 97) < 0.05f) limeD else lime
        }
        return Col.scale(col, dim)
    }

    /**
     * The zapeček: the bench round the stove's two faces, thick planks on legs, a cushion at the warm corner from level 2
     * (an embroidered one, as the grandmothers' are).
     */
    /** The run along the stove's front-right face (behind the other), its cushion in the warm corner; under Jože's head at the wall end while he [sleeps] on it. */
    private fun sideRun(sleeps: Boolean) {
        // along the front-right face first (it is behind; whoever sleeps on it next), then along the front-left one
        seat(sx1, y0 + 0.05f, sx1 + bd, sy1, alongY = true)
        if (lv >= 2) {
            val cy = if (sleeps) y0 + 0.12f else sy1 - 0.7f
            iso.box(sx1 + 0.08f, cy, bz + 0.9f, 0.58f, 0.62f, 1.1f, linen, Col.hex(0xE6DCC6), Col.hex(0xC4B89E))
            iso.px(sx1 + 0.4f, cy + 0.6f, bz + 1.5f, red)
        }
    }

    private fun frontRun() = seat(x0 + 0.05f, sy1, sx1 + bd, sy1 + bd, alongY = false)

    /** One run of the bench over x [xa]..[xb], y [ya]..[yb]: legs at its front, the plank, its grain closer up. */
    private fun seat(xa: Float, ya: Float, xb: Float, yb: Float, alongY: Boolean) {
        if (alongY) for (ly in floatArrayOf(ya + 0.15f, (ya + yb) / 2f, yb - 0.15f)) iso.post(xb - 0.12f, ly, fl, bz, Pal.WOOD_D)
        else for (lx in floatArrayOf(xa + 0.2f, (xa + xb) / 2f, xb - 0.15f)) iso.post(lx, yb - 0.12f, fl, bz, Pal.WOOD_D)
        iso.box(xa, ya, bz, xb - xa, yb - ya, 0.9f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        if (P > 1) grain(xa, ya, xb, yb, bz + 0.9f, if (alongY) 21 else 23, alongY)
        if (alongY) iso.line(xb, ya, bz + 0.9f, xb, yb, bz + 0.9f, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.25f))
        else iso.line(xa, yb, bz + 0.9f, xb, yb, bz + 0.9f, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.25f))
    }

    /**
     * Muri, Janez's small brown dog, on the bench at world ([x], [y]) on its top [z]: curled up asleep, his tail round his
     * nose; awake ([fxOn] "dog", never at night) sitting up, his ears up and his tongue out, his tail wagging.
     */
    private fun dog(x: Float, y: Float, z: Float) {
        val cx = iso.sx(x, y); val by = iso.sy(x, y, z)
        val p = P.toFloat()
        val b = Col.hex(0x8A5A34); val bl = Col.hex(0xB07A48); val bd = Col.hex(0x5E3A20)
        val awake = fxOn("dog") > 0.5f && env.dark < 0.7f
        if (!awake) {
            // curled up: the body a round loaf, the head on the paws, one ear flopped, the tail round the front
            c.fillEllipse(cx, by - 2.2f * p, 5.2f * p, 2.6f * p, bd)
            c.fillEllipse(cx - 0.3f * p, by - 2.5f * p, 4.9f * p, 2.3f * p, b)
            c.fillEllipse(cx - 1.2f * p, by - 3.4f * p, 2.8f * p, 1.1f * p, bl)
            c.fillEllipse(cx + 3.6f * p, by - 2.0f * p, 2.1f * p, 1.7f * p, b)
            c.fillEllipse(cx + 3.9f * p, by - 1.4f * p, 1.2f * p, 0.8f * p, bl)
            c.fillEllipse(cx + 3.0f * p, by - 2.9f * p, 1.1f * p, 1.4f * p, bd)
            c.set(floor(cx + 5.1f * p).toInt(), floor(by - 1.6f * p).toInt(), Pal.OUTLINE)
            c.hline(floor(cx + 3.7f * p).toInt(), floor(cx + 4.5f * p).toInt(), floor(by - 2.2f * p).toInt(), bd)
            c.fillEllipse(cx - 1.5f * p, by - 0.6f * p, 3.8f * p, 0.7f * p, bd)
            c.fillEllipse(cx - 1.5f * p, by - 0.8f * p, 3.5f * p, 0.45f * p, b)
            // his breath: a slow rise of the flank
            if (sin(t * 1.4) > 0.3) c.hline(floor(cx - 2f * p).toInt(), floor(cx + 1f * p).toInt(), floor(by - 4.6f * p).toInt(), b)
            return
        }
        val wag = if (sin(t * 12) > 0) 1f else -1f
        c.line(floor(cx - 2.5f * p).toInt(), floor(by - 1.5f * p).toInt(), floor(cx - 4.2f * p).toInt(), floor(by - (3.5f + wag) * p).toInt(), bd)
        c.fillEllipse(cx, by - 2.6f * p, 2.8f * p, 2.6f * p, b)
        c.fillEllipse(cx + 0.6f * p, by - 2.2f * p, 1.4f * p, 1.8f * p, bl)
        c.fillEllipse(cx + 0.8f * p, by - 6.2f * p, 2.1f * p, 1.9f * p, b)
        c.fillEllipse(cx + 2.4f * p, by - 5.6f * p, 1.2f * p, 0.8f * p, bl)
        c.fillEllipse(cx - 0.5f * p, by - 7.6f * p, 0.8f * p, 1.3f * p, bd)
        c.set(floor(cx + 1.1f * p).toInt(), floor(by - 6.8f * p).toInt(), Pal.OUTLINE)
        c.set(floor(cx + 3.3f * p).toInt(), floor(by - 5.8f * p).toInt(), Pal.OUTLINE)
        c.fillRect(floor(cx + 2.4f * p).toInt(), floor(by - 5f * p).toInt(), max(1, P), max(1, P) * 2, Col.hex(0xE0708A))
        c.fillRect(floor(cx - 0.4f * p).toInt(), floor(by - 0.8f * p).toInt(), max(1, P) * 2, max(1, P), bl)
        c.fillRect(floor(cx + 1.2f * p).toInt(), floor(by - 0.8f * p).toInt(), max(1, P) * 2, max(1, P), bl)
    }

    // ------------------------------------------------------------------ the left wall

    // ------------------------------------------------------------------ hiding (companion/SCENES.md, "Stage directions")

    /** The chest by the left wall: a plain one at level 1, painted from level 2 ([paintedChest]). */
    private fun chest() = paintedChest(chA, chB, painted = lv >= 2)

    /**
     * Someone hiding at one of the room's hiding places, or peeking out of it. Under the table: crouched in its middle, the
     * top and the cloth over them, their shoes and a bit of their clothes showing under it; peeking, crouched at its front
     * corner, the head out from under the top. Behind the open door: pressed to the wall behind its leaf, a braid or a
     * sleeve past its sides and the shoes under it; peeking, half out past its edge.
     */
    override fun hiding(p: PersonInScene, spot: String, m: Mark, peek: Boolean) {
        when (spot) {
            "under-table" ->
                if (peek) person(p, sx(PEEK_TX, PEEK_TY), sy(PEEK_TX, PEEK_TY, fl) + detail, flip = false, crouch = true)
                else figure(p, sx(HIDE_TX, HIDE_TY), sy(HIDE_TX, HIDE_TY, fl) + detail, flip = false, crouch = true)
            "behind-door" ->
                if (peek) person(p, sx(doorA - 0.15f, y0 + 1.1f), sy(doorA - 0.15f, y0 + 1.1f, fl) + detail, flip = false)
                else figure(p, sx(doorA - 0.15f, y0 + 0.58f), sy(doorA - 0.15f, y0 + 0.58f, fl) + detail, flip = false)
            else -> super.hiding(p, spot, m, peek)
        }
    }

    /** The bench along the left wall from the chest to the front corner, behind the table: a plank on legs. */
    private fun cornerBench() {
        for (ly in floatArrayOf(cbA + 0.2f, (cbA + cbB) / 2f, cbB - 0.2f)) iso.post(x0 + 0.6f, ly, fl, bz, Pal.WOOD_D)
        iso.box(x0, cbA, bz, 0.72f, cbB - cbA, 0.9f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        if (P > 1) grain(x0, cbA, x0 + 0.72f, cbB, bz + 0.9f, 25, alongY = true)
        iso.line(x0 + 0.72f, cbA, bz + 0.9f, x0 + 0.72f, cbB, bz + 0.9f, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.25f))
    }

    /**
     * The wedding photo on the left wall (level 3): Janez and Ana in a dark frame, sepia, he in his dark suit, she in white
     * with a veil and a posy.
     */
    private fun photo() {
        val ya = 6.0f; val yb = 6.8f; val za = fl + 11.2f; val zb = fl + 15.4f
        onX(x0 + 0.04f, ya, yb, za, zb) { y, z, px, py ->
            val u = (y - ya) / (yb - ya); val v = (z - za) / (zb - za)
            when {
                u < 0.09f || u > 0.91f || v < 0.07f || v > 0.93f -> if (u < 0.09f || v > 0.93f) Col.hex(0x6A4428) else Col.hex(0x3E2616)
                u < 0.16f || u > 0.84f || v < 0.12f || v > 0.88f -> Col.hex(0xE8DCC0)
                // she, on the right: the veil, the face, the white dress, the posy
                abs(u - 0.62f) < 0.1f && v in 0.64f..0.76f -> Col.hex(0xE6CFB0)
                abs(u - 0.62f) < 0.14f && v in 0.76f..0.84f -> Col.hex(0xF4EEE0)
                abs(u - 0.62f) < 0.14f - (0.64f - v) * 0.12f && v in 0.14f..0.64f -> if (abs(u - 0.58f) < 0.05f && abs(v - 0.44f) < 0.05f) Col.hex(0xC8784A) else Col.hex(0xF0E8D8)
                // he, on the left: his face, the dark suit
                abs(u - 0.38f) < 0.09f && v in 0.64f..0.76f -> Col.hex(0xD8BC98)
                abs(u - 0.38f) < 0.07f && v in 0.76f..0.82f -> Col.hex(0x4A3A2A)
                abs(u - 0.38f) < 0.13f && v in 0.14f..0.64f -> if (abs(u - 0.38f) < 0.03f && v > 0.5f) Col.hex(0xE8DCC8) else Col.hex(0x3A2E24)
                else -> Col.mix(Col.hex(0xB89C74), Col.hex(0x8E7456), v + (Dither.at(px, py) - 0.5f) * 0.2f)
            }
        }
    }

    // ------------------------------------------------------------------ the table

    /**
     * The table in the corner: square legs and a stretcher, a thick top; from level 2 under a white cloth embroidered with
     * a red border, hanging over its front edges.
     */
    private fun cornerTable() {
        for ((lx, ly) in listOf(qb - 0.18f to qc + 0.18f, qa + 0.18f to qd - 0.18f, qb - 0.18f to qd - 0.18f)) {
            iso.box(lx - 0.1f, ly - 0.1f, fl, 0.2f, 0.2f, qz - fl - 1f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        }
        rod(qa + 0.3f, qd - 0.18f, fl + 1.6f, qb - 0.3f, qd - 0.18f, fl + 1.6f, Pal.WOOD_D, Pal.WOOD_M, Pal.WOOD_D, Pal.WOOD_X)
        iso.box(qa, qc, qz - 1f, qb - qa, qd - qc, 1f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        if (lv < 2) {
            if (P > 1) grain(qa, qc, qb, qd, qz, 27)
            return
        }
        val zc = qz + 0.15f; val drop = 1.7f; val e = 0.06f
        onFlat(zc, qa - e, qc - e, qb + e, qd + e) { x, y, px, py -> cloth(min(min(x - qa, qb - x), min(y - qc, qd - y)), x, y, px, py, 1f) }
        onY(qd + e, qa - e, qb + e, zc - drop, zc) { x, z, px, py ->
            val hem = zc - drop + 0.3f * abs(sin((x - qa) * 11f))
            if (z < hem) 0 else cloth(min(z - hem, 0.5f) * 0.4f, x, z, px, py, 0.92f)
        }
        onX(qb + e, qc - e, qd + e, zc - drop, zc) { y, z, px, py ->
            val hem = zc - drop + 0.3f * abs(sin((y - qc) * 11f))
            if (z < hem) 0 else cloth(min(z - hem, 0.5f) * 0.4f, y, z, px, py, 0.76f)
        }
    }

    /** The table cloth [edge] cells from its edge: white linen, a band of red cross-stitch round it; [dim] shades it. */
    private fun cloth(edge: Float, u: Float, v: Float, px: Int, py: Int, dim: Float): Int {
        val band = edge in 0.08f..0.2f
        val col = when {
            band && P > 1 -> if ((px + py) % 3 == 0) linen else red
            band -> if (Dither.at(px, py) < 0.6f) red else Col.hex(0xE88A80)
            edge < 0.05f -> Col.hex(0xE8E0D2)
            else -> if (Noise.rnd(px, py, 101) < 0.03f) Col.hex(0xECE4D6) else linen
        }
        return Col.scale(col, dim)
    }

    private companion object {
        /** Where one crouches hidden under the corner table, and where peeking out from under its front corner. */
        const val HIDE_TX = 3.1f; const val HIDE_TY = 7.25f
        const val PEEK_TX = 4.55f; const val PEEK_TY = 7.75f

        /** Janez asleep on the zapeček's front run, his head in the corner: his feet this far along it; Muri at the wall end past them. */
        const val JANEZ_FEET = 2.0f
        const val DOG_ASLEEP_X = 1.65f

        /** How warm the stove glows while Janez sleeps by it (as the `stove` effect's level). */
        const val SLEEP_WARM = 0.5f

        /** Janez's wool blanket: loden green, a cream check (the tiled stove's green, the cloth's linen). */
        val GREEN_WOOL = intArrayOf(Col.hex(0x6E9A62), Col.hex(0x4E7A48), Col.hex(0x2E4E2C), Col.hex(0xE6D6A6))

        /** The zapeček's other run, along the stove's side, where Lovec Jože sleeps (who lodges with Janez). */
        const val SIDE = "sidebench"

        /** Jože's horse blanket: brown wool, a hunter's green stripe. */
        val BROWN_WOOL = intArrayOf(Col.hex(0x9A7E5C), Col.hex(0x7A6044), Col.hex(0x4A3A2A), Col.hex(0x4E7A48))
    }

    /** Janez's book on the table: a thick one, its dark red cover, the pages' edge cream, a ribbon out of it. */
    private fun book(x: Float, y: Float, z: Float) {
        iso.box(x, y, z, 0.62f, 0.46f, 0.9f, Col.hex(0xA8403A), Col.hex(0x8A2E2A), Col.hex(0x6A2020))
        iso.line(x + 0.62f, y + 0.05f, z + 0.3f, x + 0.62f, y + 0.41f, z + 0.3f, Col.hex(0xF0E6CC))
        iso.line(x + 0.05f, y + 0.46f, z + 0.3f, x + 0.57f, y + 0.46f, z + 0.3f, Col.hex(0xF0E6CC))
        iso.line(x + 0.3f, y + 0.46f, z + 0.1f, x + 0.3f, y + 0.46f, z - 0.9f, Col.hex(0xF0C23A))
        if (P > 1) iso.line(x + 0.08f, y + 0.08f, z + 0.9f, x + 0.54f, y + 0.08f, z + 0.9f, Col.hex(0xD8A838))
    }

    /** His glasses on the table by the book: two round rims, the bridge, the folded temples; a glint in each lens. */
    private fun glasses(x: Float, y: Float, z: Float) {
        val cx = iso.sx(x, y); val cy = iso.sy(x, y, z)
        val p = P.toFloat()
        val rim = Col.hex(0x3A3A44); val lens = Col.hex(0xC8DCEA)
        for (dx in floatArrayOf(-1.9f, 1.9f)) {
            c.fillEllipse(cx + dx * p, cy - 0.8f * p, 1.5f * p, 1.0f * p, rim)
            c.fillEllipse(cx + dx * p, cy - 0.8f * p, 1.5f * p - max(1f, p * 0.6f), 1.0f * p - max(1f, p * 0.5f), lens)
            c.set(floor(cx + (dx - 0.6f) * p).toInt(), floor(cy - 1.2f * p).toInt(), Col.hex(0xFFFFFF))
        }
        c.hline(floor(cx - 0.5f * p).toInt(), floor(cx + 0.5f * p).toInt(), floor(cy - 1.2f * p).toInt(), rim)
        c.line(floor(cx - 3.3f * p).toInt(), floor(cy - 0.8f * p).toInt(), floor(cx - 1.5f * p).toInt(), floor(cy + 0.3f * p).toInt(), rim)
        c.line(floor(cx + 3.3f * p).toInt(), floor(cy - 0.8f * p).toInt(), floor(cx + 1.6f * p).toInt(), floor(cy + 0.3f * p).toInt(), rim)
    }
}
