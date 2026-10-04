package si.lanisce.lani.game.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The small things of the woods below the village, at village scale (a villager is 7 px tall): deer, an owl,
 * mushrooms, flowers, a fallen log, the pond's reeds and ducks, a hunter's high seat, a woodcutter's stack, a
 * charcoal pile, fireflies; and the marks practice leaves: stumps and saplings, berry bushes picked bare, the
 * rocks quarried away. Screen-space sprites take a canvas point (the bottom centre) and are drawn under the
 * context's sprite zoom at the higher details; the built things take world cells like the buildings.
 */
internal class WoodsPainter(private val s: SceneCtx) {
    private val c get() = s.canvas
    private val t get() = s.time
    private val env get() = s.env
    private val k get() = s.k

    /** Character-art sprite whose bottom row sits just above [by], centred on [bx]; '.' is transparent. */
    private fun sprite(bx: Int, by: Int, rows: Array<String>, flip: Boolean, pal: (Char) -> Int) {
        val h = rows.size
        val w = rows[0].length
        for ((r, row) in rows.withIndex()) for (j in row.indices) {
            val ch = row[j]
            if (ch == '.') continue
            val x = if (flip) bx + w / 2 - j else bx - w / 2 + j
            c.set(x, by - h + r, pal(ch))
        }
    }

    // ------------------------------------------------------------------ animals

    /** A roe deer, head to the left ([flip] turns it), grazing or looking up. Greyer in its winter coat. */
    fun deer(bx: Int, by: Int, flip: Boolean, graze: Boolean) {
        val coat = if (env.snow) Col.hex(0x8C7A62) else Col.hex(0xA8784A)
        val coatL = if (env.snow) Col.hex(0xA89A82) else Col.hex(0xC89A68)
        val coatD = if (env.snow) Col.hex(0x625442) else Col.hex(0x7A5230)
        val rows = if (graze) arrayOf(
            "........",
            "........",
            "..LLLLL.",
            ".nBBBBBw",
            "hnBBBBB.",
            "h.d..d..",
            "x.d..d..",
        ) else arrayOf(
            "hh......",
            "xn......",
            ".nLLLLL.",
            ".BBBBBBw",
            ".BBBBBB.",
            "..d..d..",
            "..d..d..",
        )
        sprite(bx, by, rows, flip) {
            when (it) { 'L' -> coatL; 'w' -> Col.hex(0xF4EAD8); 'x' -> Col.hex(0x3A2418); 'd', 'n' -> coatD; else -> coat }
        }
    }

    /** A fawn: half the size, white spots. */
    fun fawn(bx: Int, by: Int, flip: Boolean) {
        val coat = if (env.snow) Col.hex(0x8C7A62) else Col.hex(0xB0804E)
        val coatD = if (env.snow) Col.hex(0x625442) else Col.hex(0x7A5230)
        sprite(bx, by, arrayOf("hh...", "xnLs.", ".BsBw", ".d.d.", ".d.d."), flip) {
            when (it) { 'L' -> Col.hex(0xC89A68); 's' -> Col.hex(0xF4EAD8); 'w' -> Col.hex(0xF4EAD8); 'x' -> Col.hex(0x3A2418); 'd', 'n' -> coatD; else -> coat }
        }
    }

    /** An owl on a roof ridge at night: ear tufts and two glowing eyes. */
    fun owl(bx: Int, by: Int) {
        val brown = Col.hex(0x6E5238)
        val belly = Col.hex(0xC8A878)
        val blink = sin(t * 0.9 + 1.0) > 0.92
        sprite(bx, by, arrayOf("b.b", "ebe", "bBb", "bBb"), false) {
            when (it) { 'B' -> belly; 'e' -> if (blink) brown else Col.hex(0x3A2A18); else -> brown }
        }
        if (!blink && env.dark > 0.3f) {
            c.penEmissive = true
            c.set(bx - 1, by - 3, Pal.GOLD_L); c.set(bx + 1, by - 3, Pal.GOLD_L)
            c.penEmissive = false
        }
    }

