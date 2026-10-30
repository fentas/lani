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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Na igrišču: the playground by the school (the project `igrisce`, Učiteljica Mojca's), a diorama of its lawn (companion/SCENES.md
 * "Arts"). At the back the school's side, whitewashed over its plinth, a tall classroom window; along the back edges a picket
 * fence with its gate. Under the chestnut tree in the back corner the teacher's bench; the swing on its wooden A-frame with its
 * two seats; the wooden slide on the right, its ladder up the back; the sandpit of planks on the left, its bucket and spade in
 * the sand; the climbing frame of poles in front on the right, the little wooden house beside it; the hopscotch chalked on the
 * flagstones down the middle (ristanc: one to eight and the arc of the "nebo" at the top); the red ball on the lawn.
 *
 * The seasons: the chestnut in flower in May, its leaves gold in October and its conkers on the grass, bare in winter, snow on
 * the lawn, the fence and the house's roof; at night the classroom window dark, the lamp over the school's door lit.
 *
 * Its effects ([si.lanisce.lani.game.scene.SceneArt.effects]):
 * - `swing`: the near seat swinging (how high: 1 out nearly level with the bar). Not set: still.
 * - `ball`: 0 lost under the slide's foot, half hidden by the chute; rising, it rolls out over the lawn; 1 at its place. Not
 *   set: at its place.
 * - `kite`: a kite up over the playground on its string, from beside the swing (how high), its tail fluttering in the wind.
 * - `bubbles`: soap bubbles drifting up from beside the bench and over the lawn, catching the light (how many).
 * - `castle`: a sand castle built in the sandpit: a mound, then its walls and towers, at 1 a flag on the top.
 *
 * Tapped ([pokes]): the near seat swings, then higher.
 *
 * Closer up ([detail] 2 and 3): the fence's pickets, the planks' grain, the ropes of the swing, the chalk's numbers, the sand's
 * grain, the chestnut's leaves.
 *
 * Friuli's playground ([look]; companion/SCENES.md "Culture looks"): the school's wall pale yellow over a green dado, as the
 * Friuli school's classroom; a fontanella by the fence, the green cast-iron drinking fountain of the Italian towns.
 */
internal class PlaygroundPainter : RoomPainter() {
    override val art = "playground"
    override val pokes = listOf(Poke("swing", listOf(2.4, 3.2), rest = 2.5))

    override val plotW = 13f
    override val plotD = 11f
    override val rise = 27f

    // the school's side at the back (y = SY), from SX0 to SX1, SH high; its window; the lamp over its door round the corner
    private val sy0 = 0.45f; private val sx0 = 0.3f; private val sx1 = 5.6f; private val sh = 23f
    private val swA = 1.6f; private val swB = 3.4f; private val swZa = 8f; private val swZb = 17f
    // the fences along the back edges: the right one (y = SY) from SX1 to FX1 with the gate GA..GB; the left one (x = FX)
    private val fx1 = 12.8f; private val fenceX = 0.3f; private val gA = 8.2f; private val gB = 9.4f; private val fh = 5.2f
    // the chestnut and the bench under it
    private val treeX = 1.6f; private val treeY = 2.6f
    private val benchY0 = 3.6f; private val benchY1 = 6.2f; private val benchZ = 4.4f
    // the swing's frame along x at SWY, its legs at LX0 and LX1, the bar at BZ; the seats at SEAT0 and SEAT1
    private val swY = 3.7f; private val lx0 = 4.2f; private val lx1 = 7.2f; private val barZ = 19f
    private val seat0 = 5.0f; private val seat1 = 6.4f; private val seatZ = 4.5f
    // the slide: its platform at (SLX, SLY0), the chute down toward +y to SLY1
    private val slX = 10.7f; private val slY0 = 2.3f; private val slY1 = 6.1f; private val slZ = 12f
    // the sandpit, the climbing frame, the little house, the hopscotch, the ball
    private val spX0 = 2.4f; private val spX1 = 5.5f; private val spY0 = 7.0f; private val spY1 = 9.9f
    private val clX0 = 10.9f; private val clX1 = 12.5f; private val clY0 = 7.4f; private val clY1 = 9.0f
    private val hoX0 = 11.2f; private val hoX1 = 12.6f; private val hoY0 = 3.4f; private val hoY1 = 4.8f
    private val hsX0 = 6.45f; private val hsX1 = 7.6f; private val hsY0 = 5.3f; private val hsY1 = 10.3f
    private val ballX = 8.6f; private val ballY = 6.0f
    private val fontX = 6.6f; private val fontY = 1.0f

    // the people: beside the swing, by the sandpit, at the bench
    private val swingPX = 7.95f; private val swingPY = 5.0f
    private val sandPX = 6.0f; private val sandPY = 8.75f
    private val benchPX = 2.15f; private val benchPY = 5.3f

    private val sandL = Col.hex(0xF0DCA8); private val sandM = Col.hex(0xE0C890); private val sandD = Col.hex(0xC4AA70)
    private val plankL = Col.hex(0xC49460); private val plankM = Col.hex(0xA27646); private val plankD = Col.hex(0x7A5430)
    private val ropeC = Col.hex(0xD8C49A)
    private val ballC = Col.hex(0xD8302A); private val ballL = Col.hex(0xF07060)
    private val chalkC = Col.hex(0xF8F6EE)
    private val flagL = Col.hex(0xB8B0A2); private val flagM = Col.hex(0x9C9486)

