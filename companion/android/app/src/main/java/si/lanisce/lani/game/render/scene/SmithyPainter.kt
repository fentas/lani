package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.scene.Poke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V kovačnici: the village smithy as a diorama, a stone room cut open on its own plot at the edge of the village.
 * The brick forge stands in the back corner under its iron hood and a stub of chimney, the coals glowing; the big
 * leather bellows beside it breathe on their own, and each breath brightens the coals and sends sparks up. A chain
 * hangs from a hook in the wall's beam by the hood, the horseshoes on their nails over the bellows, then the plank
 * door with its iron straps; on the left wall a small deep window, the axe on two pegs and a cart wheel waiting for
 * its tyre. In the middle the anvil on its stump with the hammer on it and the tongs against it, the quenching
 * bucket steaming, a crate of nails. Outside, the yard: a water trough, a tethering post on a patch of cobbles, a
 * horseshoe lost in the dirt, a grindstone and a woodpile. The room is dim by day; at night the forge lights it.
 *
 * Closer up ([fine]: detail 2 and 3) the same smithy is drawn finer: the stones and bricks bevelled with thin
 * ragged joints, the flagstones worn and cracked, grain in the timbers, boards and logs, round rivets on the hood,
 * the straps and the hoops, the coals in lumps with glowing cracks, the anvil's face polished and its horn lit,
 * the chain's links, the bellows' leather folds and brass nails, handles and heads on the tools.
 *
 * Tapped ([pokes]), the anvil rings: its sound spreads, a glint runs over its face and sparks jump off it; tapped
 * again, it rings twice.
 *
 * The smithy grows with its level ([si.lanisce.lani.game.scene.SceneFixtures]): at the first the coals only glow in
 * the forge; the big bellows and the chain come with the second; the cart wheel, the axe on its pegs and a second,
 * smaller anvil on its stump in front of the wheel with the third.
 *
 * At night ([beds]: "cot") Kovač Tone sleeps in the smithy, as smiths did, on a cot of planks along the left wall under the
 * window, its head against the forge's warm bricks, on a straw mattress under a charcoal-grey horse blanket with a rust
 * stripe; the cot is there only while he is on it. He banked the fire before he lay down (his ritual at 21:45): the
 * bellows rest, the coals glow low under their ash, no flames and no sparks, a thin thread of smoke, and the forge's light
 * is small (a dialog that fires the forge up has it roar as ever).
 */
internal class SmithyPainter : DioramaPainter() {
    override val art = "smithy"
    override val pokes = listOf(Poke("anvil", listOf(1.2, 1.9), rest = 2.5))

    /** At night Tone sleeps on his cot by the forge. */
    override val beds = listOf(COT)
    override val plotW = 12f
    override val plotD = 12f
    override val rise = 26.5f

    // the room: the back walls' inner faces at x = X0 (left) and y = Y0 (right), THICK deep, stone up to WALL_H under a
    // timber plate to PLATE_H; the flagstone floor, FL above the yard, runs out to the cut at X1 (front right) and Y1 (front left)
    private val x0 = 1f; private val y0 = 1f; private val x1 = 8.4f; private val y1 = 8.6f
    private val thick = 0.55f
    private val wallH = 23f; private val plateH = 25f
    private val fl = 1.5f

    // the forge in the back corner out to (FX1, FY1), its top at FT, the fire pot at (POT_X, POT_Y); the hood over it from
    // HZ0 (out to HX1, HY1) up to HZ1, where the chimney (out to CX1, CY1) rises to CHIM_H
    private val fx1 = 3.6f; private val fy1 = 3.4f; private val ft = fl + 8f
    private val potX = 2.45f; private val potY = 2.3f
    private val hx1 = 3.35f; private val hy1 = 3.15f; private val hz0 = fl + 18f; private val hz1 = fl + 23f
    private val cx1 = 2.15f; private val cy1 = 2.15f; private val chimH = 28f
    private val sk = 1.6f // the hood's skirt

    // the window in the left wall, the door in the right one, the chain from the right wall's plate
    private val wa = 4.45f; private val wb = 5.95f; private val za = fl + 10.5f; private val zb = fl + 18f
    private val da = 6.35f; private val db = 8.15f; private val dz = fl + 19f
    private val doorFr = 0.25f; private val doorTop = dz + 2.4f
    private val chainX = 3.8f

    // the anvil on its stump: the stump's top, the anvil's face
    private val ax = 5.35f; private val ay = 6.35f; private val stumpH = 3.4f; private val anvilTop = fl + stumpH + 3.9f

    private var floorId = 0

    private val stoneHi = Col.hex(0x9C9286); private val stoneL = Col.hex(0x847A6E); private val stoneM = Col.hex(0x6C645A)
    private val stoneD = Col.hex(0x544D46); private val mortar = Col.hex(0x3A342F); private val sootC = Col.hex(0x1E1A18)
    private val slabL = Col.hex(0x7C7368); private val slabM = Col.hex(0x696157); private val slabD = Col.hex(0x575048); private val joint = Col.hex(0x2E2A26)
    private val ironD = Col.hex(0x1C1A1E); private val iron = Col.hex(0x2E2B30); private val ironM = Col.hex(0x46434A)
    private val ironL = Col.hex(0x6A6670); private val ironHi = Col.hex(0x9C98A2); private val steel = Col.hex(0xB4B0BA)
    private val brickL = Col.hex(0xB06A4C); private val brickM = Col.hex(0x94533C); private val brickD = Col.hex(0x723E2E); private val brickJ = Col.hex(0x4E3C34)

    /** Closer up (detail 2 or 3): the finer touches the scene's own canvas has no room for. */
    private val fine: Boolean get() = detail > 1

    /** The length of ([x], [y]): closer up by the quicker square root (many more pixels ask), as before on the scene's canvas. */
    private fun hyp(x: Float, y: Float): Float = if (detail > 1) sqrt(x * x + y * y) else hypot(x, y)

    /** A smithy is dim even by day, so the forge's glow shows; at night the forge is nearly all the light there is. */
    override fun ambient(): FloatArray {
        val dim = 0.86f
        val warm = env.dark * 0.05f
        return floatArrayOf(env.ambR * dim + warm, env.ambG * dim + warm * 0.75f, env.ambB * dim + warm * 0.4f)
    }

    /** The bellows' breath: 0 = squeezed shut, 1 = wide open; they close fast and open slowly. */
    private fun breath(): Float {
        val ph = ((t / 2.6) % 1.0).toFloat()
        return if (ph < 0.25f) 1f - ph / 0.25f else (ph - 0.25f) / 0.75f
    }

    override fun paint() {
        fit()
        vignette()
        val lit = env.windows > 0.35f
        // Tone asleep by it: the fire banked for the night, the bellows at rest (unless a dialog fires the forge up)
        val banked = asleepHere && fxOn("forge") < 0.02f
        // the bellows come with the smithy's second level ([SceneFixtures]); before them the coals only glow and flicker
        val bellows = there("bellows")
        val br = if (banked) REST else if (bellows) breath() else 0.3f + 0.12f * sin(t * 1.7).toFloat()
        // the forge roaring (a dialog's "forge"): the coals and flames as if the bellows blew hard, whatever they do
        val heat = br * (1f - 0.85f * fxOn("forge"))
        yardPlot()
        // tufts first: they lie flat, and whatever stands in front of them covers them
        tufts(80, 13) { x, y -> busy(x, y) }
        // the room's shade on the yard beside its right side
        shade(iso.sx(x1 + 0.45f, 4.8f), iso.sy(x1 + 0.45f, 4.8f, 0f), 4.2f * 4 * K, 1.0f * 4 * K, 0.84f, groundId)

        floor()
        walls()
        thing("window") { window() }
        thing("door") { door(lit) }
        thing("horseshoe", slop = 1) { horseshoes() }
        if (there("axe")) thing("axe", slop = 1) { axe() }

        // the forge and its fire, the bellows, the hood and chimney over them, the chain beside them
        thing("forge") { forge() }
        thing("fire", outline = 0) { coals(heat, banked) }
        if (bellows) thing("bellows", slop = 1) { bellows(br) }
        prop { hood() }
        // Tone asleep on his cot along the left wall, his head by the forge's warm bricks
        peopleAt(COT).firstOrNull()?.let { p ->
            val top = cot()
            sleeperOn(p, COT_A, COT_C, COT_B, COT_D, top, alongY = true, SOOT_WOOL)
        }
        if (there("chain")) thing("chain", slop = 1) { chain() }
        if (there("wheel")) thing("wheel") { wheel() }

        for (p in peopleAt("forge")) { footShadow(2.2f, 4.6f); personAt(p, 2.2f, 4.6f, fl) }
        for (p in peopleAt("door")) { footShadow(7.2f, 1.95f); personAt(p, 7.2f, 1.95f, fl, flip = true) }
        // the third level's second anvil, a smaller one for the finer work, in front of the wheel
        if (there("wheel")) prop { smallAnvil() }
        thing("nails", slop = 1) { nails() }
        thing("anvil") { anvil() }
        anvilSparks(fxOn("sparks"))
        anvilRing()
        thing("hammer", slop = 2) { hammer() }
        thing("tongs", slop = 2) { tongs() }
        for (p in peopleAt("anvil")) { footShadow(6.6f, 5.6f); personAt(p, 6.6f, 5.6f, fl, flip = true) }
        thing("bucket", slop = 1) { bucket() }

        // the yard
        prop { trough() }
        tether()
        if (!winter) prop { lostShoe() }
        woodpile()
        prop { grindstone() }

        fireflies(iso.ix(3f, 12f), iso.iy(9f, 9f, 18f), iso.ix(12f, 3f), iso.iy(12f, 12f, 0f), 8, 5)
        lights(heat, banked)
        sunPatch()
    }

    /**
     * Tone's cot for the night: a frame of planks on short legs along the left wall, its head against the forge, a straw
     * mattress on it with a linen pillow at the head. Returns the mattress's top.
     */
    private fun cot(): Float {
        val fz = fl + 2.2f; val mh = 1.3f
        prop {
            for ((lx, ly) in listOf(COT_B - 0.1f to COT_D - 0.1f, COT_A + 0.1f to COT_D - 0.1f, COT_B - 0.1f to COT_C + 0.1f)) iso.post(lx, ly, fl, fz, Pal.WOOD_D)
            iso.box(COT_A, COT_C, fz - 0.8f, COT_B - COT_A, COT_D - COT_C, 0.8f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
            iso.line(COT_B, COT_C, fz, COT_B, COT_D, fz, Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.2f))
            strawMattress(COT_A + 0.04f, COT_C + 0.04f, COT_B - 0.04f, COT_D - 0.04f, alongY = true, z0 = fz, h = mh)
        }
        return fz + mh
    }

    // ------------------------------------------------------------------ small pieces closer up

    /** A round head a scene px across (a nail, a rivet) at ([x], [y]): [col], lit at its top left, dark at its bottom right. */
    private fun stud(x: Int, y: Int, col: Int, hi: Int, lo: Int) {
        val n = detail
        c.fillRect(x, y, n, n, col)
        c.set(x, y, hi)
        if (n > 1) c.set(x + n - 1, y + n - 1, lo)
    }

    /**
     * A rod (a handle, a crank, a leg of the tongs) from picture point ([ax], [ay]) to ([bx], [by]), [n] px thick
     * (stacked sideways when steep, downward when not): [col], its first px [hi], its last [lo].
     */
    private fun rod(ax: Int, ay: Int, bx: Int, by: Int, col: Int, hi: Int, lo: Int, n: Int = detail) {
        val steep = abs(by - ay) > abs(bx - ax)
        for (o in 0 until n) {
            val cl = if (o == 0) hi else if (o == n - 1) lo else col
            if (steep) c.line(ax + o, ay, bx + o, by, cl) else c.line(ax, ay + o, bx, by + o, cl)
        }
    }

    /** A [rod] between two world points. */
    private fun rodIso(xa: Float, ya: Float, za: Float, xb: Float, yb: Float, zb: Float, col: Int, hi: Int, lo: Int, n: Int = detail) =
        rod(iso.ix(xa, ya), iso.iy(xa, ya, za), iso.ix(xb, yb), iso.iy(xb, yb, zb), col, hi, lo, n)

    /**
     * An iron ring round ([cx], [cy]), [rx] × [ry] to the middle of its wire, the wire a scene px thick: [upper] on
     * its upper half, [lower] on its lower, its inside in shadow, a glint on its upper left.
     */
    private fun ringFine(cx: Float, cy: Float, rx: Float, ry: Float, upper: Int, lower: Int) {
        val half = detail * 0.45f + 0.15f
        val r = (rx + ry) * 0.5f
        for (py in floor(cy - ry - half).toInt()..ceil(cy + ry + half).toInt()) for (px in floor(cx - rx - half).toInt()..ceil(cx + rx + half).toInt()) {
            val dx = px + 0.5f - cx; val dy = py + 0.5f - cy
            val off = (hyp(dx / rx, dy / ry) - 1f) * r
            if (abs(off) > half) continue
            c.set(px, py, when {
                off < 1f - half && dy > 0f -> ironD
                dx < -0.35f * rx && dy < -0.35f * ry && off > -0.3f -> steel
                dy < 0f -> upper
                else -> lower
            })
        }
    }

    // ------------------------------------------------------------------ the yard

    /** The plot and its [ground] (a method of its own, so its loop over every pixel of the plot compiles on its own). */
    private fun yardPlot() = plot { x, y, px, py -> ground(x, y, px, py) }

    private fun inCobbles(x: Float, y: Float): Boolean {
        // nowhere near (the ragged edge wanders 0.35 at most)
        if (x < x1 + 0.08f || x > 11.97f || y < 4.13f || y > 10.47f) return false
        val e = (Noise.v2(x * 1.7f, y * 1.7f, 9) - 0.5f) * 0.7f
        return x > x1 + 0.45f + e && x < 11.6f + e && y > 4.5f + e && y < 10.1f + e
    }

    /** Grass, packed earth along the room's open sides and in front of the trough, a path out to the village. */
    private fun ground(x: Float, y: Float, px: Int, py: Int): Int {
        // under the room: its floor and walls cover it
        if (x > x0 - thick + 0.15f && x < x1 - 0.15f && y > y0 - thick + 0.15f && y < y1 - 0.15f) return slabD
        if (inCobbles(x, y)) return cobble(x, y, px, py)
        val g = grassAt(x, y, px, py)
        // how bare the earth is: the apron along the room, the path, the trough's puddle (not measured where it can't be)
        val apron = if (x > x0 - thick && y > y0 - thick && x < x1 + 0.8f && y < y1 + 0.8f) 1f - hyp(max(0f, x - x1), max(0f, y - y1)) / 0.8f else 0f
        val path = if (x > x1 - 0.75f && y > y1 - 0.75f && x < 12.15f && y < 12.35f) 1f - segDist(x, y, x1, y1, 11.4f, 11.6f) / 0.75f else -1f
        val trough = if (abs(x - 9.85f) < 1.4f && abs(y - 2.6f) < 2.1f) 1f - hyp((x - 9.85f) / 1.4f, (y - 2.6f) / 2.1f) else -1f
        val m = max(max(apron, path), trough)
        if (m <= 0f) return g
        val f = m * (0.75f + Noise.v2(x * 2f, y * 2f, 3) * 0.5f)
        return dirtAt(px, py, f.coerceIn(0f, 1f), g)
    }

    /** Round cobbles set in earth, staggered, lit from the upper left; mostly under snow in winter. */
    private fun cobble(x: Float, y: Float, px: Int, py: Int): Int {
        val cs = 0.44f
        val u = x / cs; val row = floor(u).toInt()
        val v = y / cs + (row and 1) * 0.5f; val col = floor(v).toInt()
        val fu = u - row - 0.5f + (Noise.rnd(row, col, 61) - 0.5f) * 0.18f
        val fv = v - col - 0.5f + (Noise.rnd(row, col, 62) - 0.5f) * 0.18f
        val d = fu * fu + fv * fv
        if (winter && (Noise.v2(x * 1.3f, y * 1.3f, 63) > 0.32f || d > 0.13f)) return if (Dither.at(px, py) < 0.5f) Pal.SNOW_M else Pal.SNOW_L
        if (d > 0.18f) {
            if (fine && Noise.rnd(px, py, 68) < 0.07f) return Col.hex(0x9A8C7A) // a grain of grit between the stones
            return if (Noise.rnd(px, py, 64) < 0.3f) Pal.DIRT_D else Col.hex(0x6E5A44)
        }
        val tone = Noise.rnd(row, col, 65)
        val base = if (tone < 0.33f) Pal.COBBLE_L else if (tone < 0.72f) Pal.COBBLE_M else Pal.COBBLE_D
        if (fine) return cobbleFine(base, fu, fv, d, px, py)
        return when {
            fu < -0.2f -> Col.mix(base, Col.hex(0xFFFFFF), 0.2f)
            fu > 0.2f || d > 0.13f -> Col.scale(base, 0.8f)
            else -> base
        }
    }