    /** A mallard drifting on the pond, head to the right ([flip] turns it). */
    fun duck(bx: Int, by: Int, flip: Boolean) {
        sprite(bx, by, arrayOf("..h", "bbb"), flip) { if (it == 'h') Col.hex(0x2E6A3A) else Col.hex(0xE8DCC0) }
        c.set(if (flip) bx - 1 else bx + 1, by - 2, Pal.GOLD) // the bill
    }

    // ------------------------------------------------------------------ plants

    /** A fly agaric or (odd seeds) a penny bun, 3 px wide. */
    fun mushroom(bx: Int, by: Int, seed: Int) {
        if (seed % 3 == 0) {
            sprite(bx, by, arrayOf("bbb", ".s.", ".s."), false) { if (it == 'b') Col.hex(0x8A5A32) else Col.hex(0xF0E6D0) }
        } else {
            val spot = (seed ushr 2) % 3
            val cap = StringBuilder("rrr").also { it.setCharAt(spot, 'w') }.toString()
            sprite(bx, by, arrayOf(cap, "rrr", ".s."), false) {
                when (it) { 'w' -> Col.hex(0xFFFFFF); 's' -> Col.hex(0xF0E6D0); else -> Col.hex(0xD8302A) }
            }
        }
    }

    /** A clump of three flowers on stems; spring is white and pink, summer yellow, blue and red. */
    fun flowers(bx: Int, by: Int, seed: Int) {
        val heads = if (env.season == Season.SPRING) intArrayOf(Col.hex(0xFFFFFF), Col.hex(0xF4B8CC), Pal.GOLD)
        else intArrayOf(Pal.GOLD, Col.hex(0x6A7AD8), Col.hex(0xE84A3A), Col.hex(0xFFFFFF))
        for (j in 0..2) {
            val x = bx + j * 2 - 2
            val hgt = 2 + (j + seed) % 2
            c.vline(x, by - hgt, by - 1, Pal.LEAF)
            c.set(x, by - hgt - 1, heads[(j + seed) % heads.size])
        }
    }

    /**
     * A berry bush at the meadow's rim (canvas px, no zoom: the bush scales like the trees), red with berries
     * from summer to autumn unless [berries] is off (picked bare today).
     */
    fun berryBush(bx: Int, by: Int, seed: Int, berries: Boolean) {
        s.trees.bush(bx, by, seed)
        if (!berries) return
        val n = 4 + (seed ushr 3) % 3
        for (j in 0 until n) {
            val x = bx + ((Noise.rnd(seed, j, 1) - 0.5f) * 5f).roundToInt() * k
            val y = by - k - ((1f + Noise.rnd(seed, j, 2) * 2.2f)).roundToInt() * k
            c.block(x, y, k, if (j % 3 == 0) Pal.GERANIUM_D else Pal.GERANIUM)
            if (k >= 3) c.set(x, y, Col.hex(0xF07A80))
        }
    }

    /** A fresh stump where a tree was felled: the cut trunk, rings on top, chips and a split log about (under sprite zoom). */
    fun stump(bx: Int, by: Int, seed: Int, fresh: Boolean) {
        val ax = bx; val ay = by
        s.sprite(ax, ay) {
            c.fillRect(ax - 1, ay - 3, 3, 3, Pal.LOG_M)
            c.vline(ax + 1, ay - 3, ay - 1, Pal.LOG_D)
            c.hline(ax - 1, ax + 1, ay - 3, Pal.WOOD_L)
            c.set(ax, ay - 3, Pal.WOOD_M)
            if (fresh) {
                c.set(ax + 3, ay - 1, Pal.WOOD_L); c.set(ax - 3, ay - 1, Col.hex(0xE0C48A)); c.set(ax + 2, ay - 2, Pal.WOOD_L)
                if ((seed ushr 2) % 2 == 0) c.hline(ax + 2, ax + 4, ay, Pal.LOG_L) else c.hline(ax - 4, ax - 2, ay, Pal.LOG_L)
            }
        }
        if (k >= 2) { c.hline(ax - k + 1, ax + k - 1, ay - 3 * k + 1, Pal.WOOD_M); c.set(ax, ay - 3 * k + 1, Pal.LOG_D) } // rings
    }