    /** Friuli's look ([readLook2]): the school's wall pale yellow over a green dado, and the fontanella by the fence. */
    private var friuliSchool = false
    private var fontanella = false

    private val fine: Boolean get() = detail > 1

    override fun ambient(): FloatArray = floatArrayOf(env.ambR, env.ambG, env.ambB)

    private fun readLook2() {
        friuliSchool = variant("school") == "friuli"
        fontanella = variant("fontanella") != null
    }

    override fun paint() {
        fit()
        vignette()
        lv = 1
        readLook2()
        plot { x, y, px, py -> lawn(x, y, px, py) }
        tufts(80, 41) { x, y -> busy(x, y) }
        if (env.season == Season.AUTUMN) conkers()
        // at the back: the school, the fences and the gate, the chestnut and the bench under it
        thing("school") { school() }
        thing("fence") { fences() }
        thing("gate") { gate() }
        if (fontanella) prop { fontanella() }
        thing("tree") { chestnut() }
        thing("bench") { bench() }
        bubbles()
        for (p in peopleAt("bench")) { footShadow(benchPX, benchPY); personAt(p, benchPX, benchPY, 0f) }
        // the swing, the slide (the ball lost under its foot first), the hopscotch, the people at the swing
        thing("swing") { swing() }
        thing("slide") { slide() }
        thing("hopscotch") { hopscotch() }
        thing("ball", slop = 2) { ball() }
        for (p in peopleAt("swing")) { footShadow(swingPX, swingPY); personAt(p, swingPX, swingPY, 0f, flip = true) }
        // the sandpit and what is in it, the little house, the climbing frame
        thing("sandpit") { sandpitFrame() }
        thing("sand") { sandIn() }
        castle()
        thing("bucket", slop = 1) { bucket() }
        thing("spade", slop = 1) { spade() }
        for (p in peopleAt("sandpit")) { footShadow(sandPX, sandPY); personAt(p, sandPX, sandPY, 0f, flip = true) }
        thing("house") { house() }
        thing("climber") { climber() }
        kite()
        fireflies(iso.ix(0f, 11f), iso.iy(11f, 11f, 18f), iso.ix(13f, 0f), iso.iy(13f, 11f, 0f), 9, 23)
        lights()
    }

    /** Where nothing grows tufts: the things on the lawn, the paths. */
    private fun busy(x: Float, y: Float): Boolean =
        (x in spX0 - 0.3f..spX1 + 0.3f && y in spY0 - 0.3f..spY1 + 0.3f) || (x in hsX0 - 0.2f..hsX1 + 0.2f && y > hsY0 - 0.3f) ||
            (x in hoX0 - 0.3f..hoX1 + 0.3f && y in hoY0 - 0.3f..hoY1 + 0.3f) || (x in clX0 - 0.3f..clX1 + 0.3f && y in clY0 - 0.3f..clY1 + 0.3f) ||
            y < 1.2f || x < 0.9f || abs(x - slX) < 0.8f && y in slY0..slY1 + 0.4f

    // ------------------------------------------------------------------ the lawn

    override fun footprint(x: Float, y: Float) = shade(iso.sx(x, y), iso.sy(x, y, 0f), 5f * K, 1.6f * K, 0.75f, groundId)

    /** The lawn: worn bare under the swing and at the slide's foot, round the sandpit; snow in winter. */
    private fun lawn(x: Float, y: Float, px: Int, py: Int): Int {
        val g = grassAt(x, y, px, py)
        val underSwing = if (x in seat0 - 0.6f..seat1 + 0.6f) max(0f, 1f - abs(y - swY) / 1.3f) else 0f
        val slideFoot = max(0f, 1f - hypot(x - slX, (y - slY1 - 0.3f) * 1.2f) / 1.0f)
        val gate = max(0f, 1f - hypot(x - (gA + gB) / 2f, (y - 1.2f) * 0.8f) / 1.4f)
        val f = max(max(underSwing, slideFoot), gate) * (0.75f + Noise.v2(x * 2f, y * 2f, 5) * 0.5f)
        return dirtAt(px, py, f.coerceIn(0f, 1f), g)
    }

    /** Conkers fallen under the chestnut in autumn, brown and glossy, a burst husk here and there. */
    private fun conkers() {
        c.penId = groundId
        for (k in 0 until 9) {
            val a = Noise.rnd(k, 201) * PI.toFloat() * 2f; val r = 0.6f + Noise.rnd(k, 202) * 1.8f
            val x = treeX + cos(a) * r; val y = treeY + 0.6f + sin(a) * r * 0.8f
            if (x < 0.5f || y < 0.6f) continue
            val cx = iso.ix(x, y); val cy = iso.iy(x, y, 0f)
            sprite(cx, cy) { c.set(cx, cy, Col.hex(0x6A3A1E)); c.set(cx + 1, cy, Col.hex(0x8A4E26)); c.set(cx, cy - 1, Col.hex(0xB07040)) }
        }
        c.penId = 0
    }

    // ------------------------------------------------------------------ the school, the fences, the gate