    /** A cobble closer up: rounded, a glint on its lit shoulder, its lower rim in shadow, a pit or two. */
    private fun cobbleFine(base: Int, fu: Float, fv: Float, d: Float, px: Int, py: Int): Int {
        val gu = fu + 0.15f; val gv = fv + 0.08f
        return when {
            gu * gu + gv * gv < 0.006f -> Col.mix(base, Col.hex(0xFFFFFF), 0.4f)
            d > 0.13f && fu + fv > 0.05f -> Col.scale(base, 0.68f)
            fu < -0.2f -> Col.mix(base, Col.hex(0xFFFFFF), 0.2f)
            fu > 0.2f || d > 0.13f -> Col.scale(base, 0.8f)
            Noise.rnd(px, py, 69) < 0.05f -> Col.scale(base, 0.88f)
            else -> base
        }
    }

    private fun segDist(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val u = (((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return hyp(x - ax - dx * u, y - ay - dy * u)
    }

    private fun busy(x: Float, y: Float): Boolean =
        (x < x1 + 0.3f && y < y1 + 0.3f) || inCobbles(x, y) || (x in 8.9f..10.8f && y in 0.8f..4.3f) ||
            (x in 1.4f..3.9f && y in 9.2f..11f) || (x in 4.5f..6.3f && y in 9.6f..11f) || hyp(max(0f, x - x1), max(0f, y - y1)) < 0.45f

    /** The water trough by the yard's back edge: planks on two stone blocks, iron bands, the sky in its water; ice in winter. */
    private fun trough() {
        val xa = 9.3f; val ya = 1.25f; val tw = 1.1f; val td = 2.7f; val zb = 1.2f; val zt = 5f
        for (by in floatArrayOf(ya + 0.3f, ya + td - 0.75f)) iso.box(xa + 0.15f, by, 0f, tw - 0.3f, 0.45f, zb, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        part()
        quadFill(xa, ya + td, zb, xa + tw, ya + td, zb, xa + tw, ya + td, zt, xa, ya + td, zt) { px, py -> plank(wallYz(px, py, ya + td) - zb, px, py, 1f) }
        quadFill(xa + tw, ya, zb, xa + tw, ya + td, zb, xa + tw, ya + td, zt, xa + tw, ya, zt) { px, py -> plank(wallXz(px, py, xa + tw) - zb, px, py, 0.8f) }
        for (bb in floatArrayOf(0.45f, td - 0.45f)) {
            if (fine) { troughBand(xa + tw, ya + bb, zb, zt); continue }
            iso.line(xa + tw, ya + bb, zb, xa + tw, ya + bb, zt, ironM)
            iso.line(xa + tw, ya + bb + 0.06f, zb, xa + tw, ya + bb + 0.06f, zt, ironD)
        }
        iso.top(xa, ya, zt, tw, td, if (winter) Pal.SNOW_L else Pal.WOOD_L)
        val m = 0.14f
        quadFill(xa + m, ya + m, zt, xa + tw - m, ya + m, zt, xa + tw - m, ya + td - m, zt, xa + m, ya + td - m, zt) { px, py ->
            val x = flatX(px, py, zt); val y = flatY(px, py, zt)
            if (winter) {
                if (Noise.v2(x * 3f, y * 3f, 66) > 0.55f) Pal.SNOW_L else if ((x + y * 0.3f) % 0.7f < 0.06f) Pal.ICE_L else Pal.ICE_M
            } else {
                val ripple = sin(y * 9f + t.toFloat() * 2.2f + x * 3f)
                when {
                    fine && ripple > 0.985f && Dither.at(px, py) < 0.5f -> Col.mix(Pal.WATER_L, Col.hex(0xFFFFFF), 0.45f)
                    ripple > 0.93f && Dither.at(px, py) < 0.6f -> Pal.WATER_L
                    x - xa < 0.3f -> Pal.WATER_D
                    fine && x - xa < 0.36f -> Col.mix(Pal.WATER_D, Pal.WATER_M, 0.5f)
                    else -> Pal.WATER_M
                }
            }
        }
    }

    /** An iron band down the trough's side, closer up: dark iron and lit iron, a scene px each, a rivet at each end. */
    private fun troughBand(x: Float, y: Float, za: Float, zb: Float) {
        val n = detail
        val bx = iso.ix(x, y); val top = iso.iy(x, y, zb); val bot = iso.iy(x, y, za)
        c.fillRect(bx - n, top, n, bot - top + 1, ironD)
        c.fillRect(bx, top, n, bot - top + 1, ironM)
        c.vline(bx, top, bot, ironL)
        for (z in floatArrayOf(za + 0.9f, zb - 0.9f)) stud(bx - n / 2, iso.iy(x, y, z) - n / 2, ironL, ironHi, ironD)
    }

    /** Boards laid flat, [v] px up the side: a seam between them, a lit top edge. */
    private fun plank(v: Float, px: Int, py: Int, light: Float): Int {
        val f = v / 1.27f
        val b = floor(f).toInt(); val fb = (f - b) * 1.27f
        val col = if (fine) plankFine(b, fb, px) else when {
            fb < 0.5f -> Pal.WOOD_X
            fb > 0.8f && b == 2 -> Pal.WOOD_L
            Noise.rnd(b, px / 3, 67) > 0.8f -> Pal.WOOD_D
            else -> Pal.WOOD_M
        }
        return Col.scale(col, light)
    }

    /** A board closer up: a thin seam and its shadow under it, its top edge lit, the grain in streaks along it. */
    private fun plankFine(b: Int, fb: Float, px: Int): Int {
        val e = 1f / K
        return when {
            fb < 0.3f -> Pal.WOOD_X
            fb < 0.3f + e -> Col.mix(Pal.WOOD_X, Pal.WOOD_D, 0.5f)
            fb > 1.27f - e -> Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), if (b == 2) 0.2f else 0.05f)
            fb > 0.8f && b == 2 -> Pal.WOOD_L
            Noise.rnd(b, px / (3 * detail), 67) > 0.8f -> Pal.WOOD_D
            Noise.v2(px * 0.09f / detail, fb * 4f + b * 3f, 68) > 0.7f -> Col.mix(Pal.WOOD_M, Pal.WOOD_D, 0.5f)
            else -> Pal.WOOD_M
        }
    }

    /** A tethering post on the cobbles with an iron ring, for the horse that waits to be shod; snow on its top in winter. */
    private fun tether() {
        val xp = 10.3f; val yp = 6.9f; val hp = 10f
        prop {
            iso.box(xp - 0.16f, yp - 0.16f, 0f, 0.32f, 0.32f, hp, if (winter) Pal.SNOW_L else Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
            iso.line(xp - 0.16f, yp + 0.16f, hp - 0.6f, xp + 0.16f, yp + 0.16f, hp - 0.6f, Pal.LOG_D)
            iso.line(xp - 0.16f, yp + 0.16f, 3f, xp - 0.16f, yp + 0.16f, hp - 1.5f, Pal.LOG_L)
        }
        prop(outline = 0) {
            // the ring, hung from a staple on the post's front
            val rx = iso.sx(xp, yp + 0.18f); val ry = iso.sy(xp, yp + 0.18f, hp - 3.2f)
            if (fine) {
                ringFine(rx, ry, 1.5f * K, 1.5f * K, ironHi, ironL)
                c.fillRect(rx.toInt(), ry.toInt() - 2 * K, 2 * detail, detail, ironD)
                c.fillRect(rx.toInt(), ry.toInt() - 2 * K, 2 * detail, 1, ironM)
            } else {
                c.set(rx.toInt(), ry.toInt() - 2 * K, ironD); c.set(rx.toInt() + 1, ry.toInt() - 2 * K, ironD)
                for (a in 0 until 16) {
                    val ang = a / 16f * 2f * PI.toFloat()
                    c.set((rx + cos(ang) * 1.5f * K).toInt(), (ry + sin(ang) * 1.5f * K).toInt(), if (ang > PI.toFloat()) ironHi else ironL)
                }
            }
        }
    }

    /** A horseshoe lost in the dirt of the yard, its toe toward the room; closer up, its nail holes. */
    private fun lostShoe() {
        val hx = 9.75f; val hy = 9.5f
        quadPlot(hx - 0.42f, hy - 0.42f, 0f, hx + 0.42f, hy - 0.42f, 0f, hx + 0.42f, hy + 0.42f, 0f, hx - 0.42f, hy + 0.42f, 0f) { px, py ->
            val u = (flatX(px, py, 0f) - hx) / 0.36f; val v = (flatY(px, py, 0f) - hy) / 0.36f
            val on = if (u <= 0f) hyp(u, v) in 0.5f..1f else abs(v) in 0.5f..1f && u < 0.9f
            when {
                !on -> 0
                fine && lostHole(u, v) -> Col.hex(0x3E3430)
                fine && (if (u <= 0f) hyp(u, v) > 0.9f else abs(v) > 0.9f) && u + v < 0f -> Col.hex(0xB0A298)
                u < -0.3f || v < -0.6f -> Col.hex(0x9A8A80)
                else -> Col.hex(0x6E5E56)
            }
        }
    }

    /** The lost horseshoe's nail holes: along the middle of its arms and round its toe. */
    private fun lostHole(u: Float, v: Float): Boolean =
        if (u <= 0f) hyp(u, v) in 0.66f..0.84f && abs(abs(atan2(v, -u)) - 0.95f) < 0.2f
        else abs(abs(v) - 0.75f) < 0.09f && abs(((u + 0.1f) / 0.35f) % 1f - 0.5f) < 0.16f && u < 0.75f

    /** The grindstone in the yard: a round stone on its axle, sunk in a wooden trough on legs, the crank at its front. */
    private fun grindstone() {
        val gx = 5.4f; val gy = 10.2f; val tz = 4.2f
        for ((lx, ly) in listOf(gx - 0.55f to gy + 0.22f, gx + 0.55f to gy + 0.22f, gx + 0.55f to gy - 0.22f)) iso.post(lx, ly, 0f, tz - 1.2f, Pal.WOOD_D)
        iso.box(gx - 0.7f, gy - 0.3f, tz - 1.2f, 1.4f, 0.6f, 1.2f, if (winter) Pal.SNOW_L else Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        iso.post(gx, gy - 0.36f, tz, tz + 2.4f, Pal.WOOD_D)
        // the stone, standing in the plane y = GY, its lower part in the trough
        part()
        val r = 3.4f; val rx = r / 4.5f; val zc = tz + 2f
        quadPlot(gx - rx, gy, tz, gx + rx, gy, tz, gx + rx, gy, zc + r, gx - rx, gy, zc + r) { px, py ->
            val u = (wallYx(px, gy) - gx) / rx; val z = wallYz(px, py, gy); val v = (z - zc) / r
            val d = hyp(u, v)
            when {
                d > 1f || z < tz -> 0
                fine && d < 0.1f -> ironD
                fine && d < 0.2f -> if (u + v > 0.05f) ironL else ironM
                d < 0.2f -> ironM
                d > 0.84f -> if (u + v < 0f) Pal.STONE_D else Pal.STONE_L
                fine && d > 0.8f -> Col.mix(Pal.STONE_L, Pal.STONE_M, 0.2f)
                Noise.rnd(px, py, 97) < 0.12f -> Pal.STONE_M
                fine && (d * 10f) % 1f < 0.12f -> Col.mix(Pal.STONE_L, Pal.STONE_M, 0.7f) // the rings the grinding wears
                u - v > 0.6f -> Pal.STONE_M
                else -> Col.mix(Pal.STONE_L, Pal.STONE_M, 0.4f)
            }
        }
        // the axle's front bearing and the crank
        part()
        iso.post(gx, gy + 0.36f, tz, zc, Pal.WOOD_M)
        if (fine) {
            rodIso(gx, gy + 0.36f, zc, gx, gy + 0.6f, zc, ironL, ironHi, ironM)
            rodIso(gx, gy + 0.6f, zc, gx, gy + 0.6f, zc - 2.2f, ironL, ironHi, ironM)
        } else {
            iso.line(gx, gy + 0.36f, zc, gx, gy + 0.6f, zc, ironL)
            iso.line(gx, gy + 0.6f, zc, gx, gy + 0.6f, zc - 2.2f, ironL)
        }
        iso.px(gx, gy + 0.7f, zc - 2.4f, Pal.WOOD_M)
        if (fine) c.fillRect(iso.ix(gx, gy + 0.7f), iso.iy(gx, gy + 0.7f, zc - 2.4f), K, 1, Pal.WOOD_L)
    }

    /** Split logs stacked in the yard, their cut ends toward us with the rings showing; snow on them in winter. */
    private fun woodpile() {
        val x = 1.75f; val y = 9.55f; val len = 1.1f
        for (row in 0 until 3) for (i in 0 until 4 - row) {
            prop {
                val lx = x + i * 0.42f + row * 0.21f
                val lz = row * 2.9f
                iso.box(lx, y, lz, 0.42f, len, 2.9f, if (winter) Pal.SNOW_L else Pal.LOG_L, Pal.LOG_M, Pal.LOG_D)
                if (!winter) iso.line(lx + 0.2f, y + 0.1f, lz + 2.9f, lx + 0.2f, y + len, lz + 2.9f, Pal.LOG_D)
                val ex = iso.sx(lx + 0.21f, y + len); val ey = iso.sy(lx + 0.21f, y + len, lz + 1.45f)
                if (fine) logEnd(ex, ey, 1.6f * K, 1.45f * K, row * 4 + i) else {
                    c.fillEllipse(ex, ey, 1.6f * K, 1.45f * K, Col.hex(0xE0BC86))
                    c.fillEllipse(ex, ey, 0.8f * K, 0.7f * K, Col.hex(0xC89A62))
                    c.set(ex.toInt(), ey.toInt(), Col.hex(0x8A6440))
                }
            }
        }
    }

    /** A log's cut end closer up: a darker rim, growth rings round an off-centre heart, a check split out from it. */
    private fun logEnd(cx: Float, cy: Float, rx: Float, ry: Float, seed: Int) {
        val hx = cx + (Noise.rnd(seed, 30) - 0.5f) * rx * 0.3f; val hy = cy + (Noise.rnd(seed, 31) - 0.5f) * ry * 0.3f
        val ca = Noise.rnd(seed, 32) * 6.283f; val cc = cos(ca); val cs = sin(ca)
        for (py in max(c.top, floor(cy - ry).toInt())..min(c.bottom - 1, ceil(cy + ry).toInt())) for (px in max(c.left, floor(cx - rx).toInt())..min(c.right - 1, ceil(cx + rx).toInt())) {
            val ex = (px + 0.5f - cx) / rx; val ey = (py + 0.5f - cy) / ry
            val e = ex * ex + ey * ey
            if (e > 1f) continue
            val hdx = px + 0.5f - hx; val hdy = py + 0.5f - hy
            val rr = hyp(hdx / rx, hdy / ry)
            val along = hdx * cc + hdy * cs; val across = abs(hdy * cc - hdx * cs)
            c.set(px, py, when {
                e > 0.8f -> Col.hex(0xC49A66)
                rr < 0.09f -> Col.hex(0x8A6440)
                along > 0f && along < rx * 0.62f && across < 0.45f + along * 0.06f -> Col.hex(0x8A6440)
                (rr * 4.5f) % 1f < 0.2f -> if (rr < 0.45f) Col.hex(0xB08050) else Col.hex(0xC8A070)
                rr < 0.45f -> Col.hex(0xC89A62)
                else -> Col.hex(0xE0BC86)
            })
        }
    }

    // ------------------------------------------------------------------ the room

    /** The flagstone floor a hand above the yard; its cut edges show the slabs' ends over the footing. */
    private fun floor() {
        floorId = s.newObject(Pal.OUTLINE)
        quadFill(x0 - thick, y1, 0f, x1, y1, 0f, x1, y1, fl, x0 - thick, y1, fl) { px, py -> footing(wallYz(px, py, y1), px, py, 1f) }
        quadFill(x1, y0 - thick, 0f, x1, y1, 0f, x1, y1, fl, x1, y0 - thick, fl) { px, py -> footing(wallXz(px, py, x1), px, py, 0.8f) }
        quadFill(x0, y0, fl, x1, y0, fl, x1, y1, fl, x0, y1, fl) { px, py -> slab(flatX(px, py, fl), flatY(px, py, fl), px, py) }
        c.penId = 0
        // contact shadows: along the forge, under the wheel, the stump, the crate and the bucket
        shade(iso.sx(fx1 + 0.1f, fy1 + 0.1f), iso.sy(fx1 + 0.1f, fy1 + 0.1f, fl), 12f * K, 2.4f * K, 0.7f, floorId)
        shade(iso.sx(x0 + 0.4f, 7.3f), iso.sy(x0 + 0.4f, 7.3f, fl), 7f * K, 1.8f * K, 0.72f, floorId)
        shade(iso.sx(ax + 0.2f, ay + 0.2f), iso.sy(ax + 0.2f, ay + 0.2f, fl), 6.8f * K, 2.8f * K, 0.7f, floorId)
        shade(iso.sx(7.35f, 4.1f), iso.sy(7.35f, 4.1f, fl), 5f * K, 2f * K, 0.75f, floorId)
        shade(iso.sx(7.4f, 7.2f), iso.sy(7.4f, 7.2f, fl), 4f * K, 1.6f * K, 0.72f, floorId)
        shade(iso.sx(4.9f, 2.0f), iso.sy(4.9f, 2.0f, fl), 9f * K, 2f * K, 0.78f, floorId)
    }

    private fun footing(z: Float, px: Int, py: Int, light: Float): Int {
        if (fine) return Col.scale(footingFine(z, px, py), light)
        val col = when {
            z > fl - 0.5f -> slabL
            Noise.rnd(px / 5, (py + 1) / 3, 77) < 0.5f -> stoneD
            else -> stoneM
        }
        return Col.scale(col, light)
    }

    /** The floor's cut edge closer up: the slabs' lit lip, rubble in the footing under it, staggered, with dark joints. */
    private fun footingFine(z: Float, px: Int, py: Int): Int {
        val n = detail
        val e = 1f / K
        if (z > fl - 0.5f) return if (z > fl - e) stoneHi else if (z < fl - 0.5f + e) Col.scale(slabL, 0.78f) else slabL
        val by = (py + n) / (3 * n)
        val sx = px + (by and 1) * 2 * n
        val bx = sx / (5 * n)
        val lx = sx - bx * 5 * n; val ly = py + n - by * 3 * n
        val base = if (Noise.rnd(bx, by, 77) < 0.5f) stoneD else stoneM
        return when {
            ly == 0 || lx == 0 -> mortar
            ly == 1 -> Col.mix(base, stoneHi, 0.3f)
            ly == 3 * n - 1 || lx == 5 * n - 1 -> Col.scale(base, 0.82f)
            else -> base
        }
    }

    /**
     * A flagstone at world ([x], [y]): rows of uneven slabs with dark joints, soot round the forge, ash and scale
     * scattered by the forge and the anvil.
     */
    private fun slab(x: Float, y: Float, px: Int, py: Int): Int {
        val rw = 1.15f
        val row = floor(y / rw).toInt(); val fy = y - row * rw
        val len = 1.0f + Noise.rnd(row, 71) * 0.8f
        val u = x / len + Noise.rnd(row, 72) * 3f
        val b = floor(u).toInt(); val fx = (u - b) * len
        var col = if (fy < 0.12f || fx < 0.12f) joint else if (fine) slabFine(x, y, fx, fy, len, rw, b, row, px, py) else {
            val tone = Noise.rnd(b, row, 73)
            var base = if (tone < 0.3f) slabL else if (tone > 0.72f) slabD else slabM
            if (fy < 0.24f || fx < 0.22f) base = Col.mix(base, stoneHi, 0.25f)
            val n = Noise.rnd(px, py, 74)
            if (n < 0.06f) Col.scale(base, 0.86f) else if (n > 0.98f) Col.mix(base, stoneHi, 0.5f) else base
        }
        val soot = if (abs(x - potX) < 3.3f && abs(y - potY) < 3.3f) (1f - hyp(x - potX, y - potY) / 3.3f).coerceIn(0f, 1f) else 0f
        if (soot > 0f && soot + (Dither.at(px, py) - 0.5f) * 0.35f > 0.2f) col = Col.mix(col, sootC, min(0.8f, soot * 0.9f))
        val ash = max(soot, if (abs(x - ax) < 1.7f && abs(y - ay) < 1.7f) (1f - hyp(x - ax, y - ay) / 1.7f).coerceIn(0f, 1f) * 0.8f else 0f)
        if (ash > 0f && Noise.rnd(px, py, 75) < ash * 0.13f) col = if (Noise.rnd(px, py, 76) < 0.5f) Col.hex(0x8E8A84) else Col.hex(0x282422)
        return col
    }

    /** A flagstone's face closer up: lit along its near edges, a shadow along its far ones, worn patches, a crack here and there. */
    private fun slabFine(x: Float, y: Float, fx: Float, fy: Float, len: Float, rw: Float, b: Int, row: Int, px: Int, py: Int): Int {
        val tone = Noise.rnd(b, row, 73)
        var base = if (tone < 0.3f) slabL else if (tone > 0.72f) slabD else slabM
        val e = 1f / (2f * K) // a picture px, in cells
        if (fy < 0.12f + e * 1.6f || fx < 0.12f + e * 1.6f) base = Col.mix(base, stoneHi, 0.3f)
        else if (fy > rw - e * 1.2f || fx > len - e * 1.2f) base = Col.scale(base, 0.8f)
        else {
            val wear = Noise.v2(x * 2.6f, y * 2.6f, 78)
            if (wear > 0.66f) base = Col.mix(base, stoneHi, 0.14f) else if (wear < 0.24f) base = Col.scale(base, 0.93f)
            val vy = fy / rw
            if (vy > 0.18f && vy < 0.82f && Noise.rnd(b, row, 79) < 0.3f) {
                // a hairline crack partway across the slab, wandering
                val at = 0.25f + Noise.rnd(b, row, 80) * 0.5f
                val slope = (Noise.rnd(b, row, 82) - 0.5f) * 1.4f
                val wob = (Noise.v1(fy * 6f + b * 3f, 81) - 0.5f) * 0.16f
                if (abs(fx / len - at - (vy - 0.5f) * slope + wob) < e * 0.7f / len) base = Col.scale(base, 0.72f)
            }
        }
        val n = Noise.rnd(px, py, 74)
        return if (n < 0.06f) Col.scale(base, 0.86f) else if (n > 0.98f) Col.mix(base, stoneHi, 0.5f) else base
    }

    private fun footShadow(x: Float, y: Float) =
        shade(iso.sx(x, y), iso.sy(x, y, fl), 5f * K, 1.6f * K, 0.7f, floorId)

    /** How sooty the walls are: black in the forge's corner above the hearth, greying toward the top everywhere. */
    private fun sootAt(wx: Float, wy: Float, z: Float): Float {
        val near = (1f - hyp(wx - x0, wy - y0) / 4.4f).coerceIn(0f, 1f)
        val up = ((z - ft) / 10f).coerceIn(0f, 1f)
        return max(near * (0.2f + up * 0.9f), ((z - 15f) / 10f).coerceIn(0f, 1f) * 0.45f)
    }

    /**
     * Rough stone at [a] cells along a wall and [z] px up: courses of uneven blocks with ragged mortar, each stone
     * lit along its top; [soot] darkens it, [light] dims the whole face.
     */
    private fun stoneAt(a: Float, z: Float, px: Int, py: Int, soot: Float, light: Float): Int {
        if (fine) return stoneFine(a, z, px, py, soot, light)
        val ch = 3f
        val zz = z + (Noise.v2(a * 4.1f, z * 0.35f, 3) - 0.5f) * 1.1f
        val course = floor(zz / ch).toInt()
        val fz = zz - course * ch
        val bl = 0.9f + Noise.rnd(course, 41) * 0.7f
        val u = a / bl + Noise.rnd(course, 42) * 5f
        val b = floor(u).toInt()
        val fa = (u - b) * bl
        val tone = Noise.rnd(b, course, 43)
        var col = when {
            fz < 0.55f || fa < 0.13f -> mortar
            fz > ch - 0.55f -> if (tone < 0.5f) stoneHi else stoneL
            fz < 1.05f && Dither.at(px, py) < 0.5f -> stoneD
            tone < 0.28f -> stoneL
            tone > 0.74f -> stoneD
            else -> stoneM
        }
        if (col != mortar) {
            val n = Noise.rnd(px, py, 44)
            if (n < 0.07f) col = Col.scale(col, 0.84f) else if (n > 0.975f) col = stoneHi
        }
        if (soot > 0f) {
            val f = soot + (Dither.at(px, py) - 0.5f) * 0.25f
            if (f > 0.12f) col = Col.mix(col, sootC, min(0.82f, f))
        }
        return if (light == 1f) col else Col.scale(col, light)
    }

    /**
     * [stoneAt] closer up: thinner joints that wander, the mortar shadowed under each stone; each stone bevelled
     * (a bright top edge, a lit left one, its bottom and right in shadow), its face mottled and pitted.
     */
    private fun stoneFine(a: Float, z: Float, px: Int, py: Int, soot: Float, light: Float): Int {
        val ch = 3f
        val k = K.toFloat()
        val zz = z + (Noise.v2(a * 4.1f, z * 0.35f, 3) - 0.5f) * 1.1f
        val course = floor(zz / ch).toInt()
        val fz = zz - course * ch
        val bl = 0.9f + Noise.rnd(course, 41) * 0.7f
        val u = a / bl + Noise.rnd(course, 42) * 5f
        val b = floor(u).toInt()
        val fa = (u - b) * bl
        val tone = Noise.rnd(b, course, 43)
        // the edges' distances in picture px: to the joint under the stone, its top, the joint at its left, its right end
        val wob = (Noise.v1(a * 9f + course * 5.3f, 47) - 0.5f) * 1.4f
        val eb = fz * k + wob; val et = (ch - fz) * k; val el = fa * 4f * k - wob; val er = (bl - fa) * 4f * k
        val jz = 0.36f * k; val ja = 0.36f * k
        var col = when {
            eb < jz || el < ja -> if (eb >= jz - 1.2f && el >= ja) Col.mix(mortar, sootC, 0.55f) else if (Noise.rnd(px, py, 49) < 0.16f) Col.mix(mortar, stoneD, 0.4f) else mortar
            et < 1f -> stoneHi
            et < 0.55f * k -> if (tone < 0.5f) stoneHi else stoneL
            el < ja + 1f -> Col.mix(stoneL, stoneHi, 0.3f)
            eb < jz + 1f || er < 1f -> Col.scale(stoneD, 0.88f)
            eb < jz + 0.5f * k && Dither.at(px, py) < 0.5f -> stoneD
            tone < 0.28f -> stoneL
            tone > 0.74f -> stoneD
            else -> stoneM
        }
        if (et >= 0.55f * k && eb >= jz + 1f && el >= ja + 1f && er >= 1f) {
            // the face bulges a little: its upper left toward the light, its lower right away
            val m = fz / ch - fa / bl * 0.4f + (tone - 0.5f) * 0.2f
            if (m > 0.6f) col = Col.mix(col, stoneHi, 0.2f) else if (m < 0.12f) col = Col.mix(col, stoneD, 0.28f)
            val n = Noise.rnd(px, py, 44)
            if (n < 0.05f) col = Col.scale(col, 0.8f) else if (n > 0.982f) col = stoneHi
        }
        if (soot > 0f) {
            val f = soot + (Dither.at(px, py) - 0.5f) * 0.25f
            if (f > 0.12f) col = Col.mix(col, sootC, min(0.82f, f))
        }
        return if (light == 1f) col else Col.scale(col, light)
    }

    /** The hood's +y slope at height [z] (from the skirt up to the chimney), for what it hides of the left wall. */
    private fun hoodY(z: Float): Float = if (z <= hz0) hy1 else if (z >= hz1) cy1 else hy1 - (hy1 - cy1) * (z - hz0) / (hz1 - hz0)
    private fun hoodX(z: Float): Float = if (z <= hz0) hx1 else if (z >= hz1) cx1 else hx1 - (hx1 - cx1) * (z - hz0) / (hz1 - hz0)

    /** Whether the left wall's stone at ([y], [z]) is hidden behind the forge, the hood or the window, drawn later. */
    private fun hiddenLeft(y: Float, z: Float): Boolean =
        (y < fy1 - 0.15f && z < ft + 0.4f) || (z > hz0 - sk + 0.6f && y < hoodY(z) - 0.15f) ||
            (y > wa + 0.15f && y < wb - 0.15f && z > za + 0.6f && z < zb - 0.6f)

    /** Whether the right wall's stone at ([x], [z]) is hidden behind the forge, the hood or the door, drawn later. */
    private fun hiddenRight(x: Float, z: Float): Boolean =
        (x < fx1 - 0.15f && z < ft + 0.4f) || (z > hz0 - sk + 0.6f && x < hoodX(z) - 0.15f) ||
            (x > da - doorFr + 0.15f && x < db + doorFr - 0.15f && z < doorTop - 0.6f)

    /**
     * The two back walls of rough stone, the timber plates along their tops, their cut ends. (In parts: the JVM
     * leaves a method this long uncompiled, and these loops run over every pixel of the walls.)
     */
    private fun walls() {
        part()
        stoneFaces()
        // the cut ends: rubble in section, a little paler
        part()
        cutEnds()
        // the timber plates on top
        part()
        plates()
        c.penId = 0
    }

    /** The walls' inner faces: the left one (x = X0) in the shade, the right one (y = Y0). */
    private fun stoneFaces() {
        quadFill(x0, y0, fl, x0, y1, fl, x0, y1, wallH, x0, y0, wallH) { px, py ->
            val y = wallXy(px, x0); val z = wallXz(px, py, x0)
            if (hiddenLeft(y, z)) mortar else stoneAt(y, z, px, py, sootAt(x0, y, z), 0.84f)
        }
        quadFill(x0, y0, fl, x1, y0, fl, x1, y0, wallH, x0, y0, wallH) { px, py ->
            val x = wallYx(px, y0); val z = wallYz(px, py, y0)
            if (hiddenRight(x, z)) mortar else stoneAt(x + 17f, z, px, py, sootAt(x, y0, z), 1f)
        }
    }

    private fun cutEnds() {
        quadFill(x0 - thick, y1, 0f, x0, y1, 0f, x0, y1, wallH, x0 - thick, y1, wallH) { px, py ->
            Col.mix(stoneAt(wallYx(px, y1) * 2.4f + 3f, wallYz(px, py, y1), px, py, 0f, 1f), stoneHi, 0.2f)
        }
        quadFill(x1, y0 - thick, 0f, x1, y0, 0f, x1, y0, wallH, x1, y0 - thick, wallH) { px, py ->
            Col.mix(stoneAt(wallXy(px, x1) * 2.4f + 9f, wallXz(px, py, x1), px, py, 0f, 0.8f), stoneL, 0.15f)
        }
    }

    /** The timber plates along the walls' tops: their sides, their cut ends, their tops. */
    private fun plates() {
        val xo = x0 - thick; val yo = y0 - thick
        quadFill(x0, y0, wallH, x0, y1, wallH, x0, y1, plateH, x0, y0, plateH) { px, py ->
            val y = wallXy(px, x0); val z = wallXz(px, py, x0)
            timber(y, z - wallH, px, py, 0.84f, sootAt(x0, y, z) * 0.7f)
        }
        quadFill(x0, y0, wallH, x1, y0, wallH, x1, y0, plateH, x0, y0, plateH) { px, py ->
            val x = wallYx(px, y0); val z = wallYz(px, py, y0)
            timber(x + 5f, z - wallH, px, py, 1f, sootAt(x, y0, z) * 0.7f)
        }
        quadFill(xo, y1, wallH, x0, y1, wallH, x0, y1, plateH, xo, y1, plateH) { px, py -> endGrain(wallYx(px, y1) - xo, wallYz(px, py, y1) - wallH, 1f) }
        quadFill(x1, yo, wallH, x1, y0, wallH, x1, y0, plateH, x1, yo, plateH) { px, py -> endGrain(wallXy(px, x1) - yo, wallXz(px, py, x1) - wallH, 0.8f) }
        quadFill(xo, yo, plateH, x0, yo, plateH, x0, y1, plateH, xo, y1, plateH) { px, py -> plateTop(flatY(px, py, plateH), flatX(px, py, plateH) - xo, px, py) }
        quadFill(x0, yo, plateH, x1, yo, plateH, x1, y0, plateH, x0, y0, plateH) { px, py -> plateTop(flatX(px, py, plateH) + 3f, flatY(px, py, plateH) - yo, px, py) }
    }

    /** A squared timber's side at [a] cells along it and [v] px up it (0..2): a lit top edge, a dark bottom, grain streaks. */
    private fun timber(a: Float, v: Float, px: Int, py: Int, light: Float, soot: Float = 0f): Int {
        var col = if (fine) timberFine(a, v) else when {
            v > 1.5f -> Pal.WOOD_M
            v < 0.5f -> Pal.WOOD_X
            Noise.v2(a * 1.4f, v * 1.2f, 81) > 0.64f -> Pal.WOOD_X
            else -> Pal.WOOD_D
        }
        if (Noise.rnd(px, py, 82) < 0.04f) col = Pal.WOOD_X
        if (soot > 0.1f) col = Col.mix(col, sootC, min(0.7f, soot))
        return if (light == 1f) col else Col.scale(col, light)
    }

    /** A timber's side closer up: a lit arris, the dark underside, grain lines wandering along it, a bolt now and then. */
    private fun timberFine(a: Float, v: Float): Int {
        val k = K.toFloat()
        val bpx = ((a / 2.3f + 0.35f) % 1f - 0.5f) * 2.3f * 4f * k; val bpz = (v - 1f) * k
        val br = 0.36f * k
        if (bpx * bpx + bpz * bpz < br * br) return if (bpz > br * 0.2f) ironL else if (bpz < -br * 0.4f) ironD else iron
        return when {
            v > 2f - 1f / k -> Pal.WOOD_L
            v > 1.5f -> Pal.WOOD_M
            v < 0.5f -> Pal.WOOD_X
            Noise.v2(a * 1.4f, v * 1.2f, 81) > 0.64f -> Pal.WOOD_X
            abs((v * 2.2f + Noise.v1(a * 2.5f, 84) * 1.6f) % 1f - 0.5f) < 0.08f -> Col.mix(Pal.WOOD_D, Pal.WOOD_X, 0.6f)
            else -> Pal.WOOD_D
        }
    }

    /** The top of a plate at [a] cells along it and [across] cells over it: planed wood, snow in winter. */
    private fun plateTop(a: Float, across: Float, px: Int, py: Int): Int {
        if (winter) return if (across < 0.1f || Dither.at(px, py) < 0.8f) Pal.SNOW_L else Pal.SNOW_M
        return when {
            across > thick - 0.1f -> Pal.WOOD_L
            Noise.v2(a * 1.6f, across * 3f, 83) > 0.66f -> Pal.WOOD_D
            fine && abs((across * 9f + Noise.v1(a * 3f, 85) * 1.5f) % 1f - 0.5f) < 0.07f -> Col.mix(Pal.WOOD_M, Pal.WOOD_D, 0.5f)
            else -> Pal.WOOD_M
        }
    }

    /** A timber's cut end: rings round its heart. */
    private fun endGrain(u: Float, v: Float, light: Float): Int {
        val r = hyp((u - thick / 2f) * 4.5f, v - 1f)
        val col = when {
            (r * (if (fine) 2.6f else 1.6f)) % 1f < (if (fine) 0.2f else 0.28f) -> Col.hex(0xA0764A)
            else -> Col.hex(0xC89A62)
        }
        return Col.scale(col, light)
    }

    // ------------------------------------------------------------------ on the walls

    /** The sky at [tt] (0 = the top, 1 = the horizon) of this hour. */
    private fun skyCol(tt: Float): Int =
        if (tt < 0.55f) Col.mix(env.skyTop, env.skyMid, tt / 0.55f) else Col.mix(env.skyMid, env.skyHorizon, (tt - 0.55f) / 0.45f)

    /** A star in the window closer up: as many as on the scene's canvas, each a single bright point. */
    private fun starAt(px: Int, py: Int): Boolean {
        val n = detail
        return Math.floorMod(px, n) == n / 2 && Math.floorMod(py, n) == n / 2 &&
            Noise.rnd(Math.floorDiv(px, n), Math.floorDiv(py, n), 16) < 0.04f * env.dark
    }

    /**
     * The small window deep in the left wall: the sky and a green hill through it (stars at night), a wooden
     * cross, the sill and the jamb showing how thick the wall is; snow on the sill in winter.
     */
    private fun window() {
        val xb = x0 - thick
        // what the window shows: sky and a hill in the outer plane of the wall
        glow {
            quadFill(x0, wa, za, x0, wb, za, x0, wb, zb, x0, wa, zb) { px, py ->
                val y = wallXy(px, xb); val z = wallXz(px, py, xb)
                val hill = za + 2.6f + Noise.v1(y * 3f, 15) * 2.2f
                when {
                    z < hill -> env.lit(if (winter) Pal.SNOW_M else if (z < hill - 1.2f) Col.hex(0x4E8A40) else Col.hex(0x6AA84E))
                    env.dark > 0.3f && (if (fine) starAt(px, py) else Noise.rnd(px, py, 16) < 0.04f * env.dark) -> Col.hex(0xFFF6D8)
                    else -> skyCol(((zb - z) / (zb - za) * 0.9f + Dither.at(px, py) * 0.1f).coerceIn(0f, 1f))
                }
            }
        }
        // the frame's cross in the outer plane, then the sill and the jamb in front of it
        val e = 1f / K
        quadPlot(x0, wa, za, x0, wb, za, x0, wb, zb, x0, wa, zb) { px, py ->
            val y = wallXy(px, x0); val z = wallXz(px, py, x0)
            val ts = (z - za) / 4f; val tj = y - wa
            when {
                ts < thick && ts <= tj -> if (winter) Pal.SNOW_L else if (ts < 0.12f) stoneHi else stoneL
                tj < thick -> if (z - 4f * tj < za + 0.5f) stoneM else Col.mix(stoneM, stoneL, 0.4f)
                else -> {
                    val yy = y - thick; val zz = z - 4f * thick
                    val mid = (wa + wb) / 2f; val midZ = (za + zb) / 2f + 0.6f
                    when {
                        fine && abs(yy - mid) < 0.07f && yy < mid - 0.07f + e / 4f -> Pal.WOOD_M
                        fine && abs(zz - midZ) < 0.35f && zz > midZ + 0.35f - e -> Pal.WOOD_M
                        abs(yy - mid) < 0.07f || abs(zz - midZ) < 0.35f -> Pal.WOOD_D
                        yy > wb - 0.12f || zz > zb - 0.6f -> Pal.WOOD_X
                        else -> 0
                    }
                }
            }
        }
    }

    /** By day the window throws a pale patch of sun across the floor. */
    private fun sunPatch() {
        val a = (env.sun * 1.4f).coerceIn(0f, 1f) * 0.34f
        if (a <= 0.02f) return
        val id = floorId
        val warm = Col.hex(0xFFF0CC)
        val qx = floatArrayOf(x0 + 1.3f, x0 + 1.3f, x0 + 3.1f, x0 + 3.1f)
        val qy = floatArrayOf(wa + 0.55f, wb + 0.55f, wb + 1.25f, wa + 1.25f)
        s.fx {
            c.polyBegin()
            for (i in 0..3) c.polyAdd(iso.sx(qx[i], qy[i]), iso.sy(qx[i], qy[i], fl))
            c.polyScan { px, py -> if (c.inside(px, py) && c.ids[c.index(px, py)] == id && Dither.at(px, py) < 0.8f) c.blend(px, py, warm, a) }
        }
        s.light(iso.sx(x0 + 1.2f, 5.6f), iso.sy(x0 + 1.2f, 5.6f, fl + 6f), 26f, 0.45f * env.sun.coerceIn(0f, 1f))
    }

    /**
     * The plank door in the right wall, in a frame of dressed stones: boards, three iron straps with rivets from
     * the hinges, the ring; a line of daylight under it by day.
     */
    private fun door(lit: Boolean) {
        val yd = y0 + 0.03f
        val fr = doorFr; val top = doorTop
        quadFill(da - fr, yd, fl, db + fr, yd, fl, db + fr, yd, top, da - fr, yd, top) { px, py ->
            val x = wallYx(px, yd); val z = wallYz(px, py, yd)
            val col = if (z > dz) {
                // the lintel: three blocks
                val k = floor((x - da + fr) / ((db - da + 2 * fr) / 3f)).toInt()
                val fk = (x - da + fr) - k * ((db - da + 2 * fr) / 3f)
                when {
                    fk < 0.13f || z < dz + 0.5f -> mortar
                    z > top - 0.6f -> stoneHi
                    else -> if (k == 1) stoneL else Col.mix(stoneL, stoneM, 0.5f)
                }
            } else {
                // the jambs: blocks in courses, paler and darker by turns
                val course = floor((z - fl) / 3.2f).toInt()
                val fz = (z - fl) - course * 3.2f
                when {
                    fz < 0.5f -> mortar
                    fz > 2.6f -> stoneHi
                    course % 2 == 0 -> stoneL
                    else -> Col.mix(stoneL, stoneM, 0.5f)
                }
            }
            if (fine && col != mortar && Noise.rnd(px, py, 93) < 0.08f) Col.scale(col, 0.88f) else col
        }
        part()
        quadFill(da, yd, fl, db, yd, fl, db, yd, dz, da, yd, dz) { px, py ->
            val x = wallYx(px, yd); val z = wallYz(px, py, yd)
            val bu = (x - da) / 0.46f; val board = floor(bu).toInt(); val fb = (bu - board) * 0.46f
            val tone = Noise.rnd(board, 91)
            var col = if (fb < 0.12f) Pal.WOOD_X else if (tone < 0.33f) Col.hex(0x7A4E2C) else if (tone < 0.66f) Col.hex(0x6A4226) else Col.hex(0x5E3A20)
            if (fb >= 0.12f && Noise.v2(board * 5f, z * 0.5f, 92) > 0.7f) col = Col.scale(col, 0.84f)
            if (fine && fb >= 0.12f) col = boardGrain(col, fb, z, board)
            // the straps from the hinges on the right, riveted
            for (sz in 0..2) {
                val z0 = fl + 2.6f + sz * 6.4f
                if (z in z0..z0 + 1.3f && x > da + 0.2f) {
                    if (fine) col = strapFine(x, z, z0) else {
                        col = if (z > z0 + 0.8f) ironL else iron
                        if (abs(((x - da) / 0.3f) % 1f - 0.5f) < 0.2f && z in z0 + 0.4f..z0 + 0.9f) col = ironHi
                    }
                }
                if (x < da + 0.34f && z in z0 - 0.6f..z0 + 1.9f && x > da + 0.18f) col = iron // the curled end
            }
            if (z < fl + 0.5f && !lit) col = Col.hex(0xFFF0C0)
            col
        }
        // the ring
        val rx = iso.sx(da + 0.36f, yd); val ry = iso.sy(da + 0.36f, yd, fl + 9.5f)
        if (fine) {
            val n = detail
            ringFine(rx, ry + 2f * K, 1.5f * K, 1.7f * K, ironL, iron)
            c.fillRect(rx.toInt() - n, ry.toInt() - n, 3 * n, 3 * n, iron)
            c.fillRect(rx.toInt() - n, ry.toInt() - n, 3 * n, 1, ironL)
            stud(rx.toInt(), ry.toInt(), ironM, ironHi, ironD)
        } else {
            for (a in 0 until 16) {
                val ang = a / 16f * 2f * PI.toFloat()
                c.set((rx + cos(ang) * 1.5f * K).toInt(), (ry + 2f * K + sin(ang) * 1.7f * K).toInt(), if (ang < PI.toFloat()) iron else ironL)
            }
            c.fillRect(rx.toInt() - 1, ry.toInt() - 1, 3, 3, iron)
        }
    }

    /** Grain along a door board closer up: fine dark streaks that wander, the board's left edge lit. */
    private fun boardGrain(col: Int, fb: Float, z: Float, board: Int): Int {
        if ((fb - 0.12f) * 4f * K < 1f) return Col.mix(col, Pal.WOOD_M, 0.35f)
        val g = fb / 0.46f * 2.5f + (Noise.v1(z * 0.3f + board * 13f, 99) - 0.5f) * 1.1f
        return if (g > 0f && abs(g % 1f - 0.5f) < 0.08f) Col.scale(col, 0.78f) else col
    }

    /** A strap of the door closer up: its top edge lit, its lower edge dark, round rivets lit from above. */
    private fun strapFine(x: Float, z: Float, z0: Float): Int {
        val k = K.toFloat()
        val rx = (((x - da) / 0.3f) % 1f - 0.5f) * 0.3f * 4f * k; val rz = (z - z0 - 0.65f) * k
        val rr = 0.28f * k
        return when {
            rx * rx + rz * rz < rr * rr -> if (rz > rr * 0.25f) steel else if (rz < -rr * 0.4f) ironM else ironHi
            z > z0 + 1.3f - 1f / k -> ironHi
            z > z0 + 0.8f -> ironL
            z < z0 + 1f / k -> ironD
            else -> iron
        }
    }

    /** Horseshoes on their nails on the right wall over the bellows, the open end down. */
    private fun horseshoes() {
        val yw = y0 + 0.03f
        for ((k, hx) in floatArrayOf(4.45f, 5.1f, 5.75f).withIndex()) {
            val hz = fl + 15.6f + (k % 2) * 1.2f
            val rx = 0.31f; val rz = 2.2f
            iso.px(hx, yw, hz + rz + 0.4f, ironD)
            if (fine) c.fillRect(iso.ix(hx, yw), iso.iy(hx, yw, hz + rz + 0.4f), K - 1, 1, ironL)
            quadPlot(hx - rx, yw, hz - rz - 0.4f, hx + rx, yw, hz - rz - 0.4f, hx + rx, yw, hz + rz + 0.2f, hx - rx, yw, hz + rz + 0.2f) { px, py ->
                val u = (wallYx(px, yw) - hx) / rx; val v = (wallYz(px, py, yw) - hz) / rz
                val on = if (v >= 0f) hyp(u, v) in 0.52f..1f else v > -1f && (abs(u) + v * 0.12f) in 0.52f..1f
                when {
                    !on -> 0
                    fine && shoeHole(u, v) -> ironD
                    u < -0.2f && v > 0.2f -> ironHi
                    v < -0.5f -> iron
                    else -> ironL
                }
            }
        }
    }

    /** A hanging horseshoe's nail holes closer up: along the middle of its arms, and one each side of its toe. */
    private fun shoeHole(u: Float, v: Float): Boolean =
        if (v >= 0f) hyp(u, v) in 0.66f..0.86f && abs(atan2(abs(u), v) - 1.05f) < 0.22f
        else v in -0.9f..-0.1f && (abs(u) + v * 0.12f) in 0.66f..0.86f && abs(((v + 0.9f) / 0.4f) % 1f - 0.5f) < 0.17f

    /** The axe on two pegs on the left wall over the wheel, the handle along the wall, the blade down. */
    private fun axe() {
        val xw = x0 + 0.03f
        val hz = fl + 18f
        val ya = 6.35f; val yb = 8.2f
        val e = 1f / K
        // the handle
        quadFill(xw, ya + 0.3f, hz, xw, yb, hz - 0.2f, xw, yb, hz + 0.9f, xw, ya + 0.3f, hz + 1.1f) { px, py ->
            val z = wallXz(px, py, xw); val y = wallXy(px, xw)
            val base = hz - 0.2f * (y - ya - 0.3f) / (yb - ya - 0.3f)
            when {
                fine && z < base + e -> Pal.WOOD_D
                fine && z > base + 0.65f && z < base + 0.65f + e -> Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.2f)
                z > base + 0.65f -> Pal.WOOD_L
                else -> Pal.WOOD_M
            }
        }
        iso.px(xw, 6.95f, hz - 0.6f, Pal.WOOD_X); iso.px(xw, 7.85f, hz - 0.65f, Pal.WOOD_X)
        // the head: the poll over the handle, the blade flaring down to its edge
        part()
        quadPlot(xw, ya - 0.3f, hz - 4.2f, xw, ya + 0.85f, hz - 4.2f, xw, ya + 0.85f, hz + 2f, xw, ya - 0.3f, hz + 2f) { px, py ->
            val y = wallXy(px, xw); val z = wallXz(px, py, xw)
            val f = ((hz - z) / 3.8f).coerceIn(0f, 1f)          // 0 at the handle, 1 at the edge
            val lo = ya - f * 0.2f; val hi = ya + 0.42f + f * 0.36f
            when {
                z > hz + 1.6f || z < hz - 3.8f -> 0
                z >= hz - 0.2f -> if (y in ya..ya + 0.42f) (if (z > hz + 1.1f) ironHi else ironL) else 0
                y !in lo..hi -> 0
                fine && f > 0.93f -> Col.hex(0xE4E2EA)
                f > 0.8f -> steel
                y > hi - 0.13f -> ironM
                fine && y < lo + e / 4f -> ironHi
                else -> ironL
            }
        }
    }

    // ------------------------------------------------------------------ the forge

    /** Brick in courses at [a] cells along and [z] px up, the joints staggered; [light] dims the face. */
    private fun brickAt(a: Float, z: Float, light: Float, px: Int = 0, py: Int = 0): Int {
        val ch = 1.5f
        val course = floor(z / ch).toInt(); val fz = z - course * ch
        val u = a / 0.55f + (course and 1) * 0.5f
        val b = floor(u).toInt(); val fa = (u - b) * 0.55f
        val tone = Noise.rnd(b, course, 51)
        val col = if (fine) brickFine(fz, fa, tone, px, py) else when {
            fz < 0.5f || fa < 0.125f -> brickJ
            tone < 0.25f -> brickL
            tone > 0.8f -> brickD
            else -> brickM
        }
        return if (light == 1f) col else Col.scale(col, light)
    }

    /** A brick closer up: thinner joints, shadowed under each brick; the brick lit along its top and left, dark along its bottom and right, speckled. */
    private fun brickFine(fz: Float, fa: Float, tone: Float, px: Int, py: Int): Int {
        val k = K.toFloat()
        val eb = fz * k; val et = (1.5f - fz) * k; val el = fa * 4f * k; val er = (0.55f - fa) * 4f * k
        val jz = 0.36f * k; val ja = 0.36f * k
        if (eb < jz || el < ja) return if (eb >= jz - 1f && el >= ja) Col.mix(brickJ, sootC, 0.45f) else brickJ
        val base = if (tone < 0.25f) brickL else if (tone > 0.8f) brickD else brickM
        return when {
            et < 1f -> Col.mix(base, Col.hex(0xE8A07A), 0.3f)
            el < ja + 1f -> Col.mix(base, brickL, 0.35f)
            er < 1f || eb < jz + 1f -> Col.scale(base, 0.82f)
            else -> {
                val n = Noise.rnd(px, py, 52)
                if (n < 0.08f) Col.scale(base, 0.86f) else if (n > 0.965f) Col.mix(base, Col.hex(0xE8A07A), 0.3f) else base
            }
        }
    }

    /**
     * The brick forge in the back corner: brick sides blackened at the top, the ash pit's arch with embers in it,
     * a stone top round the iron fire pot, a heap of coal.
     */
    private fun forge() {
        quadFill(x0, fy1, fl, fx1, fy1, fl, fx1, fy1, ft, x0, fy1, ft) { px, py ->
            val x = wallYx(px, fy1); val z = wallYz(px, py, fy1)
            // the ash pit: an arched opening
            val pit = hyp((x - 2.35f) / 0.5f, (z - fl) / 3.2f)
            when {
                pit < 1f -> if (z < fl + 1.2f) (if (Noise.rnd(px, py, 57) < 0.4f) Col.hex(0x7A746E) else Col.hex(0x5A5450)) else Col.hex(0x161210)
                pit < 1.18f && z > fl + 0.5f -> if ((atan2(z - fl, (x - 2.35f) * 6f) * 4f).toInt() % 2 == 0) brickL else brickJ
                else -> Col.mix(brickAt(x, z, 1f, px, py), sootC, ((z - ft + 2.5f) / 3f).coerceIn(0f, 0.55f))
            }
        }
        quadFill(fx1, y0, fl, fx1, fy1, fl, fx1, fy1, ft, fx1, y0, ft) { px, py ->
            val y = wallXy(px, fx1); val z = wallXz(px, py, fx1)
            Col.mix(brickAt(y + 3f, z, 0.78f, px, py), sootC, ((z - ft + 2.5f) / 3f).coerceIn(0f, 0.55f))
        }
        // a few embers fallen into the ash
        glow {
            for (k in 0 until 3) {
                val ex = iso.ix(2.12f + k * 0.2f, fy1); val ey = iso.iy(2.12f + k * 0.2f, fy1, fl + 1f)
                if (sin(t * 2.3 + k * 1.9) > -0.3) {
                    val heat = if (k == 1) 3 else 4
                    if (fine) { c.fillRect(ex, ey, detail, detail, Pal.FLAME[heat]); c.set(ex + detail / 2, ey + detail / 2, Pal.FLAME[heat - 1]) }
                    else c.set(ex, ey, Pal.FLAME[heat])
                }
            }
        }
        // the stone top, a little proud, round the fire pot
        part()
        val tt = ft + 1f; val o = 0.08f
        quadFill(x0, fy1 + o, ft, fx1 + o, fy1 + o, ft, fx1 + o, fy1 + o, tt, x0, fy1 + o, tt) { px, py -> if (wallYz(px, py, fy1 + o) > tt - 0.5f) stoneHi else stoneM }
        quadFill(fx1 + o, y0, ft, fx1 + o, fy1 + o, ft, fx1 + o, fy1 + o, tt, fx1 + o, y0, tt) { px, py -> if (wallXz(px, py, fx1 + o) > tt - 0.5f) stoneL else stoneD }
        quadFill(x0, y0, tt, fx1 + o, y0, tt, fx1 + o, fy1 + o, tt, x0, fy1 + o, tt) { px, py ->
            val x = flatX(px, py, tt); val y = flatY(px, py, tt)
            val d = hyp(x - potX, y - potY)
            when {
                d < 0.62f -> ironD
                fine && d < 0.66f && x + y > potX + potY -> Col.hex(0x5A2A1A) // the pot's inner rim, lit by the fire
                d < 0.76f -> if (x + y < potX + potY) iron else ironL
                (x - x0) < 0.1f || (y - y0) < 0.1f -> sootC
                else -> {
                    val soot = (1.5f - d) / 1.2f + (Dither.at(px, py) - 0.5f) * 0.3f
                    val base = if (Noise.rnd(px, py, 58) < 0.06f) stoneM else stoneL
                    if (soot > 0.25f) Col.mix(base, sootC, min(0.75f, soot)) else base
                }
            }
        }
        // a heap of coal on the front left of the top, lumps catching a blue-grey light
        part()
        for (k in 0 until 18) {
            val a = Noise.rnd(k, 59) * 6.283f; val r = sqrt(Noise.rnd(k, 60)) * 0.42f
            val lx = x0 + 0.62f + cos(a) * r; val ly = fy1 - 0.55f + sin(a) * r
            val sx = iso.ix(lx, ly) - detail; val sy = iso.iy(lx, ly, tt) - ((0.42f - r) * 7f * detail).toInt() - detail
            if (fine) coalLump(sx, sy, k) else {
                c.fillRect(sx, sy, 3, 2, if (k % 3 == 0) Col.hex(0x2C2C36) else Col.hex(0x141218))
                c.set(sx + (k % 2), sy, Col.hex(0x6A6A7C))
            }
        }
    }

    /** A lump of coal closer up: its corners worn round, its top catching a blue-grey light, its underside black. */
    private fun coalLump(x: Int, y: Int, k: Int) {
        val n = detail
        val lw = 3 * n; val hgt = 2 * n
        val base = if (k % 3 == 0) Col.hex(0x2C2C36) else Col.hex(0x141218)
        c.fillRect(x + 1, y, lw - 2, 1, Col.mix(base, Col.hex(0x6A6A7C), 0.45f))
        c.fillRect(x, y + 1, lw, hgt - 2, base)
        c.fillRect(x + 1, y + hgt - 1, lw - 2, 1, Col.hex(0x0C0A0E))
        c.fillRect(x + (k % 2) * n + 1, y, n - 1, 1, Col.hex(0x8A8A9C))
        c.set(x + (k % 2) * n + 1, y + 1, Col.hex(0x6A6A7C))
    }

    /**
     * The fire in the pot: a heap of coals, hotter with each breath of the bellows, small flames licking up; sparks
     * fly with each breath and smoke goes out of the chimney. [banked] for the night: the coals under a film of ash, only
     * their cracks glowing low, no flames, no sparks, a thin thread of smoke.
     */
    private fun coals(br: Float, banked: Boolean = false) {
        val blow = 1f - br
        val damp = if (banked) BANKED else 0f
        val tt = ft + 1f
        val cx = iso.sx(potX, potY); val cy = iso.sy(potX, potY, tt)
        val rx = 0.64f * 11.3f * detail; val ry = 0.64f * 5.66f * detail
        glow {
            for (px in floor(cx - rx).toInt()..(cx + rx).toInt()) {
                val dx = (px + 0.5f - cx) / rx
                if (abs(dx) > 1f) continue
                val e = sqrt(1f - dx * dx) * ry
                val top = cy - e * 0.7f - (1f - dx * dx) * 3f * detail            // the heap rises in the middle
                for (py in floor(top).toInt()..floor(cy + e - 0.5f).toInt()) {
                    val dy = (py + 0.5f - cy) / ry
                    val d = (dx * dx + dy * dy).coerceAtMost(1f)
                    if (fine) { c.set(px, py, coalFine(px, py, d, blow, damp)); continue }
                    val lump = Noise.rnd(px shr 1, py shr 1, 13)
                    val heat = Noise.v2(px * 0.35f, (py * 0.35f) + t.toFloat() * 1.8f, 15) * 0.75f + (1f - d) * 0.35f + blow * 0.35f - lump * 0.25f - damp
                    val col = when {
                        heat > 0.95f -> Pal.FLAME[0]
                        heat > 0.75f -> Pal.FLAME[1]
                        heat > 0.55f -> Pal.FLAME[2]
                        heat > 0.38f -> Pal.FLAME[3]
                        heat > 0.24f -> Pal.FLAME[4]
                        banked && Noise.rnd(px, py, 14) < 0.45f -> ASH
                        else -> Col.hex(0x2A1A16)
                    }
                    c.set(px, py, col)
                }
            }
            if (banked) return@glow
            // flames licking up from the heap, taller as the bellows blow
            val base = cy - 1.5f * K
            val flick = sin(t * 11).toFloat() * 0.5f + sin(t * 7.3 + 2).toFloat() * 0.5f
            val hgt = (4.5f + blow * 4.5f + flick * 1.2f) * K
            for (tongue in 0..4) {
                val off = (tongue - 2) * 1.5f * K
                val th = hgt * (1f - abs(tongue - 2) * 0.24f) * (0.78f + 0.22f * sin(t * (9 + tongue) + tongue * 1.7).toFloat())
                val sway = sin(t * 6 + tongue).toFloat() * 0.7f * K
                c.fillEllipse(cx + off + sway * 0.5f, base - th * 0.42f, 1.6f * K, th * 0.5f, Pal.FLAME[3])
                c.fillEllipse(cx + off * 0.8f + sway * 0.6f, base - th * 0.34f, 1.1f * K, th * 0.36f, Pal.FLAME[2])
            }
            c.fillEllipse(cx, base - hgt * 0.22f, 1.6f * K, hgt * 0.26f, Pal.FLAME[1])
            c.fillEllipse(cx, base - hgt * 0.1f, 1.0f * K, hgt * 0.12f, Pal.FLAME[0])
        }
        // its glow in the dusk and the dark (banked, a small one)
        val gr = if (banked) 5f * K else 9f * K
        val ga = (if (banked) 0.035f else 0.07f + blow * 0.04f) * env.windows
        if (env.windows > 0f) s.fx {
            c.penEmissive = true
            if (fine) glowFine(cx, cy - 3f * K, gr, Pal.FLAME[2], ga)
            else c.ditherCircle(cx, cy - 3f * K, gr, Pal.FLAME[2], ga)
            c.penEmissive = false
        }
        if (!banked) {
            if (fine) { val ns = if (br < 0.35f) 10 else 3; s.fx { sparksFine(cx, cy - 3f * K, ns, 7) } }
            else s.sparks(cx, cy - 3f * K, if (br < 0.35f) 10 else 3, 7)
        }
        s.smoke(iso.sx(1.55f, 1.55f), iso.sy(1.55f, 1.55f, chimH + 1.5f), if (banked) 0.35f else 1.1f, 21)
    }

    /**
     * A coal of the fire closer up: lumps of their own shapes (each pixel belongs to the nearest of scattered
     * centres), the cracks between them glowing hotter than their faces, the heat stepping through the flame's
     * colours in a dithered gradient, a grey film of ash on the coolest; [damp] cools them all (banked: more ash).
     */
    private fun coalFine(px: Int, py: Int, d: Float, blow: Float, damp: Float = 0f): Int {
        val n = detail
        val gx = (px + 0.5f) / (1.9f * n); val gy = (py + 0.5f) / (1.2f * n)
        val ix = floor(gx).toInt(); val iy = floor(gy).toInt()
        var d1 = 9f; var d2 = 9f; var id = 0
        for (oy in -1..1) for (ox in -1..1) {
            val h = Noise.hash(ix + ox, iy + oy, 13)
            val dx = gx - (ix + ox) - 0.15f - ((h ushr 8) and 255) / 255f * 0.7f
            val dy = gy - (iy + oy) - 0.15f - ((h ushr 16) and 255) / 255f * 0.7f
            val dd = dx * dx + dy * dy
            if (dd < d1) { d2 = d1; d1 = dd; id = h } else if (dd < d2) d2 = dd
        }
        val edge = sqrt(d2) - sqrt(d1) // 0 on the crack between two lumps
        val lump = ((id ushr 24) and 255) / 255f
        val crack = edge < 0.22f
        val sc = 0.35f / n
        var heat = Noise.v2(px * sc, py * sc + t.toFloat() * 1.8f, 15) * 0.75f + (1f - d) * 0.35f + blow * 0.35f - lump * 0.25f - damp
        heat += if (crack) 0.18f else if (edge > 0.45f) -0.1f else 0f
        heat += (Dither.at(px, py) - 0.5f) * 0.07f
        val ashy = if (damp > 0f) 0.6f else 0.22f
        return when {
            heat > 0.95f -> Pal.FLAME[0]
            heat > 0.75f -> Pal.FLAME[1]
            heat > 0.55f -> Pal.FLAME[2]
            heat > 0.38f -> Pal.FLAME[3]
            heat > 0.24f -> Pal.FLAME[4]
            heat > 0.14f -> Col.hex(0x44201A)
            !crack && Noise.rnd(px, py, 14) < ashy -> if (damp > 0f) ASH else Col.hex(0x4A4448)
            else -> Col.hex(0x2A1A16)
        }
    }

    /**
     * The fire's glow closer up: about the warmth of the scene canvas's dithered one (a dithered circle of
     * [alpha]), laid on smoothly, so it doesn't turn to a grid of dots over the stone.
     */
    private fun glowFine(cx: Float, cy: Float, r: Float, col: Int, alpha: Float) {
        if (alpha <= 0f) return
        for (py in max(c.top, floor(cy - r).toInt())..min(c.bottom - 1, ceil(cy + r).toInt())) {
            val dy = py + 0.5f - cy
            for (px in max(c.left, floor(cx - r).toInt())..min(c.right - 1, ceil(cx + r).toInt())) {
                val dx = px + 0.5f - cx
                val d2 = (dx * dx + dy * dy) / (r * r)
                if (d2 > 1f) continue
                c.blend(px, py, col, alpha * (1f - d2 * 0.6f) * 0.85f * 1.25f)
            }
        }
    }

    /**
     * The sparks closer up, flying the same paths as on the scene's canvas (see the village's sparks): each a small
     * white-hot point cooling to yellow and orange as it rises, a faint glow round it, a short trail under it.
     */
    private fun sparksFine(x: Float, y: Float, n: Int, seed: Int) {
        val k = K; val sz = detail
        c.penEmissive = true
        for (j in 0 until n) {
            val a = ((t / 1.3 + j.toFloat() / n + seed * 0.29 + Noise.rnd(j, seed) * 0.3) % 1.0).toFloat()
            if (Noise.rnd(j, seed, (t / 1.3 + j.toFloat() / n).toInt()) < 0.3f) continue
            val px = x + (sin(a * 8 + j).toFloat() * 1.5f + (Noise.rnd(j, seed, 5) - 0.5f) * a * 8f) * k
            val py = y - a * 16f * k
            val core = if (a < 0.4f) Pal.FLAME[0] else if (a < 0.7f) Pal.FLAME[1] else Pal.FLAME[2]
            val trail = if (a < 0.4f) Pal.FLAME[1] else if (a < 0.7f) Pal.FLAME[2] else Pal.FLAME[3]
            val sx = px.toInt() + (k - sz) / 2; val sy = py.toInt() + (k - sz) / 2
            for (i in 1..sz + 1) c.blend(sx + sz / 2, sy + sz - 1 + i, trail, 0.85f - i * 0.18f)
            for (i in 0 until sz) {
                c.blend(sx + i, sy - 1, core, 0.35f); c.blend(sx + i, sy + sz, core, 0.35f)
                c.blend(sx - 1, sy + i, core, 0.35f); c.blend(sx + sz, sy + i, core, 0.35f)
            }
            c.fillRect(sx, sy, sz, sz, core)
        }
        c.penEmissive = false
    }

    /**
     * Sparks off the anvil (a dialog's "sparks"): with every blow of the hammer a spray of them flies up and out and
     * falls, white-hot cooling to orange, the anvil lit for a moment; more the higher the level.
     */
    private fun anvilSparks(level: Float) {
        if (level <= 0.02f) return
        val cx = iso.sx(ax, ay); val cy = iso.sy(ax, ay, anvilTop)
        val period = 0.62
        val blow = floor(t / period).toInt()
        val u = ((t / period) - blow).toFloat() / 0.55f
        if (u >= 1f) return // between blows
        val n = (4 + 9 * level).toInt()
        val sz = detail
        s.fx {
            c.penEmissive = true
            for (j in 0 until n) {
                val a = (Noise.rnd(blow, j, 5) - 0.5f) * 2.8f
                val sp = 0.6f + Noise.rnd(blow, j, 6) * 0.8f
                val px = cx + sin(a) * sp * 18f * sz * u
                val py = cy - cos(a) * sp * 12f * sz * u + 16f * sz * u * u
                val col = if (u < 0.3f) Pal.FLAME[0] else if (u < 0.65f) Pal.FLAME[1] else Pal.FLAME[2]
                for (yy in 0 until sz) for (xx in 0 until sz) c.blend(px.toInt() + xx, py.toInt() + yy, col, 1f)
            }
            c.penEmissive = false
        }
        s.light(cx, cy - 3f * K, 22f, 0.9f * level * (1f - u))
    }

    /** Tapped ([pokes]) the anvil rings: rings of its sound spreading from the horn, a glint over its face, sparks jumping off; the second time twice. */
    private fun anvilRing() {
        val pk = poked("anvil") ?: return
        val a = pk.age(t)
        val cx = iso.sx(ax, ay); val cy = iso.sy(ax, ay, anvilTop)
        for (b in 0..pk.step) {
            val u = (a - b * 0.65f) / 1.15f
            if (u <= 0f || u >= 1f) continue
            s.fx {
                PokeArt.rings(c, cx, cy - 2f * K, u, 15f * K, Col.hex(0xE8F0FF), max(1, detail - 1), 0.85f, n = 3)
                PokeArt.sparks(c, cx, cy - K, u * 1.8f, 9 + 5 * b, 40 + b, 9f * K, 16f * K, detail, fall = 2.8f)
                // the glint running over the face
                c.penEmissive = true
                val gx = cx + (u * 2f - 1f) * 5f * K
                for (o in 0 until 2 * detail) c.blend(floor(gx).toInt() + o, floor(cy).toInt() - detail, Col.hex(0xFFFFFF), 0.9f * (1f - u))
                c.penEmissive = false
            }
            s.light(cx, cy - 3f * K, 20f, 0.8f * (1f - u))
        }
    }

    /** The forge's light on the room, flickering and brighter with each breath of the bellows; [banked], a small low glow. */
    private fun lights(br: Float, banked: Boolean = false) {
        val blow = 1f - br
        val flick = 0.9f + 0.1f * sin(t * 9.3).toFloat() * sin(t * 4.1 + 1).toFloat()
        val lx = iso.sx(potX, potY); val ly = iso.sy(potX, potY, ft + 4f)
        if (banked) {
            // the embers glow low: enough to see the sleeper by
            s.light(lx, ly, 22f, 0.55f * flick)
            s.light(lx, ly + 10f * K, 46f, 0.32f * flick)
            return
        }
        s.light(lx, ly, 30f, (0.95f + blow * 0.35f) * flick)
        s.light(lx, ly + 10f * K, 74f, (0.85f + blow * 0.2f) * flick)
    }

    /**
     * The great bellows by the forge on their trestle: a pear-shaped lower board, the ribbed leather, the upper board
     * hinged at the nozzle and lifting at the wide end with each breath, brass nails round its rim, a handle on each
     * board; the iron nozzle into the forge.
     */
    private fun bellows(br: Float) {
        val xn = fx1 + 0.3f; val xw = 6.05f; val yc = 2f
        val open = 0.6f + br * 2.2f                          // how far the wide end stands open
        fun u(x: Float) = ((x - xn) / (xw - xn)).coerceIn(0f, 1f)
        fun zLo(x: Float) = ft - 1.6f - u(x) * 4.2f            // the lower board's top, sloping down from the hearth
        fun zUp(x: Float) = zLo(x) + 0.2f + open * u(x)        // the upper board's underside
        // the boards' outline: a long pear, pointed at the nozzle, round at the back
        val bw = floatArrayOf(0.08f, 0.3f, 0.47f, 0.43f, 0.27f, 0f)
        val bx = floatArrayOf(xn, xn + 0.6f, xw - 0.7f, xw - 0.3f, xw - 0.09f, xw)
        val n = bx.size - 1
        // one picture px, in nominal px: the rods below are K px thick, a line per px
        val px1 = 0.5f / detail
        // the trestle's front legs and the rail between them
        iso.post(xn + 0.4f, yc + 0.25f, fl, zLo(xn + 0.4f) - 0.7f, Pal.WOOD_D)
        iso.post(xw - 0.45f, yc + 0.45f, fl, zLo(xw - 0.45f) - 0.7f, Pal.WOOD_D)
        if (fine) rodIso(xn + 0.4f, yc + 0.3f, fl + 1.6f, xw - 0.45f, yc + 0.5f, fl + 1.6f, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_D)
        else iso.line(xn + 0.4f, yc + 0.3f, fl + 1.6f, xw - 0.45f, yc + 0.5f, fl + 1.6f, Pal.WOOD_M)
        // the iron nozzle into the forge's side
        part()
        for (d in 0 until K) {
            val col = if (!fine) (if (d == 0) ironD else ironHi) else when {
                d == 0 -> ironD
                d < detail -> iron
                d == K - 1 -> steel
                else -> ironHi
            }
            iso.line(fx1 - 0.02f, yc, zLo(xn) - 0.2f + d * px1, xn + 0.1f, yc, zLo(xn) - 0.2f + d * px1, col)
        }
        // the lower board with its handle: the top, and its rim along the two visible sides
        part()
        for (d in 0 until K) {
            val col = if (!fine) (if (d == 0) Pal.WOOD_D else Pal.WOOD_M) else when {
                d == 0 -> Pal.WOOD_X
                d < detail -> Pal.WOOD_D
                d == K - 1 -> Pal.WOOD_L
                else -> Pal.WOOD_M
            }
            iso.line(xw - 0.1f, yc, zLo(xw) - 0.6f + d * px1, xw + 0.6f, yc, zLo(xw) - 0.4f + d * px1, col)
        }
        outlinePoly(bx, bw, yc) { zLo(it) }
        c.polyFill(Pal.WOOD_M)
        for (i in 0 until n) edge(bx[i], yc + bw[i], bx[i + 1], yc + bw[i + 1], zLo(bx[i]) - 0.7f, zLo(bx[i + 1]) - 0.7f, 0.7f, Pal.WOOD_D)
        for (i in n downTo 3) edge(bx[i], yc - bw[i], bx[i - 1], yc - bw[i - 1], zLo(bx[i]) - 0.7f, zLo(bx[i - 1]) - 0.7f, 0.7f, Pal.WOOD_X)
        // the leather between the boards, its ribs fanning out from the nozzle as the wide end opens
        part()
        for (i in 0 until n) leather(bx[i], yc + bw[i], bx[i + 1], yc + bw[i + 1], zLo(bx[i]), zLo(bx[i + 1]), zUp(bx[i]), zUp(bx[i + 1]), 1f)
        for (i in n downTo n - 1) leather(bx[i], yc - bw[i], bx[i - 1], yc - bw[i - 1], zLo(bx[i]), zLo(bx[i - 1]), zUp(bx[i]), zUp(bx[i - 1]), 0.72f)
        // the upper board: its rim, its top with the grain along it, brass nails round the edge, its handle
        part()
        for (i in 0 until n) edge(bx[i], yc + bw[i], bx[i + 1], yc + bw[i + 1], zUp(bx[i]), zUp(bx[i + 1]), 0.7f, Pal.WOOD_D)
        for (i in n downTo 3) edge(bx[i], yc - bw[i], bx[i - 1], yc - bw[i - 1], zUp(bx[i]), zUp(bx[i - 1]), 0.7f, Pal.WOOD_X)
        outlinePoly(bx, bw, yc) { zUp(it) + 0.7f }
        if (fine) {
            val gx = 0.08f / detail; val gy = 0.7f / detail
            c.polyFill { px, py ->
                when {
                    Noise.v2(px * gx, py * gy, 94) > 0.64f -> Col.mix(Pal.WOOD_L, Pal.WOOD_M, 0.6f)
                    Noise.v2(px * 0.05f, py * 1.1f, 95) > 0.74f -> Col.mix(Pal.WOOD_L, Pal.WOOD_M, 0.3f)
                    else -> Pal.WOOD_L
                }
            }
        } else c.polyFill { px, py -> if (Noise.v2(px * 0.08f, py * 0.7f, 94) > 0.64f) Col.mix(Pal.WOOD_L, Pal.WOOD_M, 0.6f) else Pal.WOOD_L }
        // a leather rim nailed round the board
        for (i in 0 until n) for (sgn in floatArrayOf(1f, -1f)) {
            val xa = bx[i]; val xb = bx[i + 1]
            val ya = yc + sgn * (bw[i] * 0.8f); val yb = yc + sgn * (bw[i + 1] * 0.8f)
            if (fine) {
                rodIso(xa, ya, zUp(xa) + 0.7f, xb, yb, zUp(xb) + 0.7f, Pal.WOOD_M, Col.hex(0x8A5634), Pal.WOOD_M, detail - 1)
            } else iso.line(xa, ya, zUp(xa) + 0.7f, xb, yb, zUp(xb) + 0.7f, Pal.WOOD_M)
        }
        for (i in 1 until n) for (sgn in floatArrayOf(1f, -1f)) {
            val nx = bx[i]; val ny = yc + sgn * (bw[i] * 0.8f)
            if (fine) brassNail(iso.ix(nx, ny), iso.iy(nx, ny, zUp(nx) + 0.7f))
            else c.set(iso.ix(nx, ny), iso.iy(nx, ny, zUp(nx) + 0.7f), Pal.GOLD)
        }
        // closer up, a nail between each two of those
        if (fine) for (i in 0 until n) for (sgn in floatArrayOf(1f, -1f)) {
            if (i == 0 && sgn < 0f) continue
            val nx = (bx[i] + bx[i + 1]) / 2f; val ny = yc + sgn * ((bw[i] + bw[i + 1]) / 2f * 0.8f)
            brassNail(iso.ix(nx, ny), iso.iy(nx, ny, zUp(nx) + 0.7f))
        }
        val hz = zUp(xw) + 0.3f
        for (d in 0 until K) {
            val col = if (!fine) (if (d == 0) Pal.WOOD_D else Pal.WOOD_L) else when {
                d == 0 -> Pal.WOOD_X
                d < detail -> Pal.WOOD_D
                d == K - 1 -> Col.mix(Pal.WOOD_L, Col.hex(0xFFFFFF), 0.2f)
                else -> Pal.WOOD_L
            }
            iso.line(xw - 0.1f, yc, hz + d * px1, xw + 0.6f, yc, hz + 0.4f + open * 0.3f + d * px1, col)
        }
    }

    /** A brass nail closer up, centred on picture point ([x], [y]): a round head, lit at its top left. */
    private fun brassNail(x: Int, y: Int) {
        val n = detail
        stud(x - n / 2, y - n / 2, Pal.GOLD, Pal.GOLD_L, Col.scale(Pal.GOLD, 0.62f))
    }

    /** Starts a polygon round the bellows' boards at the height [z] gives for each x. */
    private inline fun outlinePoly(bx: FloatArray, bw: FloatArray, yc: Float, z: (Float) -> Float) {
        c.polyBegin()
        for (i in bx.indices) c.polyAdd(iso.sx(bx[i], yc + bw[i]), iso.sy(bx[i], yc + bw[i], z(bx[i])))
        for (i in bx.indices.reversed()) c.polyAdd(iso.sx(bx[i], yc - bw[i]), iso.sy(bx[i], yc - bw[i], z(bx[i])))
    }

    /**
     * A strip of the bellows' leather from ([xa], [ya]) to ([xb], [yb]), from the lower board ([la] .. [lb]) up to the
     * upper one ([ua] .. [ub]): three ribs that fan out as it opens; closer up, each fold shaded from its crease up
     * to its ridge, a sheen along the ridge.
     */
    private fun leather(xa: Float, ya: Float, xb: Float, yb: Float, la: Float, lb: Float, ua: Float, ub: Float, light: Float) {
        val ax0 = iso.sx(xa, ya); val bx0 = iso.sx(xb, yb)
        val ayb = iso.sy(xa, ya, la); val byb = iso.sy(xb, yb, lb)
        val ayt = iso.sy(xa, ya, ua); val byt = iso.sy(xb, yb, ub)
        val mid = Col.scale(Col.hex(0x84482A), light); val crease = Col.scale(Col.hex(0x40220F), light); val ridge = Col.scale(Col.hex(0xB0703E), light)
        val sheen = Col.scale(Col.hex(0xD8A068), light)
        c.polyBegin(); c.polyAdd(ax0, ayb); c.polyAdd(bx0, byb); c.polyAdd(bx0, byt); c.polyAdd(ax0, ayt)
        c.polyFill { px, py ->
            val along = if (abs(bx0 - ax0) < 0.5f) 0f else ((px + 0.5f - ax0) / (bx0 - ax0)).coerceIn(0f, 1f)
            val yb0 = ayb + (byb - ayb) * along; val yt0 = ayt + (byt - ayt) * along
            val f = if (yb0 - yt0 < 0.5f) 0f else ((yb0 - py - 0.5f) / (yb0 - yt0)).coerceIn(0f, 0.999f)
            val p = (f * 3f) % 1f
            if (fine) when {
                p < 0.2f -> crease
                p < 0.32f -> Col.mix(crease, mid, 0.5f)
                p < 0.54f -> mid
                p < 0.62f -> Col.mix(mid, ridge, 0.5f)
                p < 0.76f -> ridge
                p < 0.84f -> sheen
                else -> Col.mix(ridge, mid, 0.45f)
            } else if (p < 0.3f) crease else if (p > 0.6f) ridge else mid
        }
    }

    /** A vertical strip [h] px high from ([xa], [ya], [za]) to ([xb], [yb], [zb]). */
    private fun edge(xa: Float, ya: Float, xb: Float, yb: Float, za: Float, zb: Float, h: Float, col: Int) {
        iso.quad(xa, ya, za, xb, yb, zb, xb, yb, zb + h, xa, ya, za + h, col)
    }

    /**
     * The iron hood over the forge: a riveted skirt, two sloping sides of plates up to the chimney, which rises above
     * the walls in brick under a stone cap; snow on the cap in winter.
     */
    private fun hood() {
        val s1 = (hy1 - cy1) / (hz1 - hz0); val s2 = (hx1 - cx1) / (hz1 - hz0)
        // the +y slope
        quadFill(x0, hy1, hz0, hx1, hy1, hz0, cx1, cy1, hz1, x0, cy1, hz1) { px, py ->
            val z = (2f * ua(px) + 4f * hy1 + 4f * s1 * hz0 - vb(py)) / (1f + 4f * s1)
            val x = ua(px) + hy1 - s1 * (z - hz0)
            hoodPlate(x, z, px, py, 1f)
        }
        // the +x slope
        quadFill(hx1, y0, hz0, hx1, hy1, hz0, cx1, cy1, hz1, cx1, y0, hz1) { px, py ->
            val z = (4f * hx1 + 4f * s2 * hz0 - 2f * ua(px) - vb(py)) / (1f + 4f * s2)
            val y = hx1 - s2 * (z - hz0) - ua(px)
            hoodPlate(y + 7f, z, px, py, 0.72f)
        }
        // the skirt, riveted
        part()
        quadFill(x0, hy1, hz0 - sk, hx1, hy1, hz0 - sk, hx1, hy1, hz0, x0, hy1, hz0) { px, py ->
            val x = wallYx(px, hy1); val z = wallYz(px, py, hy1)
            skirt(x, z, 1f)
        }
        quadFill(hx1, y0, hz0 - sk, hx1, hy1, hz0 - sk, hx1, hy1, hz0, hx1, y0, hz0) { px, py ->
            val y = wallXy(px, hx1); val z = wallXz(px, py, hx1)
            skirt(y + 0.15f, z, 0.72f)
        }
        // the chimney
        part()
        quadFill(x0, cy1, hz1, cx1, cy1, hz1, cx1, cy1, chimH, x0, cy1, chimH) { px, py -> Col.mix(brickAt(wallYx(px, cy1), wallYz(px, py, cy1), 1f, px, py), sootC, 0.25f) }
        quadFill(cx1, y0, hz1, cx1, cy1, hz1, cx1, cy1, chimH, cx1, y0, chimH) { px, py -> Col.mix(brickAt(wallXy(px, cx1) + 0.3f, wallXz(px, py, cx1), 0.78f, px, py), sootC, 0.25f) }
        // the cap and the flue's mouth
        part()
        val cxa = x0 - 0.12f; val cya = y0 - 0.12f; val cw = cx1 - x0 + 0.24f
        iso.box(cxa, cya, chimH, cw, cw, 1.2f, if (winter) Pal.SNOW_L else stoneL, stoneM, stoneD)
        iso.top(x0 + 0.12f, y0 + 0.12f, chimH + 1.2f, cx1 - x0 - 0.24f, cy1 - y0 - 0.24f, Col.hex(0x141012))
    }

    /** An iron plate of the hood at [a] cells along and [z] px up: rows of plates, rivets along the seams, sooty higher up. */
    private fun hoodPlate(a: Float, z: Float, px: Int, py: Int, light: Float): Int {
        val row = (z - hz0) / 1.7f
        val fr = (row - floor(row)) * 1.7f
        val col = if (fine) hoodPlateFine(a, z, fr, px, py) else when {
            fr < 0.36f -> ironD
            fr < 0.8f && abs(((a / 0.32f) % 1f) - 0.5f) < 0.16f -> ironHi
            Noise.rnd(px, py, 95) < 0.05f -> ironM
            z < hz0 + 1.2f -> Col.mix(ironM, ironL, 0.4f)
            else -> Col.mix(ironM, iron, ((z - hz0) / (hz1 - hz0)).coerceIn(0f, 1f))
        }
        return if (light == 1f) col else Col.scale(col, light)
    }

    /** A plate of the hood closer up: the seam's shadow and the plate's lit lower lip, round rivets, hammer marks. */
    private fun hoodPlateFine(a: Float, z: Float, fr: Float, px: Int, py: Int): Int {
        val k = K.toFloat()
        val rpx = ((a / 0.32f) % 1f - 0.5f) * 0.32f * 4f * k; val rpz = (fr - 0.58f) * k
        val rr = 0.26f * k
        val base = if (z < hz0 + 1.2f) Col.mix(ironM, ironL, 0.4f) else Col.mix(ironM, iron, ((z - hz0) / (hz1 - hz0)).coerceIn(0f, 1f))
        return when {
            fr < 0.28f -> ironD
            fr < 0.28f + 1f / k -> Col.mix(base, ironHi, 0.55f)
            rpx * rpx + rpz * rpz < rr * rr -> if (rpz > rr * 0.25f) steel else if (rpz < -rr * 0.4f) ironM else ironHi
            Noise.rnd(px, py, 95) < 0.05f -> ironM
            Noise.v2(a * 6f, z * 1.2f, 96) > 0.72f -> Col.mix(base, ironL, 0.3f)
            else -> base
        }
    }

    private fun skirt(a: Float, z: Float, light: Float): Int {
        val col = if (fine) skirtFine(a, z) else when {
            z > hz0 - 0.5f -> ironHi
            z < hz0 - 1.1f -> ironD
            abs(((a / 0.35f) % 1f) - 0.5f) < 0.14f -> steel
            else -> ironL
        }
        return if (light == 1f) col else Col.scale(col, light)
    }

    /** The hood's skirt closer up: a rolled top edge, a dark lower edge, round rivets. */
    private fun skirtFine(a: Float, z: Float): Int {
        val k = K.toFloat()
        val ra = ((a / 0.35f) % 1f - 0.5f) * 0.35f * 4f * k; val rz = (z - (hz0 - 0.8f)) * k
        val rr = 0.26f * k
        return when {
            z > hz0 - 1f / k -> steel
            z > hz0 - 0.5f -> ironHi
            z < hz0 - 1.6f + 1f / k -> ironD
            z < hz0 - 1.1f -> iron
            ra * ra + rz * rz < rr * rr -> if (rz > rr * 0.25f) steel else ironHi
            else -> ironL
        }
    }

    /** A chain from a hook in the right wall's plate beside the hood, its links flat and edge-on by turns, swaying a little, a hook at its end. */
    private fun chain() {
        val hx = iso.sx(chainX, y0 + 0.2f); val top = iso.sy(chainX, y0 + 0.2f, wallH)
        val bottom = iso.sy(chainX, y0 + 0.2f, fl + 13f)
        val sway = sin(t * 0.8).toFloat() * 1.3f
        if (fine) { chainFine(hx, top, bottom, sway); return }
        c.fillRect(hx.toInt() - 1, top.toInt() - 3, 3, 3, ironM); c.set(hx.toInt(), top.toInt() - 3, ironHi)
        var y = top + 1f; var k = 0
        while (y < bottom) {
            val f = (y - top) / (bottom - top)
            val x = (hx + sway * f).toInt(); val yi = y.toInt()
            if (k % 2 == 0) {
                c.hline(x - 1, x + 1, yi, ironHi); c.vline(x - 2, yi + 1, yi + 3, ironL); c.vline(x + 2, yi + 1, yi + 3, ironM); c.hline(x - 1, x + 1, yi + 4, ironM)
            } else {
                c.vline(x, yi, yi + 4, ironHi); c.set(x + 1, yi + 2, ironM)
            }
            y += 4f; k++
        }
        // the hook
        val x = (hx + sway).toInt(); val yi = y.toInt()
        c.vline(x, yi, yi + 4, ironL); c.hline(x - 3, x, yi + 5, ironL); c.vline(x - 3, yi + 2, yi + 4, ironL); c.set(x - 3, yi + 1, ironHi)
    }

    /** The chain closer up: flat links as round rings of wire with a lit rim and a shadowed inside, edge-on links as lit bars, the hook shaped. */
    private fun chainFine(hx: Float, top: Float, bottom: Float, sway: Float) {
        val n = detail
        // the staple in the plate
        val sx0 = hx.toInt() - n; val sy0 = top.toInt() - 3 * n
        c.fillRect(sx0, sy0, 3 * n, 3 * n, ironM)
        c.fillRect(sx0, sy0 + 3 * n - 1, 3 * n, 1, ironD)
        c.fillRect(sx0, sy0, 1, 3 * n - 1, ironL)
        stud(hx.toInt(), sy0, ironL, ironHi, iron)
        var y = top + n; var k = 0
        while (y < bottom) {
            val f = (y - top) / (bottom - top)
            val x = (hx + sway * n * f).toInt(); val yi = y.toInt()
            if (k % 2 == 0) flatLink(x + n * 0.5f, yi + 2.5f * n) else {
                // edge-on: a bar of wire, lit on its left, rounded at its ends, the far side's nub
                c.fillRect(x, yi + 1, n, 5 * n - 2, ironL)
                c.fillRect(x + (n - 1) / 2, yi, 1, 5 * n, ironL)
                c.vline(x, yi + 1, yi + 5 * n - 2, ironHi)
                if (n > 2) c.vline(x + n - 1, yi + 1, yi + 5 * n - 2, ironM)
                c.fillRect(x + n, yi + 2 * n, max(1, n - 1), n, ironM)
            }
            y += 4f * n; k++
        }
        // the hook: down, round the bottom and up to its point
        val x = (hx + sway * n).toInt(); val yi = y.toInt()
        c.fillRect(x, yi, n, 5 * n, ironL); c.vline(x, yi, yi + 5 * n - 1, ironHi)
        c.fillRect(x - 3 * n + 1, yi + 5 * n, 4 * n - 1, n, ironL); c.hline(x - 3 * n + 1, x + n - 2, yi + 6 * n - 1, iron)
        c.fillRect(x - 3 * n, yi + 2 * n, n, 3 * n, ironL); c.vline(x - 3 * n, yi + 2 * n, yi + 5 * n - 1, ironHi)
        c.fillRect(x - 3 * n + 1, yi + n, n - 1, n, ironHi)
    }

    /** A flat link of the chain closer up round ([cx], [cy]): a rounded ring of wire lit on its top and left, dark inside. */
    private fun flatLink(cx: Float, cy: Float) {
        val n = detail
        val r = 2f * n; val half = n * 0.42f + 0.12f
        for (py in floor(cy - r - half).toInt()..ceil(cy + r + half).toInt()) for (px in floor(cx - r - half).toInt()..ceil(cx + r + half).toInt()) {
            val dx = px + 0.5f - cx; val dy = py + 0.5f - cy
            val off = sqrt(sqrt(dx * dx * dx * dx + dy * dy * dy * dy)) - r // a rounded square
            if (abs(off) > half) continue
            c.set(px, py, when {
                off < 1f - half && dy > -r * 0.3f -> ironD
                dy < -r * 0.5f && dx < r * 0.4f -> ironHi
                dx < -r * 0.5f -> ironL
                dy > r * 0.5f -> iron
                else -> ironM
            })
        }
    }

    /** A cart wheel leaning on the left wall, waiting for its iron tyre: five felloes, ten spokes, the hub. */
    private fun wheel() {
        val xw = x0 + 0.3f; val yc = 7.3f; val r = 5.6f; val ry = r / 4.5f; val zc = fl + r
        val tau = 2f * PI.toFloat()
        quadPlot(xw, yc - ry, zc - r, xw, yc + ry, zc - r, xw, yc + ry, zc + r, xw, yc - ry, zc + r) { px, py ->
            val u = (wallXy(px, xw) - yc) / ry; val v = (wallXz(px, py, xw) - zc) / r
            val d = hyp(u, v)
            val ang = atan2(v, u)
            val lit = u + v
            when {
                d > 1f -> 0
                d > 0.79f -> {
                    val seg = (ang / tau * 5f + 5.3f) % 1f
                    when {
                        seg < 0.05f -> Pal.WOOD_X
                        fine && seg < 0.1f && d in 0.85f..0.92f -> ironM // a dowel by the joint
                        d > 0.93f -> if (lit > 0f) Pal.WOOD_M else Pal.WOOD_D
                        d < 0.85f -> Pal.WOOD_D
                        fine && Noise.v2(ang * 9f, d * 26f, 98) > 0.72f -> Col.mix(if (lit > 0.3f) Pal.WOOD_L else Pal.WOOD_M, Pal.WOOD_D, 0.4f)
                        lit > 0.3f -> Pal.WOOD_L
                        else -> Pal.WOOD_M
                    }
                }
                fine && d < 0.06f -> ironD
                d < 0.12f -> ironHi
                d < 0.24f -> if (lit > 0f) Pal.WOOD_L else Pal.WOOD_M
                d < 0.3f -> if (fine && lit > 0.1f) ironL else ironM
                else -> {
                    val sp = (((ang / tau * 10f + 10.5f) % 1f) - 0.5f) * (tau / 10f) * d * r
                    if (abs(sp) < 0.6f) (if (sp < 0f) Pal.WOOD_L else if (fine && sp > 0.42f) Pal.WOOD_D else Pal.WOOD_M) else 0
                }
            }
        }
    }

    /**
     * The second anvil (level 3): a small one on a low stump in front of the wheel, for nails and hooks: its body, its
     * face lit along the front edge, the horn toward the wall, a punch lying across it.
     */
    private fun smallAnvil() {
        val x = 3.75f; val y = 7.35f; val sh = 2.4f
        val cx = iso.sx(x, y); val yb = iso.sy(x, y, fl); val yt = iso.sy(x, y, fl + sh)
        val rx = 5.2f * detail; val ryy = 2.6f * detail
        c.fillEllipse(cx, yb, rx * 1.08f, ryy * 1.1f, Pal.LOG_D)
        for (px in floor(cx - rx).toInt()..(cx + rx).toInt()) {
            val dx = (px + 0.5f - cx) / rx
            if (abs(dx) > 1f) continue
            val e = sqrt(1f - dx * dx) * ryy
            c.vline(px, yt.toInt(), (yb + e).toInt(), if (dx < -0.45f) Pal.LOG_L else if (dx > 0.45f) Pal.LOG_D else if (Noise.rnd(px / detail, 23) < 0.3f) Pal.LOG_D else Pal.LOG_M)
        }
        c.fillEllipse(cx, yt, rx, ryy, Col.hex(0xCFA46C))
        c.fillEllipse(cx, yt, rx * 0.55f, ryy * 0.55f, Col.hex(0xA87E4C))
        c.fillEllipse(cx, yt, rx * 0.3f, ryy * 0.3f, Col.hex(0xCFA46C))
        val sz = fl + sh; val fz = sz + 2.8f
        val top = Col.hex(0xA6A4AE); val side = Col.hex(0x5C5A64); val end = Col.hex(0x3C3A42)
        iso.box(x - 0.3f, y - 0.2f, sz, 0.6f, 0.4f, 0.9f, side, Col.scale(side, 0.9f), end)
        iso.box(x - 0.46f, y - 0.18f, sz + 0.9f, 0.92f, 0.36f, fz - sz - 0.9f, top, side, end)
        val tipX = x - 1.0f
        iso.quad(x - 0.46f, y + 0.18f, fz, x - 0.46f, y + 0.18f, sz + 1.2f, tipX, y, fz - 0.5f, tipX, y, fz - 0.3f, side)
        iso.quad(x - 0.46f, y - 0.18f, fz, x - 0.46f, y + 0.18f, fz, tipX, y, fz - 0.3f, tipX, y, fz - 0.3f, top)
        iso.line(x - 0.46f, y + 0.18f, fz, x + 0.46f, y + 0.18f, fz, Col.hex(0xD8D6DE))
        iso.line(x - 0.46f, y + 0.18f, fz, tipX, y, fz - 0.3f, Col.hex(0xD8D6DE))
        // a punch lying across its face
        rodIso(x - 0.15f, y - 0.1f, fz + 0.3f, x + 0.35f, y + 0.25f, fz + 0.2f, ironM, ironHi, iron, max(1, detail))
    }

    // ------------------------------------------------------------------ the floor's things

    /** A crate of nails by the wall: boards, the nails heaped in it catching the light, one dropped on the floor. */
    private fun nails() {
        val xa = 6.95f; val ya = 3.7f; val cw = 0.8f; val cd = 0.7f; val ch = 3.2f
        iso.box(xa, ya, fl, cw, cd, ch, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        iso.line(xa, ya + cd, fl + ch / 2, xa + cw, ya + cd, fl + ch / 2, Pal.WOOD_X)
        iso.line(xa + cw, ya, fl + ch / 2, xa + cw, ya + cd, fl + ch / 2, Pal.WOOD_X)
        if (fine) {
            // the crate's corner, lit, and the nails that hold its boards
            iso.line(xa + cw, ya + cd, fl, xa + cw, ya + cd, fl + ch, Col.mix(Pal.WOOD_M, Pal.WOOD_L, 0.5f))
            for ((nx, ny) in listOf(xa + 0.12f to ya + cd, xa + cw - 0.12f to ya + cd, xa + cw to ya + 0.12f, xa + cw to ya + cd - 0.12f)) {
                for (nz in floatArrayOf(fl + ch / 4, fl + ch * 3 / 4)) c.set(iso.ix(nx, ny), iso.iy(nx, ny, nz), ironM)
            }
        }
        iso.top(xa + 0.1f, ya + 0.1f, fl + ch, cw - 0.2f, cd - 0.2f, Col.hex(0x3A3438))
        for (k in 0 until 16) {
            val nx = xa + 0.14f + Noise.rnd(k, 25) * (cw - 0.28f); val ny = ya + 0.14f + Noise.rnd(k, 26) * (cd - 0.28f)
            val px = iso.ix(nx, ny); val py = iso.iy(nx, ny, fl + ch) - (Noise.rnd(k, 27) * 2f).toInt() * detail
            if (fine) heapNail(px, py, k) else { c.set(px, py, ironL); c.set(px + 1, py, if (k % 2 == 0) ironHi else ironL) }
        }
        val px = iso.ix(xa - 0.2f, ya + cd + 0.3f); val py = iso.iy(xa - 0.2f, ya + cd + 0.3f, fl)
        if (fine) {
            // the dropped nail: its head, the shank lit along its top, the point
            val n = detail
            stud(px, py, ironL, ironHi, iron)
            c.fillRect(px + n, py, 2 * n - 1, n, ironL)
            c.fillRect(px + n, py, 2 * n - 1, 1, ironHi)
            c.fillRect(px + n, py + n - 1, 2 * n - 1, 1, iron)
            c.set(px + 3 * n - 1, py + n / 2, ironL)
        } else { c.set(px, py, ironL); c.set(px + 1, py, ironHi); c.set(px + 2, py, ironL) }
    }

    /** A nail in the crate closer up: its head and its shank, lying one way or the other. */
    private fun heapNail(x: Int, y: Int, k: Int) {
        val n = detail
        val dir = if (k % 2 == 0) 1 else -1
        val len = n + (k % 3)
        val sx = if (dir > 0) x + n else x - 1
        c.line(sx, y + n / 2, sx + dir * len, y + n / 2 + len / 2, ironL)
        c.line(sx, y + n / 2 + 1, sx + dir * len, y + n / 2 + len / 2 + 1, iron)
        stud(x, y, if (k % 2 == 0) ironHi else ironL, steel, ironM)
    }

    /**
     * The anvil on its stump: a squat log flaring at its roots, bark in furrows, the end grain round the anvil's
     * feet; the anvil's feet, waist, body and polished face, the horn toward the forge, the holes in the heel.
     */
    private fun anvil() {
        val sr = 0.78f
        val cx = iso.sx(ax, ay); val yb = iso.sy(ax, ay, fl); val yt = iso.sy(ax, ay, fl + stumpH)
        val rx = sr * 11.3f * detail; val ryy = sr * 5.66f * detail
        c.fillEllipse(cx, yb, rx * 1.12f, ryy * 1.15f, Pal.LOG_D)
        if (fine) stumpFine(cx, yb, yt, rx, ryy) else {
            for (px in floor(cx - rx).toInt()..(cx + rx).toInt()) {
                val dx = (px + 0.5f - cx) / rx
                if (abs(dx) > 1f) continue
                val e = sqrt(1f - dx * dx) * ryy
                val furrow = Noise.rnd(px, 21) < 0.3f
                for (py in yt.toInt()..(yb + e).toInt()) {
                    val col = when {
                        furrow && (py + px) % 5 != 0 -> Pal.LOG_D
                        dx < -0.45f -> Pal.LOG_L
                        dx > 0.45f -> Pal.LOG_D
                        else -> Pal.LOG_M
                    }
                    c.set(px, py, col)
                }
            }
            for (py in floor(yt - ryy).toInt()..(yt + ryy).toInt()) for (px in floor(cx - rx).toInt()..(cx + rx).toInt()) {
                val dx = (px + 0.5f - cx) / rx; val dy = (py + 0.5f - yt) / ryy
                val d = dx * dx + dy * dy
                if (d > 1f) continue
                c.set(px, py, if (d > 0.78f) Pal.LOG_L else if ((sqrt(d) * 3.2f) % 1f < 0.25f) Col.hex(0xA87E4C) else Col.hex(0xCFA46C))
            }
        }
        // the anvil
        part()
        val sz = fl + stumpH; val fz = anvilTop
        val top = Col.hex(0xA6A4AE); val side = Col.hex(0x5C5A64); val end = Col.hex(0x3C3A42)
        iso.box(ax - 0.55f, ay - 0.38f, sz, 1.15f, 0.76f, 0.8f, side, Col.scale(side, 0.9f), end)
        iso.box(ax - 0.32f, ay - 0.22f, sz + 0.8f, 0.69f, 0.44f, 1.2f, side, side, end)
        iso.box(ax - 0.78f, ay - 0.3f, sz + 2f, 1.68f, 0.6f, fz - sz - 2f, top, side, end)
        // the horn: a cone out to its tip
        val tipX = ax - 1.75f
        iso.quad(ax - 0.78f, ay + 0.3f, fz, ax - 0.78f, ay + 0.3f, sz + 2.2f, tipX, ay + 0.02f, fz - 1f, tipX, ay + 0.02f, fz - 0.6f, side)
        iso.quad(ax - 0.78f, ay - 0.3f, fz, ax - 0.78f, ay + 0.3f, fz, tipX, ay + 0.02f, fz - 0.6f, tipX, ay - 0.02f, fz - 0.6f, top)
        if (fine) { anvilFine(sz, fz, tipX, end); return }
        iso.line(ax - 0.78f, ay + 0.3f, fz, tipX, ay + 0.02f, fz - 0.6f, Col.hex(0xD8D6DE))
        // the face: polished, a bright front edge, the hardy and pritchel holes in the heel
        iso.line(ax - 0.78f, ay + 0.3f, fz, ax + 0.9f, ay + 0.3f, fz, Col.hex(0xD8D6DE))
        iso.line(ax - 0.5f, ay - 0.05f, fz, ax + 0.3f, ay - 0.05f, fz, Col.hex(0xBEBCC6))
        iso.px(ax + 0.62f, ay - 0.08f, fz, ironD)
        c.set(iso.ix(ax + 0.42f, ay + 0.02f), iso.iy(ax + 0.42f, ay + 0.02f, fz), ironD)
    }

    /** The anvil's stump closer up: bark in plates between deep furrows, lit at their edges; the rings of its end grain, a split from the heart. */
    private fun stumpFine(cx: Float, yb: Float, yt: Float, rx: Float, ryy: Float) {
        val n = detail
        for (px in floor(cx - rx).toInt()..(cx + rx).toInt()) {
            val dx = (px + 0.5f - cx) / rx
            if (abs(dx) > 1f) continue
            val e = sqrt(1f - dx * dx) * ryy
            val col0 = Math.floorDiv(px, n); val inCol = px - col0 * n
            val furrow = Noise.rnd(col0, 21) < 0.3f
            val plateEdge = !furrow && inCol == 0 && Noise.rnd(col0 - 1, 21) < 0.3f
            for (py in yt.toInt()..(yb + e).toInt()) {
                val col = when {
                    furrow && (Math.floorDiv(py, n) + col0) % 5 != 0 -> if (inCol == n / 2) Col.scale(Pal.LOG_D, 0.72f) else Pal.LOG_D
                    plateEdge -> if (dx > 0.45f) Pal.LOG_M else Pal.LOG_L
                    dx < -0.45f -> if (Noise.rnd(px, py / 2, 22) < 0.12f) Pal.LOG_M else Pal.LOG_L
                    dx > 0.45f -> Pal.LOG_D
                    else -> if (Noise.rnd(px, py / 2, 22) < 0.1f) Pal.LOG_D else Pal.LOG_M
                }
                c.set(px, py, col)
            }
        }
        val hx = cx - rx * 0.12f; val hy = yt - ryy * 0.1f
        val ca = 0.5f; val cc = cos(ca); val cs = sin(ca)
        for (py in floor(yt - ryy).toInt()..(yt + ryy).toInt()) for (px in floor(cx - rx).toInt()..(cx + rx).toInt()) {
            val dx = (px + 0.5f - cx) / rx; val dy = (py + 0.5f - yt) / ryy
            val d = dx * dx + dy * dy
            if (d > 1f) continue
            val hdx = px + 0.5f - hx; val hdy = py + 0.5f - hy
            val rr = hyp(hdx / rx, hdy / ryy)
            val along = hdx * cc + hdy * cs; val across = abs(hdy * cc - hdx * cs)
            c.set(px, py, when {
                d > 0.86f -> Pal.LOG_L
                d > 0.78f -> Col.hex(0xB88A56)
                along > 0f && along < rx * 0.7f && across < 0.5f + along * 0.05f -> Col.hex(0x7A5634)
                (rr * 5f) % 1f < 0.2f -> Col.hex(0xA87E4C)
                else -> Col.hex(0xCFA46C)
            })
        }
    }

    /**
     * The anvil closer up: its face polished brighter toward the front edge, a few scratches; the horn lit along its
     * top in two tones and dark beneath; the body's corners catching the light; the hardy hole's far wall lit.
     */
    private fun anvilFine(sz: Float, fz: Float, tipX: Float, end: Int) {
        val e = 1f / K
        iso.line(ax + 0.9f, ay + 0.3f, sz + 2f, ax + 0.9f, ay + 0.3f, fz - e, Col.hex(0x7C7A84))
        iso.line(ax + 0.37f, ay + 0.22f, sz + 0.8f, ax + 0.37f, ay + 0.22f, sz + 2f, Col.hex(0x6C6A74))
        iso.line(ax + 0.6f, ay + 0.38f, sz, ax + 0.6f, ay + 0.38f, sz + 0.8f, Col.hex(0x6C6A74))
        quadFill(ax - 0.78f, ay - 0.3f, fz, ax + 0.9f, ay - 0.3f, fz, ax + 0.9f, ay + 0.3f, fz, ax - 0.78f, ay + 0.3f, fz) { px, py ->
            val x = flatX(px, py, fz); val v = (flatY(px, py, fz) - ay + 0.3f) / 0.6f
            when {
                v > 0.8f -> Col.hex(0xC2C0CA)
                v > 0.3f && v < 0.55f && x > ax - 0.55f && x < ax + 0.35f -> Col.hex(0xB8B6C0)
                v < 0.15f -> Col.hex(0x96949E)
                Noise.rnd(px, py, 26) < 0.03f -> Col.hex(0xCAC8D2)
                else -> Col.hex(0xA6A4AE)
            }
        }
        iso.line(ax - 0.78f, ay + 0.3f, fz, ax + 0.9f, ay + 0.3f, fz, Col.hex(0xE8E6EE))
        iso.line(ax - 0.78f, ay + 0.3f, fz, tipX, ay + 0.02f, fz - 0.6f, Col.hex(0xE8E6EE))
        iso.line(ax - 0.78f, ay + 0.3f, fz - e, tipX, ay + 0.02f, fz - 0.6f - e, Col.hex(0xC4C2CC))
        iso.line(ax - 0.78f, ay + 0.3f, fz - 2 * e, tipX + 0.1f, ay + 0.03f, fz - 0.6f - 2 * e, Col.hex(0x8C8A94))
        iso.line(ax - 0.78f, ay + 0.3f, sz + 2.2f + e, tipX, ay + 0.02f, fz - 1f + e, end)
        // the hardy hole, square, its far wall lit; the pritchel hole round
        iso.px(ax + 0.62f, ay - 0.08f, fz, ironD)
        c.fillRect(iso.ix(ax + 0.62f, ay - 0.08f) + 1, iso.iy(ax + 0.62f, ay - 0.08f, fz), K - 1, 1, Col.hex(0x5C5A64))
        c.fillCircle(iso.ix(ax + 0.42f, ay + 0.02f) + detail / 2f, iso.iy(ax + 0.42f, ay + 0.02f, fz) + detail / 2f, detail * 0.62f, ironD)
    }

    /** The hammer lying on the anvil's face, its handle out over the front edge. */
    private fun hammer() {
        val fz = anvilTop
        val hx = ax + 0.05f; val hy = ay - 0.12f
        if (fine) {
            // the handle: lit along its top, shaded along its underside, a scene px and a half thick
            rodIso(hx + 0.12f, hy + 0.13f, fz + 0.4f, hx + 0.57f, hy + 1.04f, fz - 0.6f, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_D, detail + (detail + 1) / 2)
        } else {
            iso.line(hx + 0.1f, hy + 0.14f, fz + 0.5f, hx + 0.55f, hy + 1.05f, fz - 0.5f, Pal.WOOD_L)
            iso.line(hx + 0.15f, hy + 0.12f, fz + 0.3f, hx + 0.6f, hy + 1.03f, fz - 0.7f, Pal.WOOD_M)
        }
        iso.box(hx - 0.13f, hy - 0.24f, fz, 0.26f, 0.5f, 1f, Col.hex(0xA6A4AE), Col.hex(0x5C5A64), ironD)
        iso.line(hx - 0.13f, hy + 0.26f, fz + 1f, hx + 0.13f, hy + 0.26f, fz + 1f, Col.hex(0xD8D6DE))
        if (fine) {
            // the head's edges: its top's left side lit, the corner toward us, the striking face's bevel
            iso.line(hx - 0.13f, hy - 0.24f, fz + 1f, hx - 0.13f, hy + 0.26f, fz + 1f, Col.hex(0xC4C2CC))
            iso.line(hx + 0.13f, hy + 0.26f, fz, hx + 0.13f, hy + 0.26f, fz + 1f, Col.hex(0x8A8892))
            iso.line(hx + 0.13f, hy - 0.24f, fz + 1f - 1f / K, hx + 0.13f, hy + 0.26f, fz + 1f - 1f / K, Col.hex(0x4A4850))
        }
    }

    /** The tongs leaning on the stump's front, their jaws up against the anvil. */
    private fun tongs() {
        val bx = ax - 0.4f; val by = ay + 1.05f
        val pz = fl + 5.4f
        if (fine) {
            // the reins, lit on their left; the rivet at the pivot; the jaws
            val rw = (3 * detail) / 2
            rodIso(bx - 0.1f, by, fl, bx + 0.1f, by - 0.3f, pz, ironM, ironHi, iron, rw)
            rodIso(bx + 0.14f, by + 0.02f, fl, bx + 0.16f, by - 0.3f, pz, iron, ironL, ironD, rw)
            rodIso(bx + 0.12f, by - 0.3f, pz, bx + 0.04f, by - 0.38f, pz + 2.4f, ironHi, steel, ironM)
            rodIso(bx + 0.2f, by - 0.3f, pz, bx + 0.3f, by - 0.38f, pz + 2.2f, ironL, ironHi, iron)
            val rx = iso.ix(bx + 0.16f, by - 0.3f) + K / 2f; val ry = iso.iy(bx + 0.16f, by - 0.3f, pz) + K / 2f
            c.fillCircle(rx, ry, K * 0.5f, ironD)
            c.fillCircle(rx - detail * 0.35f, ry - detail * 0.35f, K * 0.22f, ironL)
            return
        }
        for (d in 0..1) {
            iso.line(bx - 0.1f + d * 0.06f, by, fl, bx + 0.1f + d * 0.06f, by - 0.3f, pz, if (d == 0) ironHi else ironM)
            iso.line(bx + 0.14f + d * 0.06f, by + 0.02f, fl, bx + 0.16f + d * 0.06f, by - 0.3f, pz, if (d == 0) ironL else iron)
        }
        iso.px(bx + 0.16f, by - 0.3f, pz, ironD)
        iso.line(bx + 0.12f, by - 0.3f, pz, bx + 0.04f, by - 0.38f, pz + 2.4f, ironHi)
        iso.line(bx + 0.2f, by - 0.3f, pz, bx + 0.3f, by - 0.38f, pz + 2.2f, ironL)
    }

    /** The quenching bucket: staves, two iron hoops, dark water with a glint; it steams now and then. */
    private fun bucket() {
        val bx = 7.35f; val by = 7.1f; val r = 0.44f; val hh = 3.6f
        val cx = iso.sx(bx, by); val yb = iso.sy(bx, by, fl); val yt = iso.sy(bx, by, fl + hh)
        val rx = r * 11.3f * detail; val ryy = r * 5.66f * detail
        for (px in floor(cx - rx).toInt()..(cx + rx).toInt()) {
            val dx = (px + 0.5f - cx) / rx
            if (abs(dx) > 1f) continue
            val e = sqrt(1f - dx * dx) * ryy
            val stave = floor((dx + 1f) * 3.5f).toInt()
            for (py in yt.toInt()..(yb + e * 0.9f).toInt()) {
                val h = (yb + e - py) / (yb - yt)
                val col = if (fine) bucketFine(dx, h, stave, px, py) else when {
                    abs(h - 0.25f) < 0.07f || abs(h - 0.78f) < 0.07f -> if (dx < -0.3f) ironL else ironM
                    ((dx + 1f) * 3.5f) % 1f < 0.18f -> Pal.WOOD_X
                    dx < -0.5f -> Pal.WOOD_L
                    dx > 0.5f -> Pal.WOOD_D
                    stave % 2 == 0 -> Pal.WOOD_M
                    else -> Col.mix(Pal.WOOD_M, Pal.WOOD_D, 0.4f)
                }
                c.set(px, py, col)
            }
        }
        c.fillEllipse(cx, yt, rx, ryy, Pal.WOOD_D)
        if (fine) {
            // the staves' tops round the rim, the inside in shadow, the water, a glint on it
            val n = detail
            c.fillEllipse(cx, yt + 0.25f * n, rx - 0.5f * n, ryy - 0.35f * n, Pal.WOOD_M)
            c.fillEllipse(cx, yt + 0.35f * n, rx - 1.2f * n, ryy - 0.8f * n, Pal.WOOD_X)
            c.fillEllipse(cx, yt + 0.6f * n, rx - 1.5f * n, ryy - 1f * n, Col.hex(0x2E4A60))
            c.fillRect(cx.toInt() - 2 * n, yt.toInt(), n, n, Col.hex(0x8AB0CC)); c.fillRect(cx.toInt() - n, yt.toInt(), n, n, Col.hex(0x6A90AC))
            c.set(cx.toInt() - 2 * n, yt.toInt(), Col.hex(0xC8E0F0))
            c.fillRect(cx.toInt() + n, yt.toInt() + n, n, 1, Col.hex(0x4A6A84))
        } else {
            c.fillEllipse(cx, yt + 0.6f, rx - 1.5f, ryy - 1f, Col.hex(0x2E4A60))
            c.set(cx.toInt() - 2, yt.toInt(), Col.hex(0x8AB0CC)); c.set(cx.toInt() - 1, yt.toInt(), Col.hex(0x6A90AC))
        }
        // steam, thicker in the first seconds after each quench
        val quench = ((t / 7.0) % 1.0).toFloat()
        val amount = if (quench < 0.35f) 1f - quench / 0.35f * 0.6f else 0.4f
        s.fx {
            for (j in 0 until 5) {
                val a = ((t * 0.42 + j / 5.0) % 1.0).toFloat()
                val sx = cx + sin(t * 1.7 + j * 2.1).toFloat() * (1f + a * 3f) * detail
                val sy = yt - 2f * detail - a * 9f * K
                c.ditherCircle(sx, sy, (1f + a * 2.2f) * K * 0.8f, Col.hex(0xF2F2F6), (1f - a) * 0.55f * amount)
            }
        }
    }

    /** A stave of the bucket closer up: thin seams, grain, the hoops lit along their top edge with a rivet each. */
    private fun bucketFine(dx: Float, h: Float, stave: Int, px: Int, py: Int): Int {
        val hoop = if (abs(h - 0.25f) < 0.07f) h - 0.25f else if (abs(h - 0.78f) < 0.07f) h - 0.78f else 9f
        if (hoop < 1f) return when {
            hoop > 0.035f -> if (dx < -0.3f) ironHi else ironL
            hoop < -0.045f -> ironD
            abs(dx + 0.55f) < 0.07f || abs(dx - 0.3f) < 0.06f -> ironHi
            else -> if (dx < -0.3f) ironL else ironM
        }
        val base = when {
            ((dx + 1f) * 3.5f) % 1f < 0.1f -> return Pal.WOOD_X
            dx < -0.5f -> Pal.WOOD_L
            dx > 0.5f -> Pal.WOOD_D
            stave % 2 == 0 -> Pal.WOOD_M
            else -> Col.mix(Pal.WOOD_M, Pal.WOOD_D, 0.4f)
        }
        return if (Noise.v2(px * 0.4f, py * 0.06f + stave * 7f, 97) > 0.7f) Col.scale(base, 0.86f) else base
    }

    /** Like [quadFill], but pixels the shader answers 0 for are left alone. */
    private inline fun quadPlot(
        xa: Float, ya: Float, za: Float, xb: Float, yb: Float, zb: Float,
        xc: Float, yc: Float, zc: Float, xd: Float, yd: Float, zd: Float,
        crossinline shader: (Int, Int) -> Int,
    ) {
        c.polyBegin()
        c.polyAdd(iso.sx(xa, ya), iso.sy(xa, ya, za)); c.polyAdd(iso.sx(xb, yb), iso.sy(xb, yb, zb))
        c.polyAdd(iso.sx(xc, yc), iso.sy(xc, yc, zc)); c.polyAdd(iso.sx(xd, yd), iso.sy(xd, yd, zd))
        c.polyScan { px, py -> val col = shader(px, py); if (col != 0) c.set(px, py, col) }
    }

    private companion object {
        /** Tone's cot by the forge ("cot"). */
        const val COT = "cot"

        /** The cot along the left wall under the window, along y, its head against the forge's front (y 3.4). */
        const val COT_A = 1.08f; const val COT_B = 2.08f; const val COT_C = 3.48f; const val COT_D = 5.9f

        /** Tone's horse blanket: charcoal grey wool, a rust stripe. */
        val SOOT_WOOL = intArrayOf(Col.hex(0x7C7874), Col.hex(0x5C5854), Col.hex(0x383432), Col.hex(0xA8583A))

        /** The bellows at rest for the night (half closed), and how much the banked fire's coals are cooler. */
        const val REST = 0.4f
        const val BANKED = 0.52f

        /** The ash over the banked coals. */
        val ASH = Col.hex(0x5E5854)
    }
}