    /** A sapling: a stem with two leaves and a bud (under sprite zoom). */
    fun sapling(bx: Int, by: Int, seed: Int) {
        val green = if (env.season == Season.AUTUMN) Col.hex(0xC49B2A) else Pal.LEAF
        val light = if (env.season == Season.AUTUMN) Col.hex(0xECC23E) else Col.hex(0x6DBF4A)
        s.sprite(bx, by) {
            c.vline(bx, by - 4, by - 1, Col.hex(0x5A7A38))
            c.set(bx - 1, by - 3, green); c.set(bx + 1, by - 4, green)
            c.set(bx - 2, by - 3, light); c.set(bx + 2, by - 4, light)
            c.set(bx, by - 5, light)
            if (env.snow) c.set(bx, by - 6, Pal.SNOW_L)
        }
    }

    /** A fallen trunk lying along +x, mossy, its cut end to the right. World cells. */
    fun log(x: Float, y: Float) {
        val i = s.iso
        i.box(x, y, 0f, 2.4f, 0.75f, 2.6f, Pal.LOG_L, Pal.LOG_M, Pal.WOOD_L)
        i.px(x + 2.4f, y + 0.4f, 1.3f, Pal.LOG_D) // the heart of the cut end
        i.line(x + 0.2f, y + 0.75f, 1.2f, x + 2.2f, y + 0.75f, 1.2f, Pal.LOG_D) // a bark seam
        if (k >= 2) { i.line(x + 0.1f, y + 0.75f, 2.0f, x + 2.3f, y + 0.75f, 2.0f, Pal.LOG_L); i.line(x + 0.3f, y + 0.75f, 0.5f, x + 2.1f, y + 0.75f, 0.5f, Pal.LOG_D) }
        if (env.snow) i.top(x, y, 2.6f, 2.4f, 0.75f, Pal.SNOW_L)
        else {
            i.px(x + 0.5f, y + 0.25f, 2.6f, Pal.LEAF); i.px(x + 1.3f, y + 0.5f, 2.6f, Pal.LEAF); i.px(x + 0.9f, y + 0.2f, 2.6f, Col.hex(0x5C9A48))
            i.px(x + 1.6f, y + 0.75f, 1.6f, Col.hex(0xE0B070)) // a bracket fungus
        }
    }

    /** Reeds on the pond's bank, swaying a little; dry and pale from late autumn. */
    fun reeds(bx: Int, by: Int, seed: Int) {
        val dry = env.snow || env.season == Season.AUTUMN && env.month == 11
        val stem = if (dry) Col.hex(0xB8A470) else if (env.season == Season.AUTUMN) Col.hex(0x9A9A44) else Col.hex(0x4E8A3A)
        val sway = (sin(t * 1.4 + seed) * 0.9).roundToInt()
        for (j in 0 until 4) {
            val x = bx + j * 2 - 3
            val hgt = 3 + (Noise.hash(seed, j) ushr 3) % 3
            c.vline(x, by - hgt, by - 1, stem)
            if (j % 2 == 0) { c.set(x + sway, by - hgt - 2, Pal.WOOD_D); c.set(x + sway, by - hgt - 1, Pal.WOOD_D) } // a cattail
            else c.set(x + sway, by - hgt - 1, stem)
        }
    }

    fun lilyPad(bx: Int, by: Int, bloom: Boolean) {
        c.set(bx, by, Pal.LEAF); c.set(bx + 1, by, Pal.LEAF); c.set(bx, by - 1, Col.hex(0x5AAA48))
        if (bloom) c.set(bx + 1, by - 1, Col.hex(0xF4B8CC))
    }