    /** The school's side: whitewash over its plinth (pale yellow over a green dado in Friuli), its tall classroom window, the lamp. */
    private fun school() {
        val (wl, wm, wd) = if (friuliSchool) Triple(Col.hex(0xF7EDCF), Col.hex(0xEDDDB4), Col.hex(0xD6C390)) else Triple(Col.hex(0xFAF4E4), Col.hex(0xF0E6CC), Col.hex(0xD9CFB9))
        onY(sy0, sx0, sx1, 0f, sh) { x, z, px, py ->
            val n = Noise.rnd(px, py, 211)
            when {
                x in swA..swB && z in swZa..swZb -> {
                    val e = min(min(x - swA, swB - x) * 4f, min(z - swZa, swZb - z))
                    if (e < 0.45f) Pal.STONE_L else if (abs(x - (swA + swB) / 2f) < 0.06f || abs(z - (swZa + swZb) * 0.55f) < 0.3f || abs(z - swZa - 2.5f) < 0.25f) Pal.WOOD_L
                    else if (env.windows > 0.3f) Col.hex(0x34465A) else Col.mix(Pal.GLASS_HI, skyAt(0.3f), 0.4f)
                }
                friuliSchool && z < 4.2f -> if (n < 0.1f) Col.hex(0x6F8F7A) else Col.hex(0xA9C9B2)
                z < 2.2f -> if (n < 0.3f) Pal.STONE_D else if (n < 0.85f) Pal.STONE_M else Pal.STONE_L
                z < 2.6f -> Pal.STONE_L
                n < 0.04f -> wl
                n > 0.96f -> wd
                else -> wm
            }
        }
        // its corner and the eave of its roof
        onX(sx1, sy0 - 0.5f, sy0, 0f, sh) { _, z, _, _ -> Col.scale(if (z < 2.2f) Pal.STONE_M else wm, 0.8f) }
        val roof = if (winter) Pal.SNOW_L else Pal.ROOF_M
        iso.quad(sx0 - 0.2f, sy0 - 0.6f, sh + 3.4f, sx1 + 0.3f, sy0 - 0.6f, sh + 3.4f, sx1 + 0.3f, sy0 + 0.5f, sh - 0.4f, sx0 - 0.2f, sy0 + 0.5f, sh - 0.4f, roof)
        if (!winter) for (k in 0 until 4) { val f = k / 3f; iso.line(sx0 - 0.2f, sy0 - 0.6f + 1.1f * f, sh + 3.4f - 3.8f * f, sx1 + 0.3f, sy0 - 0.6f + 1.1f * f, sh + 3.4f - 3.8f * f, if (k == 3) Pal.ROOF_D else Pal.ROOF_L) }
        // the lamp on its bracket at the corner, lit from dusk
        val lx = sx1 - 0.4f
        rod(lx, sy0, sh - 5f, lx, sy0 + 0.6f, sh - 5f, iron)
        val cx = iso.sx(lx, sy0 + 0.6f); val cy = iso.sy(lx, sy0 + 0.6f, sh - 6.2f)
        val lit = env.windows > 0.35f
        if (lit) glow { c.fillCircle(cx, cy, 1.1f * K, Pal.WINDOW_LIT) } else c.fillCircle(cx, cy, 1.1f * K, Col.hex(0xC8D0D8))
        c.hline(floor(cx - 1.4f * K).toInt(), floor(cx + 1.4f * K).toInt(), floor(cy - 1.2f * K).toInt(), iron)
    }

    /** A picket at [a] along the fence and [z] up, or 0 between the pickets: pointed tops, two rails behind them. */
    private fun picket(a: Float, z: Float, dim: Float): Int {
        val pw = 0.28f
        val u = (a / pw) % 1f
        val top = fh - 0.8f * abs(u - 0.5f) * 2f
        if (abs(z - 1.4f) < 0.35f || abs(z - fh + 1.6f) < 0.35f) return Col.scale(plankD, dim)
        if (u < 0.22f || z > top) return 0
        if (winter && z > top - 0.5f) return Pal.SNOW_L
        val col = if (u < 0.36f) Col.hex(0xF2EEE4) else if (u > 0.88f) Col.hex(0xC8C2B6) else Col.hex(0xE6E0D2)
        return Col.scale(col, dim)
    }

    /** The picket fences along the back edges, white; the gap for the gate in the right one. */
    private fun fences() {
        onY(sy0, sx1, fx1, 0f, fh) { x, z, _, _ -> if (x in gA..gB) 0 else picket(x, z, 1f) }
        onX(fenceX, sy0 + 0.6f, plotD - 0.3f, 0f, fh) { y, z, _, _ -> picket(y, z, 0.86f) }
    }

    /** The gate in the right fence, standing a little open on its posts. */
    private fun gate() {
        iso.box(gA - 0.12f, sy0 - 0.12f, 0f, 0.24f, 0.24f, fh + 1.4f, plankL, plankM, plankD)
        iso.box(gB - 0.12f, sy0 - 0.12f, 0f, 0.24f, 0.24f, fh + 1.4f, plankL, plankM, plankD)
        // the leaf swung in a little from its hinge at GB
        val ex = gB - 1.0f; val ey = sy0 + 0.5f
        c.polyBegin()
        c.polyAdd(iso.sx(gB, sy0), iso.sy(gB, sy0, 0.4f)); c.polyAdd(iso.sx(ex, ey), iso.sy(ex, ey, 0.4f))
        c.polyAdd(iso.sx(ex, ey), iso.sy(ex, ey, fh)); c.polyAdd(iso.sx(gB, sy0), iso.sy(gB, sy0, fh))
        val sa = iso.sx(gB, sy0); val sb = iso.sx(ex, ey)
        c.polyScan { px, py ->
            val f = ((px + 0.5f - sa) / (sb - sa)).coerceIn(0f, 1f)
            val wx = gB + (ex - gB) * f; val wy = sy0 + (ey - sy0) * f
            val z = (iso.sy(wx, wy, 0f) - (py + 0.5f)) / K
            val col = picket(f * 1.1f + 0.05f, z, 0.92f)
            if (col != 0) c.set(px, py, col) else if (abs(z - (1.4f + fh - 1.6f) / 2f - 0.1f - (z - 1.4f) * 0f) < 0.3f && f > 0.05f && f < 0.95f) c.set(px, py, plankD)
        }
    }