    // ------------------------------------------------------------------ built things

    /** A hunter's high seat (lovska preža): four legs, a boarded hide with a little roof, a ladder up the left side. */
    fun highSeat(x: Float, y: Float) {
        val i = s.iso
        val z = 7f
        i.post(x, y, 0f, z, Pal.WOOD_D); i.post(x + 1.2f, y, 0f, z, Pal.WOOD_D)
        i.post(x, y + 1.2f, 0f, z, Pal.WOOD_D); i.post(x + 1.2f, y + 1.2f, 0f, z, Pal.WOOD_D)
        i.line(x + 1.2f, y, 1.5f, x + 1.2f, y + 1.2f, 5.5f, Pal.WOOD_M) // a cross brace
        i.box(x - 0.15f, y - 0.15f, z, 1.5f, 1.5f, 3f, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_D)
        if (k >= 2) for (zz in intArrayOf(1, 2)) i.line(x - 0.15f, y + 1.35f, z + zz, x + 1.35f, y + 1.35f, z + zz, Pal.WOOD_M) // the boards
        i.post(x + 1.2f, y, z + 3f, z + 5.5f, Pal.WOOD_D); i.post(x + 1.2f, y + 1.2f, z + 3f, z + 5.5f, Pal.WOOD_D); i.post(x, y + 1.2f, z + 3f, z + 5.5f, Pal.WOOD_D)
        i.gableX(x - 0.35f, y - 0.35f, z + 5.5f, 1.9f, 1.9f, 1.6f, Pal.SHINGLE_L, Pal.SHINGLE_M, Pal.SHINGLE_D, Pal.SHINGLE_D)
        // the ladder: two rails from the ground to the hide's floor, three rungs
        i.line(x + 0.25f, y + 2.7f, 0f, x + 0.25f, y + 1.35f, z, Pal.WOOD_L)
        i.line(x + 0.85f, y + 2.7f, 0f, x + 0.85f, y + 1.35f, z, Pal.WOOD_L)
        for (j in 1..3) i.line(x + 0.25f, y + 2.7f - j * 0.34f, j * z / 4f, x + 0.85f, y + 2.7f - j * 0.34f, j * z / 4f, Pal.WOOD_M)
        if (env.snow) i.line(x - 0.35f, y + 0.6f, z + 7.1f, x + 1.55f, y + 0.6f, z + 7.1f, Pal.SNOW_L)
    }

    /** The top of the high seat's roof, where the owl sits. */
    fun highSeatTop(x: Float, y: Float): IntArray = intArrayOf(s.iso.ix(x + 0.6f, y + 0.6f), s.iso.iy(x + 0.6f, y + 0.6f, 14.1f))