    /** The fontanella (Friuli): a green cast-iron column, a spout, the water running into the grate at its foot. */
    private fun fontanella() {
        val cx = iso.sx(fontX, fontY); val base = iso.sy(fontX, fontY, 0f)
        val hh = 9f * K
        val g = Col.hex(0x2E5A3A); val gl = Col.hex(0x4E8A5A); val gd = Col.hex(0x1E3A26)
        drum(cx, base, base - hh, 0.9f * K, 0.45f * K, gl, g, gd)
        c.fillEllipse(cx, base - hh, 1.2f * K, 0.7f * K, gl)
        c.fillRect(floor(cx + 0.8f * K).toInt(), floor(base - hh * 0.62f).toInt(), (1.2f * K).toInt(), max(1, detail), g)
        if (!winter) {
            val wx = floor(cx + 2f * K).toInt()
            for (yy in floor(base - hh * 0.62f).toInt() until floor(base).toInt()) if ((yy + (t * 12).toInt()) % 3 != 0) c.set(wx, yy, Col.hex(0xC8E4F4))
        }
        c.fillEllipse(cx + K, base, 1.6f * K, 0.6f * K, Col.hex(0x3A3A3A))
    }

    // ------------------------------------------------------------------ the chestnut and the bench

    /** The chestnut tree: a stout trunk, a broad crown of clumps; candles of flowers in May, gold in October, bare in winter. */
    private fun chestnut() {
        val bx = iso.sx(treeX, treeY); val by = iso.sy(treeX, treeY, 0f)
        val trunkH = 12f * K; val tw = 1.6f * K
        for (yy in 0 until trunkH.toInt()) for (xx in floor(bx - tw).toInt()..floor(bx + tw).toInt()) {
            val u = (xx + 0.5f - bx) / tw
            c.set(xx, floor(by - yy).toInt(), if (u < -0.4f) Col.hex(0x7A6452) else if (u > 0.5f) Col.hex(0x3E3226) else Col.hex(0x5E4A3A))
        }
        val cx = bx; val cy = by - trunkH - 7f * K
        if (winter) {
            for (b in 0 until 7) {
                val a = -PI.toFloat() / 2f + (b - 3f) * 0.4f
                val len = (8f + Noise.rnd(b, 221) * 5f) * K
                c.line(floor(bx).toInt(), floor(by - trunkH).toInt(), floor(bx + cos(a) * len).toInt(), floor(by - trunkH + sin(a) * len).toInt(), Col.hex(0x4A3A2C))
                c.set(floor(bx + cos(a) * len * 0.6f).toInt(), floor(by - trunkH + sin(a) * len * 0.6f).toInt() - 1, Pal.SNOW_L)
            }
            return
        }
        val (d, m, l) = when (env.season) {
            Season.AUTUMN -> Triple(Col.hex(0x8A4A16), Col.hex(0xC07A28), Col.hex(0xE8B048))
            Season.SPRING -> Triple(Col.hex(0x3E7A34), Col.hex(0x62A248), Col.hex(0x9ACC66))
            else -> Triple(Col.hex(0x245A2A), Col.hex(0x387A36), Col.hex(0x5E9C46))
        }
        val bloom = env.month == 5
        for (k in 0 until 11) {
            val a = k * 2.3f
            val rr = (5f + Noise.rnd(k, 223) * 2.4f) * K
            val px = cx + sin(a) * 6f * K; val py = cy + cos(a) * 4f * K - (if (k > 8) 3.5f * K else 0f)
            for (yy in floor(py - rr).toInt()..floor(py + rr).toInt()) for (xx in floor(px - rr).toInt()..floor(px + rr).toInt()) {
                val dx = (xx + 0.5f - px) / rr; val dy = (yy + 0.5f - py) / (rr * 0.9f)
                val q = dx * dx + dy * dy
                if (q > 1f) continue
                if (q > 0.82f && Dither.at(xx, yy) < 0.4f) continue
                val lit = -dx * 0.5f - dy * 0.75f + (Noise.rnd(xx / detail, yy / detail, 225) - 0.5f) * 0.4f
                var col = if (lit > 0.4f) l else if (lit > -0.2f) m else d
                // the white candles of its flowers standing up out of the leaves in May
                if (bloom && Noise.rnd(xx / (2 * detail), yy / (3 * detail), 227) < 0.05f) col = Col.hex(0xF4EEE0)
                c.set(xx, yy, col)
            }
        }
    }

    /** The teacher's bench under the chestnut, along the left fence: a plank seat and back on two legs. */
    private fun bench() {
        val xa = 0.75f; val xb = 1.35f
        iso.box(xa, benchY0, benchZ - 0.5f, xb - xa, benchY1 - benchY0, 0.5f, if (winter) Pal.SNOW_L else plankL, plankM, plankD)
        iso.box(xa - 0.1f, benchY0, benchZ, 0.12f, benchY1 - benchY0, 3.2f, plankL, plankM, plankD)
        for (yy in floatArrayOf(benchY0 + 0.15f, benchY1 - 0.3f)) iso.box(xb - 0.2f, yy, 0f, 0.14f, 0.14f, benchZ - 0.5f, plankM, plankD, Pal.WOOD_X)
    }

    /** Soap bubbles blown from beside the bench, drifting up over the lawn, catching the light (how many: `bubbles`). */
    private fun bubbles() {
        val n = (fxOn("bubbles") * 14f).toInt()
        if (n <= 0) return
        s.fx {
            for (j in 0 until n) {
                val ph = ((t * 0.18 + Noise.rnd(j, 231)) % 1.0).toFloat()
                val x = benchPX + 0.8f + ph * (2.5f + Noise.rnd(j, 232) * 3f)
                val y = benchPY - 0.6f + (Noise.rnd(j, 233) - 0.5f) * 2f
                val z = 9f + ph * 22f + sin(t * 1.3 + j).toFloat() * 1.5f
                val cx = iso.sx(x, y); val cy = iso.sy(x, y, z)
                val r = (1.0f + Noise.rnd(j, 234) * 0.8f) * K * 0.6f + 0.6f
                val a = 0.75f * (1f - ph * ph)
                c.penEmissive = true
                arcBlend(cx, cy, r, Col.hex(0xE8F4FF), a)
                c.blend(floor(cx - r * 0.4f).toInt(), floor(cy - r * 0.4f).toInt(), Col.hex(0xFFFFFF), a)
                c.blend(floor(cx + r * 0.5f).toInt(), floor(cy + r * 0.3f).toInt(), Col.hex(0xF0A8E8), a * 0.6f)
                c.penEmissive = false
            }
        }
    }

    private fun arcBlend(cx: Float, cy: Float, r: Float, col: Int, a: Float) {
        val n = max(8, (r * 6f).toInt())
        for (i in 0 until n) {
            val ang = i * 2f * PI.toFloat() / n
            c.blend(floor(cx + cos(ang) * r).toInt(), floor(cy + sin(ang) * r).toInt(), col, a)
        }
    }

    // ------------------------------------------------------------------ the swing

    /** The near seat's angle now (radians, + toward us): `swing`, else a tap's, else still. */
    private fun seatAngle(): Float {
        val e = fx("swing") ?: 0f
        var amp = e * 0.9f
        poked("swing")?.let { pk ->
            val a = pk.age(t); val dur = if (pk.step == 0) 2.4f else 3.2f
            if (a < dur) amp = max(amp, (1f - a / dur) * (if (pk.step == 0) 0.5f else 0.85f))
        }
        return sin(t * 2.6).toFloat() * amp
    }