    /**
     * A woodcutter's stack: split logs piled between stakes, a chopping block with the axe in it, a few logs
     * about; taller and with more logs lying around for every tree felled lately ([felled]).
     */
    fun woodStack(x: Float, y: Float, felled: Int = 0) {
        val i = s.iso
        val extra = min(felled, 8)
        val h = 5f + extra * 0.5f
        i.box(x, y, 0f, 2.2f, 1f, h, Pal.LOG_M, Pal.LOG_L, Pal.LOG_D)
        // cut ends on the right face, bark rows on the left face, log tops above
        for (zz in 0 until h.toInt()) for (j in 0 until 2) {
            i.px(x + 2.2f, y + 0.25f + j * 0.5f, zz + 0.5f, if ((zz + j) % 2 == 0) Pal.WOOD_L else Pal.LOG_D)
            if (k >= 2) c.set(i.ix(x + 2.2f, y + 0.25f + j * 0.5f) + k / 2, i.iy(x + 2.2f, y + 0.25f + j * 0.5f, zz + 0.5f) + k / 2, if ((zz + j) % 2 == 0) Pal.WOOD_M else Pal.LOG_M) // the heart of each cut end
        }
        for (zz in 1 until h.toInt() step 2) i.line(x, y + 1f, zz + 0.5f, x + 2.2f, y + 1f, zz + 0.5f, Pal.LOG_D)
        i.line(x + 0.5f, y, h, x + 0.5f, y + 1f, h, Pal.LOG_D); i.line(x + 1.4f, y, h, x + 1.4f, y + 1f, h, Pal.LOG_D)
        i.post(x + 2.2f, y + 1f, 0f, h + 1f, Pal.WOOD_D); i.post(x, y + 1f, 0f, h + 1f, Pal.WOOD_D) // the stakes
        // the chopping block, the axe in it
        i.box(x + 0.7f, y + 1.9f, 0f, 0.7f, 0.7f, 2f, Pal.WOOD_L, Pal.LOG_M, Pal.LOG_D)
        val hx = i.ix(x + 1.05f, y + 2.25f); val hy = i.iy(x + 1.05f, y + 2.25f, 2f)
        s.sprite(hx, hy) { c.line(hx, hy - 1, hx + 3, hy - 5, Pal.WOOD_M); c.set(hx - 1, hy - 1, Pal.STONE_L); c.set(hx, hy - 2, Pal.STONE_M) }
        i.px(x + 2.7f, y + 1.7f, 0.5f, Pal.LOG_L); i.px(x + 2.9f, y + 1.9f, 0.5f, Pal.LOG_M); i.px(x - 0.3f, y + 2.1f, 0.5f, Pal.LOG_L)
        // more split logs lying about with recent fellings
        for (j in 0 until min(extra, 5)) {
            val u = Noise.rnd(j, 71); val v = Noise.rnd(j, 72)
            val lx = x - 0.6f + u * 3.4f; val ly = y + 2.6f + v * 0.9f
            i.px(lx, ly, 0.5f, if (j % 2 == 0) Pal.LOG_L else Pal.WOOD_L); i.px(lx + 0.25f, ly, 0.5f, Pal.LOG_M)
        }
        if (env.snow) i.top(x, y, h, 2.2f, 1f, Pal.SNOW_L)
    }

    /**
     * The rock cluster at the clearing's edge (canvas px, the point in front of it): three rocks, fewer as stone
     * is quarried ([quarried] lately), and behind them a quarry face that grows with it: a cut wall of stone,
     * a heap of blocks, a pick left leaning, chips when it was worked today ([fresh]).
     */
    fun rocks(ax: Int, ay: Int, quarried: Int, fresh: Boolean) {
        val q = min(quarried, 8)
        if (q >= 1) {
            // the quarry face: stone courses stepping back, wider and taller with every load taken
            val w = 5 + q; val h = 3 + (q + 1) / 2
            s.sprite(ax, ay) {
                c.fillRect(ax - w, ay - 6 - h, w * 2, h, Pal.STONE_M)
                for (yy in 0 until h step 2) c.hline(ax - w, ax + w - 1, ay - 6 - h + yy, Pal.STONE_D)
                for (yy in 1 until h step 2) c.hline(ax - w + 1 + (yy % 4), ax + w - 2, ay - 6 - h + yy, Pal.STONE_X.let { if (yy % 4 == 1) Pal.STONE_L else it })
                c.hline(ax - w, ax + w - 1, ay - 7 - h, Pal.STONE_L)
                c.fillRect(ax - w - 1, ay - 6 - h + 1, 1, h - 1, Pal.STONE_D)
                // cut blocks stacked at the foot
                if (q >= 2) { c.fillRect(ax + w - 5, ay - 8, 4, 2, Pal.STONE_L); c.fillRect(ax + w - 4, ay - 10, 3, 2, Pal.STONE_M); c.set(ax + w - 5, ay - 8, Pal.STONE_D) }
                // a pick leaning on the face
                c.line(ax - w + 2, ay - 6, ax - w + 4, ay - 10, Pal.WOOD_M); c.hline(ax - w + 3, ax - w + 5, ay - 11, Pal.STONE_X)
                if (fresh) { c.set(ax - 3, ay - 5, Pal.STONE_L); c.set(ax + 2, ay - 6, Pal.STONE_L); c.set(ax, ay - 4, Col.hex(0xD8D2C4)) }
            }
        }
        if (q < 4) s.trees.rock(ax - 5 * k, ay - 2 * k, 9)
        if (q < 2) s.trees.rock(ax + 4 * k, ay - 3 * k, 70)
        if (q < 1) s.trees.rock(ax, ay - 5 * k, 133)
        else if (q < 6) s.trees.rock(ax + 1 * k, ay - 4 * k, 134 + q) // a smaller one left of the cluster
    }

    /** A charcoal pile (oglarska kopa): a turfed dome with charcoal showing through, smouldering at the top. */
    fun charcoalPile(bx: Int, by: Int) {
        val rx = 7f; val ry = 6.5f
        val black = Col.hex(0x2A2624)
        for (y in (by - ry).toInt()..by - 1) {
            val v = (by - y) / ry
            val half = rx * sqrt((1f - v * v).coerceAtLeast(0f))
            for (x in (bx - half).toInt()..(bx + half).toInt()) {
                val rel = (x + 0.5f - bx) / max(half, 1f)
                var col = if (rel < -0.3f) Pal.SOIL_L else if (rel < 0.45f) Pal.SOIL_M else Pal.SOIL_D
                if (Noise.rnd(x, y, 44) < 0.18f || y == by - 1) col = black
                c.set(x, y, col)
            }
        }
        c.penEmissive = true
        c.set(bx, (by - ry).toInt(), Col.hex(0xE8501A))
        if (env.dark > 0.2f) {
            c.set(bx - 3, by - 2, Pal.FLAME[3]); c.set(bx + 4, by - 3, Pal.FLAME[3]); c.set(bx + 1, by - 4, Pal.FLAME[3])
            s.light(bx.toFloat(), by - 3f * k, 12f, 0.35f)
        }
        c.penEmissive = false
        // the burner's stack of wood beside it
        c.fillRect(bx + 8, by - 3, 4, 3, Pal.LOG_M); c.hline(bx + 8, bx + 11, by - 4, Pal.LOG_L); c.set(bx + 9, by - 2, Pal.LOG_D)
        if (env.snow) c.hline(bx + 8, bx + 11, by - 5, Pal.SNOW_L)
    }

    // ------------------------------------------------------------------ light

    /** Fireflies over an ellipse of ground on summer nights: a few blinking, drifting points of light. */
    fun fireflies(cx: Float, cy: Float, rx: Float, ry: Float, n: Int, seed: Int) {
        val a = ((env.dark - 0.45f) / 0.3f).coerceIn(0f, 1f)
        if (a <= 0f) return
        c.penEmissive = true
        for (j in 0 until n) {
            val ang = Noise.rnd(j, seed, 1) * 2 * PI
            val r = sqrt(Noise.rnd(j, seed, 2)) * 0.95f
            val x = cx + cos(ang).toFloat() * rx * r + sin(t * (0.5 + Noise.rnd(j, seed, 3) * 0.5) + j).toFloat() * 3f * k
            val y = cy + sin(ang).toFloat() * ry * r - (2f + Noise.rnd(j, seed, 4) * 6f) * k + cos(t * 0.7 + j * 1.3).toFloat() * 1.5f * k
            if (sin(t * (1.6 + Noise.rnd(j, seed, 5)) + j * 2.1) < 0.45) continue
            c.blend(x.toInt(), y.toInt(), Col.hex(0xE0FF80), a)
            s.light(x, y, 4f, 0.3f * a)
        }
        c.penEmissive = false
    }
}