    /** The swing: its two A-frames of logs, the bar across them, the two seats on their ropes, the near one swinging. */
    private fun swing() {
        for (lx in floatArrayOf(lx0, lx1)) {
            rod(lx, swY - 0.9f, 0f, lx, swY, barZ, Pal.LOG_M, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
            rod(lx, swY + 0.9f, 0f, lx, swY, barZ, Pal.LOG_M, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
            rod(lx, swY - 0.45f, barZ * 0.45f, lx, swY + 0.45f, barZ * 0.45f, Pal.LOG_D)
        }
        rod(lx0 - 0.25f, swY, barZ + 0.3f, lx1 + 0.25f, swY, barZ + 0.3f, Pal.LOG_M, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
        seat(seat1, 0f)
        seat(seat0, seatAngle())
    }

    /** A seat at [sx] along the bar, swung by [a] (radians, toward +y): two ropes down to a plank. */
    private fun seat(sx: Float, a: Float) {
        val len = barZ - seatZ
        // the rope's length in cells along y and nominal px down (a cell is 4 px along y on the screen, 2 down)
        val dy = sin(a) * len / 2f; val dz = cos(a) * len
        val y = swY + dy; val z = barZ - dz
        for (o in floatArrayOf(-0.3f, 0.3f)) {
            val ax = iso.ix(sx + o, swY); val ay = iso.iy(sx + o, swY, barZ)
            val bx = iso.ix(sx + o, y); val by = iso.iy(sx + o, y, z)
            c.line(ax, ay, bx, by, ropeC)
            if (fine) c.line(ax + 1, ay, bx + 1, by, Col.hex(0xA89670))
        }
        iso.box(sx - 0.38f, y - 0.22f, z - 0.4f, 0.76f, 0.44f, 0.4f, plankL, plankM, plankD)
    }

    // ------------------------------------------------------------------ the slide

    /** The slide: four posts, the platform with its rail, the ladder up its back, the chute down toward us, polished. */
    private fun slide() {
        val w = 0.62f
        val xa = slX - w / 2f; val xb = slX + w / 2f
        // the posts and the platform, the ladder at the back
        for (px in floatArrayOf(xa, xb)) for (py in floatArrayOf(slY0 - 0.7f, slY0)) iso.box(px - 0.06f, py - 0.06f, 0f, 0.12f, 0.12f, slZ, plankM, plankD, Pal.WOOD_X)
        iso.box(xa, slY0 - 0.7f, slZ, w, 0.7f, 0.4f, plankL, plankM, plankD)
        for (px in floatArrayOf(xa, xb)) rod(px, slY0 - 0.7f, slZ + 0.4f, px, slY0 - 0.7f, slZ + 4f, plankM, plankL, plankM, plankD)
        rod(xa, slY0 - 0.7f, slZ + 4f, xb, slY0 - 0.7f, slZ + 4f, plankL, plankL, plankM, plankD)
        for (px in floatArrayOf(xa + 0.05f, xb - 0.05f)) rod(px, slY0 - 1.7f, 0f, px, slY0 - 0.7f, slZ, plankM, plankL, plankM, plankD)
        for (k in 1..5) { val f = k / 6f; rod(xa + 0.05f, slY0 - 1.7f + f, slZ * f, xb - 0.05f, slY0 - 1.7f + f, slZ * f, plankL) }
        // the chute: its bed, its two side boards, from the platform down to the lawn
        quadFill(xa, slY0, slZ + 0.3f, xb, slY0, slZ + 0.3f, xb, slY1, 0.6f, xa, slY1, 0.6f) { px, py ->
            val x = (px - iso.sx(xa, 0f) + 0f)
            val v = Noise.rnd(px / max(1, detail), py / max(1, detail), 241)
            if (v < 0.08f) Col.hex(0xE8E4D8) else Col.hex(0xC8C4B8)
        }
        for (px in floatArrayOf(xa, xb)) {
            quadFill(px, slY0, slZ + 0.3f, px, slY1, 0.6f, px, slY1, 1.6f, px, slY0, slZ + 1.4f) { _, _ -> if (px == xb) plankD else plankL }
        }
        // the lip at its foot
        iso.box(xa, slY1, 0f, w, 0.25f, 0.6f, plankL, plankM, plankD)
    }

    // ------------------------------------------------------------------ the hopscotch and the ball

    /**
     * The hopscotch chalked on the flagstones of the path down the middle: one, two, three single; four and five side by side;
     * six single; seven and eight side by side; the half circle of the "nebo" at the far end; the numbers in each.
     */
    private fun hopscotch() {
        onFlat(0.05f, hsX0, hsY0, hsX1, hsY1) { x, y, px, py ->
            // the flagstones under it
            val fu = (x - hsX0) / ((hsX1 - hsX0) / 2f); val fv = (y - hsY0) / 0.62f
            val joint = (fu - floor(fu)) < 0.06f || (fv - floor(fv)) < 0.08f
            var col = if (winter) Pal.SNOW_L else if (joint) Col.hex(0x8A8478) else if (Noise.rnd(floor(fu).toInt(), floor(fv).toInt(), 251) < 0.5f) flagL else flagM
            if (!winter && chalkAt(x, y)) col = chalkC
            col
        }
    }

    /** Whether ([x], [y]) is on a chalk line of the hopscotch (its squares from the near end, the nebo at the far one). */
    private fun chalkAt(x: Float, y: Float): Boolean {
        val w = hsX1 - hsX0; val cx = (hsX0 + hsX1) / 2f
        val sq = 0.62f
        val lw = if (fine) 0.035f else 0.06f
        // rows from the near end (y high) toward the far end: 1, 2, 3 single, 4|5, 6, 7|8, then the nebo
        val row = floor((hsY1 - 0.15f - y) / sq).toInt()
        val fr = (hsY1 - 0.15f - y) - row * sq
        if (row < 0 || row > 6) return false
        val double = row == 3 || row == 5
        val half = if (double) w * 0.48f else w * 0.26f
        if (abs(x - cx) > half + lw) return false
        if (row == 6) {
            // the nebo: a half circle
            val r = hypot((x - cx) / (w * 0.48f), fr / sq)
            return abs(r - 1f) < lw * 2f || fr < lw
        }
        if (fr < lw || fr > sq - lw) return true
        if (abs(abs(x - cx) - half) < lw) return true
        if (double && abs(x - cx) < lw) return true
        // the numbers, small marks in the middle of each square
        val num = when (row) { 0 -> 1; 1 -> 2; 2 -> 3; 3 -> if (x < cx) 4 else 5; 4 -> 6; else -> if (x < cx) 7 else 8 }
        val mx = if (double) (if (x < cx) cx - half / 2f else cx + half / 2f) else cx
        return fine && abs(fr - sq / 2f) < 0.12f && abs(x - mx) < 0.02f + num * 0.008f && (num % 2 == 0 || abs(fr - sq / 2f) < 0.06f)
    }

    /** The red ball: on the lawn by itself; 0 lost under the slide's foot (`ball`), rising it rolls out to its place. */
    private fun ball() {
        val b = fx("ball") ?: 1f
        val hx = slX - 0.05f; val hy = slY1 - 0.45f
        val e = 1f - (1f - b) * (1f - b)
        val x = hx + (ballX - hx) * e; val y = hy + (ballY - hy) * e
        val cx = iso.sx(x, y); val cy = iso.sy(x, y, 0f)
        val r = 1.3f * K * 0.5f + 0.8f
        c.fillCircle(cx, cy - r, r, ballC)
        c.fillCircle(cx - r * 0.35f, cy - r * 1.35f, r * 0.42f, ballL)
        // a white band round it
        c.hline(floor(cx - r * 0.8f).toInt(), floor(cx + r * 0.8f).toInt(), floor(cy - r * 0.8f).toInt(), Col.hex(0xF4F0E8))
    }

    // ------------------------------------------------------------------ the sandpit

    private fun sandpitFrame() {
        val t = 0.16f
        iso.box(spX0 - t, spY0 - t, 0f, spX1 - spX0 + 2 * t, t, 1.8f, plankL, plankM, plankD)
        iso.box(spX0 - t, spY0, 0f, t, spY1 - spY0, 1.8f, plankL, plankM, plankD)
        // the near planks drawn after the sand (sandIn), their tops as seats
    }

    private fun sandIn() {
        onFlat(0.8f, spX0, spY0, spX1, spY1) { x, y, px, py ->
            val n = Noise.rnd(px / max(1, detail), py / max(1, detail), 261)
            val dig = Noise.v2(x * 1.6f, y * 1.6f, 263)
            var col = if (n < 0.2f) sandD else if (n < 0.8f) sandM else sandL
            if (dig > 0.7f) col = Col.scale(col, 0.9f)
            if (winter) col = if (Dither.at(px, py) < 0.3f) Pal.SNOW_M else Pal.SNOW_L
            col
        }
        val t = 0.16f
        iso.box(spX0 - t, spY1, 0f, spX1 - spX0 + 2 * t, t, 1.8f, plankL, plankM, plankD)
        iso.box(spX1, spY0, 0f, t, spY1 - spY0, 1.8f, plankL, plankM, plankD)
        if (winter) {
            iso.line(spX0, spY1 + t / 2f, 1.9f, spX1 + t, spY1 + t / 2f, 1.9f, Pal.SNOW_L)
            iso.line(spX1 + t / 2f, spY0, 1.9f, spX1 + t / 2f, spY1, 1.9f, Pal.SNOW_L)
        }
    }

    /** The sand castle (`castle`): a mound, then its wall and towers, at 1 a little flag on the top. */
    private fun castle() {
        val k = fxOn("castle")
        if (k <= 0.02f || winter) return
        prop {
            val cx = iso.sx(3.6f, 8.0f); val base = iso.sy(3.6f, 8.0f, 0.8f)
            val mound = min(1f, k / 0.4f)
            c.fillEllipse(cx, base - 1.2f * K * mound, 4f * K * mound, 2.2f * K * mound, sandD)
            c.fillEllipse(cx - K * 0.5f, base - 1.6f * K * mound, 3.2f * K * mound, 1.6f * K * mound, sandM)
            if (k > 0.4f) {
                val wall = min(1f, (k - 0.4f) / 0.3f)
                for ((ox, hh) in listOf(-2.6f to 4.5f, 0f to 6.5f, 2.6f to 4.5f)) {
                    val tx = cx + ox * K; val th = hh * K * wall
                    c.fillRect(floor(tx - K).toInt(), floor(base - 2f * K - th).toInt(), 2 * K, th.toInt() + 1, sandM)
                    c.vline(floor(tx - K).toInt(), floor(base - 2f * K - th).toInt(), floor(base - 2f * K).toInt(), sandL)
                    if (wall > 0.9f) for (q in 0 until 2) c.fillRect(floor(tx - K + q * K).toInt(), floor(base - 2f * K - th - detail).toInt(), max(1, detail), max(1, detail), sandM)
                }
            }
            if (k > 0.95f) {
                val fxp = floor(cx).toInt(); val top = floor(base - 2f * K - 6.5f * K).toInt()
                c.vline(fxp, top - 4 * detail, top, Pal.WOOD_D)
                c.fillRect(fxp + 1, top - 4 * detail, 2 * detail, detail + 1, Col.hex(0xE84030))
            }
        }
    }

    /** The bucket in the sand: a little blue tin pail with its handle. */
    private fun bucket() {
        val bx = 3.15f; val by = 9.05f
        val cx = iso.sx(bx, by); val base = iso.sy(bx, by, 0.8f)
        val hh = 2.4f * K; val rT = 1.1f * K; val rB = 0.8f * K
        for (yy in 0 until hh.toInt()) {
            val f = yy / hh; val r = rB + (rT - rB) * f
            for (xx in floor(cx - r).toInt()..floor(cx + r).toInt()) {
                val u = (xx + 0.5f - cx) / r
                c.set(xx, floor(base - yy).toInt(), if (u < -0.4f) Col.hex(0x5A9ADA) else if (u > 0.5f) Col.hex(0x2A5A9A) else Col.hex(0x3A78C0))
            }
        }
        c.fillEllipse(cx, base - hh, rT, rT * 0.4f, Col.hex(0x1E3E6A))
        arc(cx, base - hh - 0.5f, rT * 0.95f, 1.4f * K, Col.hex(0xC8D0D8), PI.toFloat(), 2f * PI.toFloat())
    }

    /** The spade stuck in the sand by the bucket: a red blade, a wooden handle. */
    private fun spade() {
        val bx = 2.95f; val by = 7.55f
        rod(bx, by, 0.8f, bx - 0.25f, by - 0.3f, 6f, Pal.WOOD_L, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        val cx = iso.sx(bx, by); val cy = iso.sy(bx, by, 0.8f)
        c.fillRect(floor(cx - 0.7f * K).toInt(), floor(cy - 1.2f * K).toInt(), (1.4f * K).toInt(), (1.2f * K).toInt(), Col.hex(0xD8402E))
        c.fillRect(floor(cx - 0.7f * K).toInt(), floor(cy - 1.2f * K).toInt(), max(1, detail), (1.2f * K).toInt(), Col.hex(0xF07060))
    }

    // ------------------------------------------------------------------ the little house and the climbing frame

    /** The little wooden house: plank walls, a door hole, a window, a red roof; snow on it in winter. */
    private fun house() {
        val hh = 7.5f
        iso.box(hoX0, hoY0, 0f, hoX1 - hoX0, hoY1 - hoY0, hh, plankL, plankM, plankD)
        // the door hole on its front (y = HOY1) and a window on its right (x = HOX1)
        onY(hoY1 + 0.01f, hoX0 + 0.35f, hoX0 + 0.95f, 0f, 5f) { _, z, _, _ -> if (z > 4.6f) plankD else Col.hex(0x2A1E14) }
        onX(hoX1 + 0.01f, hoY0 + 0.45f, hoY0 + 1.05f, 2.8f, 5.2f) { y, z, _, _ -> if (abs(y - hoY0 - 0.75f) < 0.05f || abs(z - 4f) < 0.25f) plankL else Col.hex(0x34465A) }
        // the planks' seams
        var x = hoX0 + 0.3f
        while (x < hoX1) { iso.line(x, hoY1, 0f, x, hoY1, hh, plankD); x += 0.3f }
        val (rl, rr) = if (winter) Pal.SNOW_L to Pal.SNOW_M else Pal.ROOF_L to Pal.ROOF_D
        iso.gableX(hoX0 - 0.2f, hoY0 - 0.2f, hh, hoX1 - hoX0 + 0.4f, hoY1 - hoY0 + 0.4f, 3.6f, rr, rl, plankL, Pal.ROOF_D)
    }

    /** The climbing frame: a cube of poles, rungs between them at three heights, a cross of poles on top. */
    private fun climber() {
        val hh = 12f
        val xs = floatArrayOf(clX0, clX1); val ys = floatArrayOf(clY0, clY1)
        // the back poles, then the rungs, then the front ones
        rod(clX0, clY0, 0f, clX0, clY0, hh, Pal.LOG_M, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
        for (z in floatArrayOf(4f, 8f, hh)) {
            rod(clX0, clY0, z, clX1, clY0, z, Pal.LOG_M, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
            rod(clX0, clY0, z, clX0, clY1, z, Pal.LOG_M, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
        }
        rod(clX1, clY0, 0f, clX1, clY0, hh, Pal.LOG_M, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
        rod(clX0, clY1, 0f, clX0, clY1, hh, Pal.LOG_M, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
        for (z in floatArrayOf(4f, 8f, hh)) {
            rod(clX1, clY0, z, clX1, clY1, z, Pal.LOG_M, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
            rod(clX0, clY1, z, clX1, clY1, z, Pal.LOG_M, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
        }
        rod(clX1, clY1, 0f, clX1, clY1, hh, Pal.LOG_L, Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
        if (winter) for (x in xs) for (y in ys) iso.px(x, y, hh + 0.4f, Pal.SNOW_L)
    }

    // ------------------------------------------------------------------ the kite

    /** The kite (`kite`): up over the playground on its string from beside the swing, its tail of bows fluttering. */
    private fun kite() {
        val k = fxOn("kite")
        if (k <= 0.02f) return
        val ax = iso.sx(swingPX - 0.3f, swingPY - 0.2f); val ay = iso.sy(swingPX - 0.3f, swingPY - 0.2f, 9f)
        val wind = gust
        val kx = ax + (18f + 10f * k) * K + sin(t * 0.9).toFloat() * 2f * K * wind
        val ky = ay - (10f + 34f * k) * K + cos(t * 1.3).toFloat() * 1.5f * K
        prop(outline = 0) {
            // the string, sagging
            val n = 24
            var lx = ax; var ly = ay
            for (i in 1..n) {
                val f = i / n.toFloat()
                val x = ax + (kx - ax) * f; val y = ay + (ky - ay) * f + sin(f * PI.toFloat()) * 4f * K
                c.line(floor(lx).toInt(), floor(ly).toInt(), floor(x).toInt(), floor(y).toInt(), Col.hex(0xE8E4D8))
                lx = x; ly = y
            }
        }
        prop {
            // the kite: a diamond in red and yellow, its spars
            val r = 3.2f * K
            c.polyBegin(); c.polyAdd(kx, ky - r * 1.3f); c.polyAdd(kx + r, ky); c.polyAdd(kx, ky + r * 1.1f); c.polyAdd(kx - r, ky)
            c.polyFill { px, py -> if ((px < kx) == (py < ky)) Col.hex(0xE8402E) else Col.hex(0xF0C030) }
            c.line(floor(kx).toInt(), floor(ky - r * 1.3f).toInt(), floor(kx).toInt(), floor(ky + r * 1.1f).toInt(), Pal.WOOD_D)
            c.line(floor(kx - r).toInt(), floor(ky).toInt(), floor(kx + r).toInt(), floor(ky).toInt(), Pal.WOOD_D)
            // its tail of bows
            for (j in 1..5) {
                val tx = kx - sin(t * 3.0 + j).toFloat() * 1.5f * K * wind; val ty = ky + r * 1.1f + j * 2.2f * K
                c.fillRect(floor(tx - K * 0.6f).toInt(), floor(ty).toInt(), (1.2f * K).toInt(), max(1, detail), if (j % 2 == 0) Col.hex(0x3A78C0) else Col.hex(0xE8402E))
            }
        }
    }

    private fun lights() {
        if (env.windows > 0.35f) s.light(iso.sx(sx1 - 0.4f, sy0 + 0.6f), iso.sy(sx1 - 0.4f, sy0 + 0.6f, sh - 6f), 70f, 0.85f * env.windows)
    }
}
