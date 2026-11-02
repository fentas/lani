package si.lanisce.lani.game.render

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How far a landmark is built: [SITE] after the first step (pegs and a string round the footprint, a heap of timber or
 * stone), [FRAME] for the first half of the steps in between (half-built: the walls half up, the frame standing), then
 * [SCAFFOLD] (nearly there, scaffolding round it, the roof or the finish still missing), and [DONE] when the last step
 * is done.
 */
internal enum class BuildLook {
    SITE, FRAME, SCAFFOLD, DONE;

    companion object {
        fun of(stage: Int, stages: Int): BuildLook = when {
            stage >= stages -> DONE
            stage <= 1 -> SITE
            // the steps between the first and the last: the first half (rounded up) half-built, the rest scaffolded
            stage - 2 < (stages - 1) / 2 -> FRAME
            else -> SCAFFOLD
        }
    }
}

/**
 * The landmarks of the village projects (see [LandmarkLayout] for where they stand), in the buildings' isometric voxel
 * style: world cells through [Iso], parts ordered back to front by hand, the buildings' roofs, stone and timber. At a
 * closer look ([fine], k ≥ 2; [finest], k = 3) they gain what the extra pixels allow: mortar, planks, tile rows, the
 * figures on the painted hive fronts, the clock's numerals. Lamps, candles and the clock's faces light up at dusk;
 * roofs and tops take snow in winter; vines, flowers and hay follow the months.
 */
internal class LandmarkPainter(private val s: SceneCtx, private val b: BuildingPainter) {
    private val i get() = s.iso
    private val c get() = s.canvas
    private val env get() = s.env
    private val k get() = s.k
    private val fine get() = s.k >= 2
    private val finest get() = s.k >= 3
    private val lit get() = env.windows > 0.35f
    private val warm get() = !env.snow && env.month in 4..9

    fun draw(sp: LandmarkSpot) {
        val m = sp.landmark
        val look = BuildLook.of(m.stage, m.stages)
        val x = sp.x; val y = sp.y; val w = sp.w; val d = sp.d
        if (look == BuildLook.SITE && m.id != Crossing.BRIDGE) { site(x, y, w, d, LandmarkLayout.shapeOf(m.id).stone); return }
        when (m.id) {
            "mlaj" -> mlaj(x, y, look)
            Crossing.BRIDGE -> most(x, y, w, d, m.stage, m.stages)
            "mlin" -> mlin(x, y, w, d, look)
            "balinisce" -> balinisce(x, y, w, d, look)
            "kapelica" -> kapelica(x, y, look)
            "vinograd" -> vinograd(x, y, w, d, look)
            "cebelji_travnik" -> travnik(x, y, w, d, look)
            "gasilski_dom" -> gasilskiDom(x, y, look)
            "igrisce" -> igrisce(x, y, look)
            "vodnjak_na_trgu" -> vodnjak(x, y, look)
            "toplar" -> toplar(x, y, look)
            "razgledni_stolp" -> stolp(x, y, look)
            "mestna_ura" -> ura(x, y, look)
            else -> monument(x, y, w, d, look)
        }
    }

    // ---------------------------------------------------------------- the building site

    /** The first step: the footprint pegged out with a string, and the material heaped in the middle. */
    private fun site(x: Float, y: Float, w: Float, d: Float, stone: Boolean) {
        stakes(x + 0.15f, y + 0.15f, w - 0.3f, d - 0.3f)
        val hx = x + w / 2f - 0.7f; val hy = y + d / 2f - 0.55f
        if (stone) stoneHeap(hx + 0.1f, hy) else timberHeap(hx, hy)
    }

    /** Pegs at the corners and a string between them (on the ground: no outline, like a line drawn in the grass). */
    private fun stakes(x: Float, y: Float, w: Float, d: Float) {
        val string = if (env.snow) Pal.WOOD_X else Pal.CANVAS_L
        i.post(x, y, 0f, 3f, Pal.WOOD_L)
        ground {
            i.line(x, y, 2f, x + w, y, 2f, string); i.line(x, y, 2f, x, y + d, 2f, string)
            i.line(x, y + d, 2f, x + w, y + d, 2f, string); i.line(x + w, y, 2f, x + w, y + d, 2f, string)
        }
        i.post(x + w, y, 0f, 3f, Pal.WOOD_L); i.post(x, y + d, 0f, 3f, Pal.WOOD_L); i.post(x + w, y + d, 0f, 3f, Pal.WOOD_L)
        if (fine) for (p in arrayOf(floatArrayOf(x, y), floatArrayOf(x + w, y), floatArrayOf(x, y + d), floatArrayOf(x + w, y + d))) {
            c.set(i.ix(p[0], p[1]), i.iy(p[0], p[1], 3f), Pal.WOOD_M) // the pegs' cut tops
        }
    }

    /** Squared beams stacked three high, their cut ends to the right, a round log in front. */
    private fun timberHeap(x: Float, y: Float) {
        val h = 3f
        i.box(x, y, 0f, 1.4f, 0.7f, h, Pal.WOOD_L, Pal.WOOD_M, END)
        for (z in 1..2) {
            i.line(x, y + 0.7f, z.toFloat(), x + 1.4f, y + 0.7f, z.toFloat(), Pal.WOOD_D)
            i.line(x + 1.4f, y, z.toFloat(), x + 1.4f, y + 0.7f, z.toFloat(), Pal.WOOD_M)
        }
        i.line(x + 1.4f, y + 0.35f, 0f, x + 1.4f, y + 0.35f, h, Pal.WOOD_M)
        if (fine) for (z in 0..2) for (j in 0..1) c.set(i.ix(x + 1.4f, y + 0.17f + j * 0.35f), i.iy(x + 1.4f, y + 0.17f + j * 0.35f, z + 0.5f), Pal.WOOD_M) // the heart of each end
        if (env.snow) i.top(x, y, h, 1.4f, 0.7f, Pal.SNOW_L)
        i.box(x + 0.15f, y + 0.85f, 0f, 1.2f, 0.3f, 1f, Pal.LOG_L, Pal.LOG_M, Pal.WOOD_L)
    }

    /** Cut blocks of stone, one course on another, a loose one beside. */
    private fun stoneHeap(x: Float, y: Float) {
        i.box(x, y, 0f, 1.2f, 0.8f, 2f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        i.line(x + 0.6f, y + 0.8f, 0f, x + 0.6f, y + 0.8f, 2f, Pal.STONE_D)
        i.line(x + 1.2f, y + 0.4f, 0f, x + 1.2f, y + 0.4f, 2f, Pal.STONE_X)
        i.box(x + 0.2f, y + 0.1f, 2f, 0.7f, 0.55f, 1.5f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        if (fine) { i.line(x, y + 0.8f, 1f, x + 1.2f, y + 0.8f, 1f, Pal.STONE_D); i.line(x + 1.2f, y, 1f, x + 1.2f, y + 0.8f, 1f, Pal.STONE_X) }
        if (env.snow) { i.top(x, y, 2f, 1.2f, 0.8f, Pal.SNOW_L); i.top(x + 0.2f, y + 0.1f, 3.5f, 0.7f, 0.55f, Pal.SNOW_L) }
        i.box(x + 0.9f, y + 0.95f, 0f, 0.45f, 0.35f, 1f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
    }

    /** Scaffolding round a box: the back pole (drawn before the box). */
    private fun scaffoldBack(x: Float, y: Float, top: Float) = i.post(x - 0.2f, y - 0.2f, 0f, top, Pal.WOOD_D)

    /** Scaffolding round a box: the poles, ledgers and braces in front of it, a board along the top. */
    private fun scaffoldFront(x: Float, y: Float, w: Float, d: Float, top: Float) {
        val x0 = x - 0.2f; val y0 = y - 0.2f; val x1 = x + w + 0.2f; val y1 = y + d + 0.2f
        val ledger = Col.mix(Pal.WOOD_L, Pal.WOOD_M, 0.3f)
        i.post(x1, y0, 0f, top, Pal.WOOD_D); i.post(x0, y1, 0f, top, Pal.WOOD_D)
        var z = 4f
        while (z < top) { i.line(x0, y1, z, x1, y1, z, ledger); i.line(x1, y0, z, x1, y1, z, Pal.WOOD_M); z += 5f }
        i.line(x0, y1, 0.5f, x1, y1, top - 0.5f, Pal.WOOD_D)
        i.line(x1, y1, 0.5f, x1, y0, top - 0.5f, Pal.WOOD_X)
        i.post(x1, y1, 0f, top, Pal.WOOD_D)
        i.line(x0, y1, top, x1, y1, top, Pal.WOOD_L); i.line(x1, y0, top, x1, y1, top, Pal.WOOD_L)
        if (fine) i.line(x0, y1, top - 0.5f, x1, y1, top - 0.5f, Pal.WOOD_M)
    }

    // ---------------------------------------------------------------- helpers

    /** A soft shadow under a footprint, a little to the lower right like the buildings'. */
    private fun shadow(x: Float, y: Float, w: Float, d: Float) {
        c.polyBegin()
        c.polyAdd(i.sx(x + 0.25f, y + 0.1f), i.sy(x + 0.25f, y + 0.1f, 0f))
        c.polyAdd(i.sx(x + w + 0.35f, y + 0.1f), i.sy(x + w + 0.35f, y + 0.1f, 0f))
        c.polyAdd(i.sx(x + w + 0.35f, y + d + 0.2f), i.sy(x + w + 0.35f, y + d + 0.2f, 0f))
        c.polyAdd(i.sx(x + 0.25f, y + d + 0.2f), i.sy(x + 0.25f, y + d + 0.2f, 0f))
        c.polyScan { px, py -> c.shadeGround(px, py, 0.82f) }
    }

    /** A pole or a leg between two points, a nominal pixel thick. */
    private fun rod(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, col: Int) {
        val ax = i.ix(x1, y1); val ay = i.iy(x1, y1, z1); val bx = i.ix(x2, y2); val by = i.iy(x2, y2, z2)
        for (dx in 0 until k) c.line(ax + dx, ay, bx + dx, by, col)
    }

    private inline fun glow(block: () -> Unit) { val was = c.penEmissive; c.penEmissive = true; block(); c.penEmissive = was }

    /** Drawn as ground: no outline round it and not tappable (strings, flowers, the water under an arch). */
    private inline fun ground(block: () -> Unit) { val was = c.penId; c.penId = 0; block(); c.penId = was }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    // ---------------------------------------------------------------- Mlaj · the maypole

    /** A tall spruce stripped of its bark but for the green crown, a wreath below the crown, ribbons fluttering from it. */
    private fun mlaj(x: Float, y: Float, look: BuildLook) {
        val cx = x + 1f; val cy = y + 1f
        val top = 27f
        stones(cx, cy, back = true)
        when (look) {
            BuildLook.FRAME -> raising(cx, cy)
            BuildLook.SCAFFOLD -> {
                pole(cx, cy, top)
                ring(cx - 0.45f, cy + 0.55f) // the wreath being bound on the ground
                ladder(cx, cy)
            }
            else -> when (s.look.maypole) {
                Maypole.RIBBONS -> ribbonPole(cx, cy, top - 2f)
                else -> {
                    val cuccagna = s.look.maypole == Maypole.CUCCAGNA
                    // the greasy pole's wheel of prizes hangs just under the wreath, its back half behind the pole
                    if (cuccagna) wheel(cx, cy, top - 8f, front = false)
                    wreath(cx, cy, top - 6f, front = false)
                    pole(cx, cy, top)
                    if (cuccagna) for (z in intArrayOf(4, 9, 14, 19)) c.set(i.ix(cx + 0.02f, cy + 0.13f), i.iy(cx + 0.02f, cy + 0.13f, z.toFloat()), Col.mix(STRIPPED_L, Col.hex(0xFFFFFF), 0.5f)) // the grease's shine
                    wreath(cx, cy, top - 6f, front = true)
                    ribbons(cx, cy, top - 6f)
                    if (cuccagna) { wheel(cx, cy, top - 8f, front = true); prizes(cx, cy, top - 8f) }
                    if (s.look.maypole == Maypole.MAIBAUM) kraxel(cx, cy, top - 7f)
                }
            }
        }
        stones(cx, cy, back = false)
    }

    /**
     * The prizes of Friuli's greasy pole (l'albero della cuccagna), as In piazza hangs them on the wheel under its wreath: a
     * salami, a wheel of cheese, a white bag of sweets, by turns, swaying on their strings.
     */
    private fun prizes(cx: Float, cy: Float, wz: Float) {
        val prizes = intArrayOf(Col.hex(0x8A2A2A), Col.hex(0xF0CC5A), Col.hex(0xF4F0E6), Col.hex(0x8A2A2A), Col.hex(0xF0CC5A), Col.hex(0xF4F0E6))
        for (j in prizes.indices) {
            val a = j * 2 * PI / prizes.size + 0.4
            val px = cx + cos(a).toFloat() * 0.62f; val py = cy + sin(a).toFloat() * 0.62f
            if (px + py - cx - cy < 0f) continue // the ones behind the pole hang out of sight
            val ax = i.ix(px, py); val ay = i.iy(px, py, wz)
            val sway = (sin(s.time * 1.8 + j) * 0.6).roundToInt()
            val col = prizes[j]; val dark = Col.scale(col, 0.72f)
            s.sprite(ax, ay) {
                c.set(ax, ay + 1, ROPE)
                c.set(ax + sway, ay + 2, col); c.set(ax + sway, ay + 3, col); c.set(ax + sway, ay + 4, dark)
                if (j % 3 == 1) { c.set(ax + sway + 1, ay + 3, dark); c.set(ax + sway - 1, ay + 3, col) } // the cheese is round
            }
        }
    }

    /** Carinthia's Kraxler's prize under the Maibaum's wreath at [z] (Unter dem Maibaum's): two strings of Würstel, a slab of Speck. */
    private fun kraxel(cx: Float, cy: Float, z: Float) {
        val ax = i.ix(cx, cy + 0.15f); val ay = i.iy(cx, cy + 0.15f, z)
        val sway = (sin(s.time * 1.5) * 0.6).roundToInt()
        val wurst = Col.hex(0x9A4A2E); val wurstD = Col.hex(0x6E3020); val speck = Col.hex(0xE8C8B0); val speckD = Col.hex(0xB0644A)
        s.sprite(ax, ay) {
            for (d in intArrayOf(-1, 2)) for (q in 0..3) c.set(ax + d + if (q > 1) sway else 0, ay + q, if (q % 2 == 0) wurst else wurstD)
            c.set(ax + sway, ay + 2, speck); c.set(ax + sway, ay + 3, speckD); c.set(ax + 1 + sway, ay + 2, speckD)
        }
    }

    /** The greasy pole's wheel round it at [z]: its back or [front] half, a ring of wood with its green garland. */
    private fun wheel(cx: Float, cy: Float, z: Float, front: Boolean) {
        for (j in 0 until 16) {
            val a = j * 2 * PI / 16
            val ox = cos(a).toFloat() * 0.62f; val oy = sin(a).toFloat() * 0.62f
            if ((ox + oy >= 0f) != front) continue
            i.px(cx + ox, cy + oy, z, if (j % 2 == 0) Pal.WOOD_L else Pal.WOOD_M)
            i.px(cx + ox, cy + oy, z + 1f, if (j % 3 == 0 && !env.snow) Flag.FRIULI_GOLD else Pal.LEAF)
        }
    }

    /**
     * The Lake District's maypole for the children's dance, as the green's: a white pole with a red stripe winding up it, a
     * garland at its top, and eight long ribbons, red, blue, yellow and green, hanging from it to a child's height, swaying.
     */
    private fun ribbonPole(cx: Float, cy: Float, top: Float) {
        val cols = s.look.festive.maypole
        val n = cols.size * 2
        // the ribbons behind the pole first
        for (pass in 0..1) {
            if (pass == 1) {
                i.box(cx - 0.11f, cy - 0.11f, 0f, 0.22f, 0.22f, top, Pal.FLAG_WHITE, Pal.FLAG_WHITE, Col.hex(0xD8D6CE))
                var z = 0.5f
                while (z < top - 0.5f) { i.px(cx - 0.05f, cy + 0.11f, z, Flag.EN_RED); if (fine) i.px(cx + 0.11f, cy - 0.05f, z + 1f, Col.scale(Flag.EN_RED, 0.8f)); z += 2f }
                i.px(cx, cy, top + 1f, Pal.GOLD)
            }
            for (j in 0 until n) {
                val a = j * 2 * PI / n + s.time * 0.25
                val ox = cos(a).toFloat() * 0.9f; val oy = sin(a).toFloat() * 0.9f
                if ((ox + oy >= 0f) != (pass == 1)) continue
                val sway = sin(s.time * 1.6 + j).toFloat() * 0.08f
                i.line(cx, cy, top - 0.5f, cx + ox + sway, cy + oy - sway, 5f, cols[j % cols.size])
            }
        }
        wreath(cx, cy, top - 0.5f, front = true)
    }

    private fun stones(cx: Float, cy: Float, back: Boolean) {
        for (j in 0 until 7) {
            val a = j * 2 * PI / 7 + 0.3
            val ox = cos(a).toFloat() * 0.5f; val oy = sin(a).toFloat() * 0.5f
            if ((ox + oy < 0f) != back) continue
            i.px(cx + ox, cy + oy, 0.5f, if (j % 2 == 0) Pal.STONE_L else Pal.STONE_M)
        }
    }

    private fun pole(cx: Float, cy: Float, top: Float) {
        i.box(cx - 0.12f, cy - 0.12f, 0f, 0.25f, 0.25f, top, STRIPPED_L, STRIPPED_L, STRIPPED_D)
        if (fine) for (z in intArrayOf(5, 11, 17)) c.set(i.ix(cx - 0.05f, cy + 0.13f), i.iy(cx - 0.05f, cy + 0.13f, z.toFloat()), STRIPPED_D) // knots
        s.trees.pine(i.ix(cx, cy), i.iy(cx, cy, top - 2f), 0.62f, 29)
    }

    private fun wreath(cx: Float, cy: Float, z: Float, front: Boolean) {
        for (j in 0 until 16) {
            val a = j * 2 * PI / 16
            val ox = cos(a).toFloat() * 0.55f; val oy = sin(a).toFloat() * 0.55f
            if ((ox + oy >= 0f) != front) continue
            val col = when {
                j % 4 == 1 && !env.snow -> Pal.PAINTED[(j / 4) % 3 * 2]
                j % 2 == 0 -> Pal.LEAF
                else -> GREEN_D
            }
            i.px(cx + ox, cy + oy, z, col)
        }
    }

    private fun ribbons(cx: Float, cy: Float, z: Float) {
        val cols = s.look.festive.maypole
        for (j in cols.indices) {
            val a = -0.6 + j * 0.7
            val px = cx + cos(a).toFloat() * 0.55f; val py = cy + sin(a).toFloat() * 0.55f
            val ax = i.ix(px, py); val ay = i.iy(px, py, z)
            val len = 6 + (j * 2) % 3
            s.sprite(ax, ay) {
                for (t in 1..len) {
                    val sway = (sin(s.time * 2.2 + j * 1.3 + t * 0.6) * t / len * 1.6).roundToInt()
                    c.set(ax + sway, ay + t, cols[j])
                }
            }
        }
    }

    /** A wreath lying on the ground. */
    private fun ring(cx: Float, cy: Float) {
        for (j in 0 until 12) {
            val a = j * 2 * PI / 12
            i.px(cx + cos(a).toFloat() * 0.35f, cy + sin(a).toFloat() * 0.35f, 0.4f, if (j % 3 == 0 && !env.snow) Pal.PAINTED[0] else Pal.LEAF)
        }
    }

    /** The pole half raised: leaning on the forked poles (šrange) that push it up, a rope from its top to a peg. */
    private fun raising(cx: Float, cy: Float) {
        i.top(cx - 0.3f, cy - 0.3f, 0.1f, 0.6f, 0.6f, Pal.SOIL_D) // the hole
        i.box(cx + 0.35f, cy - 0.8f, 0f, 0.5f, 0.4f, 1f, if (env.snow) Pal.SNOW_L else Pal.SOIL_L, Pal.SOIL_M, Pal.SOIL_D) // the dug earth
        val bx = i.ix(cx, cy); val by = i.iy(cx, cy, 0f)
        val tx = bx + 9 * k; val ty = by - 20 * k
        i.post(cx - 0.8f, cy - 0.6f, 0f, 2f, Pal.WOOD_D)
        c.line(tx, ty, i.ix(cx - 0.8f, cy - 0.6f), i.iy(cx - 0.8f, cy - 0.6f, 2f), Pal.CANVAS_M)
        for (dx in 0 until 2 * k) c.line(bx - k + dx, by, tx - k + dx, ty, if (dx < k) STRIPPED_L else STRIPPED_D)
        val p = env.pine
        c.fillEllipse(tx + 2f * k, ty - 1.5f * k, 3.5f * k, 2.2f * k, p[1])
        c.fillEllipse(tx + 1f * k, ty - 2.5f * k, 2f * k, 1.2f * k, if (env.snow) Pal.SNOW_L else p[3])
        c.fillEllipse(tx + 3.5f * k, ty - 0.5f * k, 1.5f * k, 1f * k, p[2])
        // the forked poles, from the ground in front up to the trunk's middle
        val mx = bx + 5 * k; val my = by - 11 * k
        val f1x = i.ix(cx - 0.3f, cy + 0.85f); val f1y = i.iy(cx - 0.3f, cy + 0.85f, 0f)
        val f2x = i.ix(cx + 0.85f, cy + 0.5f); val f2y = i.iy(cx + 0.85f, cy + 0.5f, 0f)
        for (dx in 0 until k) { c.line(f1x + dx, f1y, mx - k + dx, my + k, Pal.WOOD_M); c.line(f2x + dx, f2y, mx + k + dx, my + 3 * k, Pal.WOOD_M) }
        c.block(mx - 2 * k, my, k, Pal.WOOD_M); c.block(mx + 2 * k, my + 2 * k, k, Pal.WOOD_M) // the forks' prongs
    }

    private fun ladder(cx: Float, cy: Float) {
        val a0x = cx + 0.95f; val a0y = cy + 0.1f; val b0x = cx + 0.7f; val b0y = cy + 0.55f
        val a1x = cx + 0.25f; val a1y = cy - 0.05f; val b1x = cx + 0.05f; val b1y = cy + 0.25f; val z1 = 17f
        i.line(a0x, a0y, 0f, a1x, a1y, z1, Pal.WOOD_L); i.line(b0x, b0y, 0f, b1x, b1y, z1, Pal.WOOD_L)
        for (j in 1..5) {
            val t = j / 6f
            i.line(lerp(a0x, a1x, t), lerp(a0y, a1y, t), z1 * t, lerp(b0x, b1x, t), lerp(b0y, b1y, t), z1 * t, Pal.WOOD_M)
        }
    }

    // ---------------------------------------------------------------- Most · the bridge where the road crosses the stream

    /**
     * The village's bridge where the road crosses the stream (see [Crossing]; laid across it along x from bank to bank,
     * [w] long, [d] as wide as the road and a little more): a stone pillar on each bank, a deck of thick planks laid
     * across the way on them, a railing along each side (never across the way): the road goes on at both its ends, and
     * until the deck is laid it fords the stream on stepping stones beside the work. Built as its [stage]s go (of
     * [stages], the six steps of the project): the stream measured (pegs at the deck's corners on both banks, a line over
     * the water along the road), stones gathered on both banks, the two pillars, the long logs from pillar to pillar, the
     * planks (the road goes over it from then on), and finished, the railings.
     */
    private fun most(x: Float, y: Float, w: Float, d: Float, stage: Int, stages: Int) {
        val step = Crossing.step(stage, stages).coerceAtLeast(1)
        val snow = env.snow
        val x1 = x + w; val y1 = y + d
        val kind = s.look.bridge
        if (kind == Bridge.PACKHORSE && step >= 3) { packhorse(x, y, w, d, step); return }
        if (step <= 2) {
            // measured: pegs at the deck's corners on both banks, a line between them over the water along the road
            ground { i.line(x + 0.1f, y + d / 2f, 2f, x1 - 0.1f, y + d / 2f, 2f, if (snow) Pal.WOOD_X else Pal.CANVAS_L) }
            for (px in floatArrayOf(x + 0.1f, x1 - 0.1f)) { i.post(px, y + 0.1f, 0f, 3f, Pal.WOOD_L); i.post(px, y1 - 0.1f, 0f, 3f, Pal.WOOD_L) }
            // the stones gathered for the pillars on both banks, at the road's edge
            if (step == 2) { gathered(x - 0.2f, y1 - 0.85f); gathered(x1 - 0.45f, y1 - 0.85f) }
            return
        }
        // the pillars on the banks, their ends showing either side of the deck's
        for (px in floatArrayOf(x, x1 - PILLAR)) pillar(px, y - 0.12f, d + 0.24f)
        if (step == 4) logs(x, x1, y, d)
        if (step >= 6) rail(x, x1, y)
        if (step >= 5) {
            // the planks across the way, finer closer up (Carinthia's of larch)
            val wood = if (kind == Bridge.LARCH) LARCH else intArrayOf(Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
            i.box(x, y, DECK_UNDER, w, d, DECK - DECK_UNDER, if (snow) Pal.SNOW_L else wood[0], wood[1], wood[2])
            if (!snow) {
                val gap = if (fine) 0.25f else 0.5f
                var px = x + gap
                while (px < x1 - 0.05f) { i.line(px, y, DECK, px, y1, DECK, if (fine) Col.mix(wood[0], wood[1], 0.6f) else wood[1]); px += gap }
            }
        }
        if (step >= 6) rail(x, x1, y1)
    }

    /**
     * The Lake District's packhorse bridge ([Bridge.PACKHORSE]), from its third step (the first two, the beck measured and
     * the stones gathered, as the timber bridge's): the walls on both banks, dry slate in courses; the timber frame between
     * them, the arch's ribs on their props; the arch of slate laid on it, humped over the beck, the water under its round
     * opening, the frame gone; finished, its low parapets along both sides, the way between them.
     */
    private fun packhorse(x: Float, y: Float, w: Float, d: Float, step: Int) {
        val snow = env.snow
        val x1 = x + w; val y1 = y + d
        val ab = ABUTMENT; val s0 = x + ab; val s1 = x1 - ab; val sw = s1 - s0
        fun under(u: Float) = ARCH_FOOT + (ARCH_TOP - ARCH_FOOT) * sin(PI.toFloat() * u)
        fun top(u: Float) = WALL_H + HUMP * sin(PI.toFloat() * u)
        // the far bank's wall
        slateWall(x, y - 0.08f, ab, d + 0.16f)
        if (step == 4) {
            // the timber frame the arch is laid on: its rib along each side, on props from the water
            for (yy in floatArrayOf(y, y1)) {
                val n = 8
                for (j in 0 until n) {
                    val u0 = j / n.toFloat(); val u1 = (j + 1) / n.toFloat()
                    i.line(s0 + sw * u0, yy, under(u0), s0 + sw * u1, yy, under(u1), Pal.WOOD_L)
                }
                for (u in floatArrayOf(0.25f, 0.5f, 0.75f)) i.post(s0 + sw * u, yy, 0f, under(u), Pal.WOOD_M)
            }
        }
        if (step >= 5) {
            // the far parapet, then the arch slice by slice from the far bank, its face toward us
            val n = max(8, (sw * 6 * k).toInt())
            if (step >= 6) for (j in 0 until n) {
                val u = (j + 0.5f) / n
                i.box(s0 + sw * j / n, y, top(u), sw / n + 0.02f, PARAPET, PARAPET_H, if (snow) Pal.SNOW_L else SLATE[0], SLATE[1], SLATE[2])
            }
            for (j in 0 until n) {
                val u = (j + 0.5f) / n
                val zu = under(u); val zt = top(u)
                val xa = s0 + sw * j / n; val dx = sw / n + 0.02f
                i.box(xa, y, zu, dx, d, zt - zu, if (snow) Pal.SNOW_L else SLATE_WAY, SLATE[0], SLATE[1])
                // the round opening on its face: the water in the arch's shade under it, the ring of its stones round it
                i.panelY(xa, y1, 0f, dx, zu, if (snow) Pal.ICE_M else ARCH_SHADE)
                i.line(xa, y1, zu + 0.3f, xa + dx, y1, zu + 0.3f, SLATE[0])
                if (fine && j % 2 == 0) i.px(s0 + sw * u, y1, zu + 0.9f, SLATE[2])
            }
            if (step >= 6) for (j in 0 until n) {
                val u = (j + 0.5f) / n
                i.box(s0 + sw * j / n, y1 - PARAPET, top(u), sw / n + 0.02f, PARAPET, PARAPET_H, if (snow) Pal.SNOW_L else SLATE[0], SLATE[1], SLATE[2])
            }
        }
        // the near bank's wall
        slateWall(s1, y - 0.08f, ab, d + 0.16f)
    }

    /** A wall of dry slate on a bank, [w] along the way, [d] across it: its courses closer up. */
    private fun slateWall(x: Float, y: Float, w: Float, d: Float) {
        i.box(x, y, 0f, w, d, WALL_H, if (env.snow) Pal.SNOW_L else SLATE[0], SLATE[1], SLATE[2])
        if (fine) { var z = 0.7f; while (z < WALL_H - 0.2f) { i.line(x, y + d, z, x + w, y + d, z, SLATE_D); i.line(x + w, y, z, x + w, y + d, z, SLATE_D); z += 0.7f } }
    }

    /**
     * A stone pillar on the bank, from [x] along the road ([PILLAR] thick), [d] across it, as high as the deck's underside and a
     * little more; Friuli's of river cobbles under a course of brick ([Bridge.COBBLES]).
     */
    private fun pillar(x: Float, y: Float, d: Float) {
        if (s.look.bridge == Bridge.COBBLES) {
            // a pier of river cobbles a little wider and higher, its course of brick on top at the deck's corners
            val cx = x - (COBBLE_PIER - PILLAR) / 2f
            b.looks.cobbleBox(cx, y - 0.1f, 0f, COBBLE_PIER, d + 0.2f, COBBLE_PIER_H, 61)
            i.box(cx, y - 0.1f, COBBLE_PIER_H, COBBLE_PIER, d + 0.2f, 0.5f, if (env.snow) Pal.SNOW_L else BRICK_L, BRICK, BRICK_D)
            return
        }
        i.box(x, y, 0f, PILLAR, d, PILLAR_H, if (env.snow) Pal.SNOW_L else Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        if (fine) b.mortar(x, y, 0f, PILLAR, d, PILLAR_H, Pal.STONE_D, Pal.STONE_X)
    }

    /** The long logs from pillar to pillar ([x0] to [x1]) before the planks are laid on them, their cut ends toward us. */
    private fun logs(x0: Float, x1: Float, y: Float, d: Float) {
        for (v in floatArrayOf(0.18f, 0.5f, 0.82f)) {
            val ly = y + d * v - 0.13f
            i.box(x0, ly, DECK_UNDER + 0.1f, x1 - x0, 0.26f, 0.6f, if (env.snow) Pal.SNOW_L else Pal.LOG_L, Pal.LOG_M, END)
        }
    }

    /** A railing along one side ([y]) of the deck, from [x0] to [x1]: posts at its ends and middle standing on the deck, a rail on them. */
    private fun rail(x0: Float, x1: Float, y: Float) {
        val top = DECK + 2.8f
        val larch = s.look.bridge == Bridge.LARCH
        for (px in floatArrayOf(x0 + 0.12f, (x0 + x1) / 2f, x1 - 0.12f)) i.post(px, y, DECK, top, if (larch) LARCH[2] else Pal.WOOD_D)
        i.line(x0 + 0.12f, y, top, x1 - 0.12f, y, top, if (env.snow) Pal.SNOW_L else if (larch) LARCH[0] else Pal.WOOD_L)
        if (fine) i.line(x0 + 0.12f, y, DECK + 1.4f, x1 - 0.12f, y, DECK + 1.4f, if (larch) LARCH[1] else Pal.WOOD_M)
    }

    /** A few stones gathered on the bank: two side by side and one on them. */
    private fun gathered(x: Float, y: Float) {
        i.box(x, y + 0.3f, 0f, 0.3f, 0.3f, 1f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        i.box(x + 0.3f, y + 0.4f, 0f, 0.28f, 0.3f, 1f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        i.box(x + 0.1f, y + 0.35f, 1f, 0.3f, 0.25f, 0.8f, if (env.snow) Pal.SNOW_L else Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
    }

    // ---------------------------------------------------------------- Mlin · the mill on the stream

    /**
     * France's water mill on the village's bank of the stream: a timber house on a stone footing under a shingled roof (the
     * Lake District's of slate stone under slate, as its beck's, [MapLook.slateMill]),
     * a mill race dug off the stream past its front (its end toward the water, -x), the wheel standing in the race.
     * Half-built, the footing with the frame going up on it and the race dug, dry; then the walls under bare rafters in
     * scaffolding, the wheel standing still; finished, the race runs off the stream (+x), the wheel turning with it, a sack
     * of flour waits by the door and the window glows at night; the race frozen in the snow, the wheel stands.
     */
    private fun mlin(x: Float, y: Float, w: Float, d: Float, look: BuildLook) {
        val hx = x + 0.45f; val hy = y + 0.15f; val hw = w - 0.6f; val hd = d - 0.85f
        val done = look == BuildLook.DONE
        // the race: off the stream at the footprint's water end, past the house's front
        val ry = hy + hd + 0.4f
        ground {
            i.top(x - 0.2f, ry - 0.2f, 0f, w - 0.5f, 0.4f, if (done) (if (env.snow) Pal.ICE_M else Pal.WATER_M) else Pal.SOIL_D)
            if (done && !env.snow) for (q in 0 until 3 * k) {
                val u = ((s.time * 0.6 + q * 0.37) % 1.0).toFloat()
                c.set(i.ix(x - 0.1f + u * (w - 0.7f), ry), i.iy(x - 0.1f + u * (w - 0.7f), ry, 0f), Pal.WATER_L)
            }
        }
        shadow(hx, hy, hw, hd)
        if (look == BuildLook.FRAME) {
            i.box(hx, hy, 0f, hw, hd, 2f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
            if (fine) b.mortar(hx, hy, 0f, hw, hd, 2f, Pal.STONE_D, Pal.STONE_X)
            // the timber frame going up on the footing, a brace across its front
            for (px in floatArrayOf(hx, hx + hw)) for (py in floatArrayOf(hy, hy + hd)) i.post(px, py, 2f, 8f, Pal.WOOD_M)
            i.line(hx, hy + hd, 8f, hx + hw, hy + hd, 8f, Pal.WOOD_L); i.line(hx + hw, hy, 8f, hx + hw, hy + hd, 8f, Pal.WOOD_L)
            i.line(hx + 0.1f, hy + hd, 2.5f, hx + hw - 0.1f, hy + hd, 7.5f, Pal.WOOD_D)
            return
        }
        if (!done) scaffoldBack(hx, hy, 11f)
        // the Lake District's of slate stone up to its eaves, as By the beck's (its look's style: the dale's stone)
        val slate = s.look.style?.takeIf { s.look.slateMill }
        if (slate != null) b.looks.stoneBox(hx, hy, 0f, hw, hd, 8f, slate.walls[0], 67)
        else {
            i.box(hx, hy, 0f, hw, hd, 2f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
            if (fine) b.mortar(hx, hy, 0f, hw, hd, 2f, Pal.STONE_D, Pal.STONE_X)
            i.box(hx, hy, 2f, hw, hd, 6f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
            if (fine) for (z in 3..7) {
                i.line(hx, hy + hd, z.toFloat(), hx + hw, hy + hd, z.toFloat(), Pal.WOOD_D)
                i.line(hx + hw, hy, z.toFloat(), hx + hw, hy + hd, z.toFloat(), Pal.WOOD_X)
            }
        }
        // the door on the front, away from the wheel; a small window on the side, lit at night once it grinds
        i.panelY(hx + hw * 0.62f, hy + hd, 2f, 0.32f, 4.5f, Pal.DOOR)
        if (done && lit) {
            glow { i.panelX(hx + hw, hy + 0.3f, 4.5f, 0.3f, 2f, Pal.WINDOW_LIT) }
            s.light(i.sx(hx + hw, hy + 0.45f), i.sy(hx + hw, hy + 0.45f, 5.5f), 11f, 0.5f * env.windows)
        } else i.panelX(hx + hw, hy + 0.3f, 4.5f, 0.3f, 2f, Pal.GLASS)
        if (done) {
            b.roofX(hx - 0.2f, hy - 0.2f, 8f, hw + 0.4f, hd + 0.4f, 4.5f, if (slate != null) b.slate() else b.shingles(), slate?.walls?.get(0)?.get(1) ?: Pal.WOOD_L)
            if (!env.snow) { i.px(hx + hw * 0.62f + 0.45f, hy + hd + 0.1f, 1f, Pal.CANVAS_L); i.px(hx + hw * 0.62f + 0.45f, hy + hd + 0.1f, 2f, Pal.CANVAS_L) } // a sack of flour
        } else {
            roofFrameX(hx - 0.2f, hy - 0.2f, 8f, hw + 0.4f, hd + 0.4f, 4.5f)
            scaffoldFront(hx, hy, hw, hd, 11f)
        }
        // the wheel in the race by the house's front, its axle into the wall
        val cx = hx + 0.5f; val cz = 3.8f
        i.line(cx, hy + hd, cz, cx, ry, cz, IRON)
        // it turns while the race runs (frozen in the snow, it stands still)
        millWheel(cx, ry, cz, 0.55f, 4f, turning = done && !env.snow)
        if (done && !env.snow) {
            // the water its paddles throw up where they come out of the race, downstream
            val fx = i.ix(cx + 0.35f, ry); val fy = i.iy(cx + 0.35f, ry, 0.5f)
            s.sprite(fx, fy) { for (q in 0..2) c.set(fx + q, fy - (((s.time * 8).toInt() + q) % 3), if (q == 1) Pal.FLAG_WHITE else Pal.WATER_L) }
        }
    }

    /**
     * An undershot wheel in the plane y = [y] round ([cx], [cz]): its rim, spokes and paddles, turning when [turning], its
     * paddles down in the race going with the water (+x, the way the race runs off the stream), its top against it.
     */
    private fun millWheel(cx: Float, y: Float, cz: Float, rx: Float, rz: Float, turning: Boolean) {
        val a0 = if (turning) (s.time * 1.3).toFloat() else 0.3f
        fun at(a: Float, f: Float) = floatArrayOf(cx + cos(a) * rx * f, cz + sin(a) * rz * f)
        val n = 14
        for (j in 0 until n) {
            val p = at(j * 2f * PI.toFloat() / n, 1f); val q = at((j + 1) * 2f * PI.toFloat() / n, 1f)
            i.line(p[0], y, p[1], q[0], y, q[1], Pal.WOOD_M)
        }
        for (j in 0 until 4) {
            val a = a0 + j * PI.toFloat() / 4f
            val p = at(a, 1f); val q = at(a + PI.toFloat(), 1f)
            i.line(p[0], y, p[1], q[0], y, q[1], Pal.WOOD_D)
        }
        for (j in 0 until 8) {
            val a = a0 + j * PI.toFloat() / 4f + 0.2f
            val p = at(a, 1f); val q = at(a, 1.3f)
            i.line(p[0], y, p[1], q[0], y, q[1], Pal.WOOD_D)
        }
        i.px(cx, y, cz, IRON)
    }

    // ---------------------------------------------------------------- Balinišče · the bocce court

    /** A long strip of raked sand in a low wooden border, the balls and the little white jack on it. */
    private fun balinisce(x: Float, y: Float, w: Float, d: Float, look: BuildLook) {
        val along = w >= d
        val len = if (along) w else d; val wide = if (along) d else w
        fun box(u: Float, v: Float, z: Float, du: Float, dv: Float, h: Float) {
            val top = if (env.snow) Pal.SNOW_L else Pal.WOOD_L
            if (along) i.box(x + u, y + v, z, du, dv, h, top, Pal.WOOD_M, Pal.WOOD_D) else i.box(x + v, y + u, z, dv, du, h, top, Pal.WOOD_M, Pal.WOOD_D)
        }
        fun top(u: Float, v: Float, z: Float, du: Float, dv: Float, col: Int) = if (along) i.top(x + u, y + v, z, du, dv, col) else i.top(x + v, y + u, z, dv, du, col)
        fun line(u1: Float, v1: Float, z1: Float, u2: Float, v2: Float, z2: Float, col: Int) =
            if (along) i.line(x + u1, y + v1, z1, x + u2, y + v2, z2, col) else i.line(x + v1, y + u1, z1, x + v2, y + u2, z2, col)
        fun ball(u: Float, v: Float, col: Int) {
            val wx = if (along) x + u else x + v; val wy = if (along) y + v else y + u
            i.px(wx, wy, 1.3f, col)
            if (fine) c.set(i.ix(wx, wy), i.iy(wx, wy, 1.3f), Col.mix(col, Col.hex(0xFFFFFF), 0.45f))
        }
        val t = 0.14f; val bh = 1.5f
        val court = s.look.court
        val sand = if (env.snow) Pal.SNOW_M else when (court) { Court.CURLING -> LANE_M; Court.GREEN -> GREEN_M; Court.BOCCE -> SAND_M }
        box(0f, 0f, 0f, len, t, bh); box(0f, 0f, 0f, t, wide, bh)
        val sandTo = if (look == BuildLook.FRAME) len * 0.55f else len - t
        top(t, t, 0.6f, sandTo - t, wide - 2 * t, sand)
        if (look == BuildLook.FRAME) {
            // the sand still heaped at the far end, the front boards lying ready
            val u0 = len * 0.68f; val v0 = wide * 0.2f
            val heapL = if (env.snow) Pal.SNOW_L else SAND_L; val heapD = if (env.snow) Pal.SNOW_D else SAND_D
            if (along) i.pyramid(x + u0, y + v0, 0f, 0.8f, 0.7f, 2.2f, heapL, heapD) else i.pyramid(x + v0, y + u0, 0f, 0.7f, 0.8f, 2.2f, heapL, heapD)
            box(len * 0.15f, wide - 0.3f, 0f, len * 0.5f, 0.2f, 0.4f)
            if (along) { i.post(x + len, y + wide, 0f, 3f, Pal.WOOD_L); i.post(x + len, y, 0f, 3f, Pal.WOOD_L) }
            else { i.post(x + wide, y + len, 0f, 3f, Pal.WOOD_L); i.post(x, y + len, 0f, 3f, Pal.WOOD_L) }
            return
        }
        if (!env.snow && court == Court.GREEN) {
            // the green mown in stripes across it
            var u = t; var q = 0
            while (u < sandTo) { if (q % 2 == 0) top(u, t, 0.6f, min(0.5f, sandTo - u), wide - 2 * t, GREEN_L); u += 0.5f; q++ }
        } else if (!env.snow && court == Court.CURLING) {
            // the lane's lines and the target's square at the far end
            line(0.3f, wide * 0.5f, 0.6f, len - 0.3f, wide * 0.5f, 0.6f, LANE_L)
            line(len - 0.9f, wide * 0.2f, 0.6f, len - 0.9f, wide * 0.8f, 0.6f, Pal.FLAG_WHITE)
        } else if (!env.snow) {
            if (fine) for (q in 1..3) line(0.35f, wide * q / 4f, 0.6f, len - 0.35f, wide * q / 4f, 0.6f, SAND_D) // raked
            else line(0.4f, wide / 2f, 0.6f, len - 0.4f, wide / 2f, 0.6f, SAND_D)
        }
        if (look == BuildLook.DONE && !env.snow && court == Court.CURLING) {
            // the stocks near the Daube (the little wooden block), one sliding down the lane
            ball(len * 0.82f, wide * 0.5f, DAUBE) // the Daube, a black rubber disc
            for ((u, v, col) in listOf(Triple(0.76f, 0.3f, Flag.AT_RED), Triple(0.88f, 0.66f, Col.hex(0x2F6A3E)), Triple(0.7f, 0.68f, Flag.AT_RED))) stock(len * u, wide * v, col, along, x, y)
            val ph = ((s.time / 8.0) % 1.0).toFloat()
            val slide = if (ph < 0.6f) 1f - (1f - ph / 0.6f).let { it * it } else 1f
            stock(len * (0.12f + 0.52f * slide), wide * 0.5f, Col.hex(0x2F6A3E), along, x, y)
        } else if (look == BuildLook.DONE && !env.snow && court == Court.GREEN) {
            ball(len * 0.8f, wide * 0.45f, Pal.FLAG_WHITE) // the jack
            ball(len * 0.74f, wide * 0.28f, WOOD_BALL); ball(len * 0.87f, wide * 0.64f, WOOD_BALL_D); ball(len * 0.71f, wide * 0.7f, WOOD_BALL)
            val ph = ((s.time / 11.0) % 1.0).toFloat()
            val roll = if (ph < 0.6f) 1f - (1f - ph / 0.6f).let { it * it } else 1f
            ball(len * (0.12f + 0.54f * roll), wide * (0.5f + 0.12f * sin(roll * PI).toFloat()), WOOD_BALL_D)
        } else if (look == BuildLook.DONE && !env.snow) {
            ball(len * 0.8f, wide * 0.45f, JACK) // the jack (il pallino) of wood
            ball(len * 0.74f, wide * 0.28f, BALL_STEEL); ball(len * 0.87f, wide * 0.64f, BALL_STEEL_D); ball(len * 0.71f, wide * 0.7f, BALL_STEEL_D)
            // one on its way down the court, slowing to a stop by the others
            val ph = ((s.time / 9.0) % 1.0).toFloat()
            val roll = if (ph < 0.5f) 1f - (1f - ph / 0.5f).let { it * it } else 1f
            ball(len * (0.12f + 0.54f * roll), wide * 0.5f, BALL_STEEL)
        }
        box(0f, wide - t, 0f, len, t, bh); box(len - t, 0f, 0f, t, wide, bh)
        if (look == BuildLook.SCAFFOLD) {
            // the rake left leaning on the end board
            line(len - 0.35f, wide * 0.75f, 0.6f, len + 0.05f, wide * 0.3f, 6f, Pal.WOOD_M)
            line(len - 0.45f, wide * 0.55f, 0.7f, len - 0.25f, wide * 0.95f, 0.7f, Pal.STONE_X)
        }
    }

    /** A Stockschießen stock on the lane at [u] along it and [v] across: a flat disc of [col] with its handle. */
    private fun stock(u: Float, v: Float, col: Int, along: Boolean, x: Float, y: Float) {
        val wx = if (along) x + u else x + v; val wy = if (along) y + v else y + u
        i.px(wx, wy, 0.9f, col); i.px(wx + 0.12f, wy, 0.9f, Col.scale(col, 0.8f))
        i.px(wx, wy, 1.9f, IRON)
    }

    // ---------------------------------------------------------------- Kapelica · the wayside chapel

    /** A small white chapel on a stone plinth, a tiled gable roof, a niche with the Virgin in the front, a candle at night. */
    private fun kapelica(x: Float, y: Float, look: BuildLook) {
        val x0 = x + 0.3f; val y0 = y + 0.3f; val bw = 1.0f
        val nx = x0 + 0.26f; val nw = 0.48f; val ny = y0 + bw
        shadow(x0, y0, bw, bw)
        if (look == BuildLook.FRAME) {
            i.box(x0, y0, 0f, bw, bw, 5f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
            b.mortar(x0, y0, 0f, bw, bw, 5f, Pal.STONE_D, Pal.STONE_X)
            if (!fine) { i.line(x0, ny, 2.5f, x0 + bw, ny, 2.5f, Pal.STONE_D); i.line(x0 + bw, y0, 2.5f, x0 + bw, ny, 2.5f, Pal.STONE_X) }
            i.panelY(nx, ny, 2f, nw, 3f, NICHE)
            return
        }
        val done = look == BuildLook.DONE
        if (!done) scaffoldBack(x0, y0, 11f)
        i.box(x0 - 0.05f, y0 - 0.05f, 0f, bw + 0.1f, bw + 0.1f, 1f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        // its walls as the village builds them (the Lake District's of stone, [MapLook.style]), white by default
        val stone = s.look.style?.stone == true
        // an ancona's ochre (Friuli's, [MapLook.ancona]: the village's first plaster)
        val walls = if (s.look.ancona) s.look.style?.walls?.get(0) else null
        if (stone) b.looks.stoneBox(x0, y0, 1f, bw, bw, 9f, s.look.style!!.walls[0], 79, quoins = false)
        else if (walls != null) i.box(x0, y0, 1f, bw, bw, 9f, walls[0], walls[1], walls[2])
        else i.box(x0, y0, 1f, bw, bw, 9f, Pal.WALL_L, Pal.WALL_M, Pal.WALL_D)
        // the niche, its rounded top, a frame round it at a closer look
        i.panelY(nx, ny, 3f, nw, 4f, NICHE)
        i.panelY(nx + 0.12f, ny, 7f, nw - 0.24f, 1f, NICHE)
        if (fine) { i.line(nx - 0.06f, ny, 3f, nx - 0.06f, ny, 7f, Pal.WALL_D); i.line(nx + nw + 0.06f, ny, 3f, nx + nw + 0.06f, ny, 7f, Pal.WALL_D) }
        if (done) {
            val fx = i.ix(nx + nw / 2f, ny); val fy = i.iy(nx + nw / 2f, ny, 3f)
            s.sprite(fx, fy) { c.set(fx, fy - 1, MANTLE); c.set(fx, fy - 2, MANTLE); c.set(fx, fy - 3, Pal.SKIN) }
            if (finest) c.set(fx + 1, fy - 3 * k - 1, Pal.GOLD_L) // her halo
            if (lit) {
                glow { s.sprite(fx, fy) { c.set(fx + 1, fy - 1, Pal.WINDOW_LIT_HI); c.set(fx + 1, fy - 2, Pal.FLAME[1]) } }
                s.light(fx.toFloat(), fy - 2f * k, 12f, 0.55f * env.windows)
            }
            // a small window on the side
            if (lit) glow { i.panelX(x0 + bw, y0 + 0.4f, 4f, 0.22f, 2.5f, Pal.WINDOW_LIT) } else i.panelX(x0 + bw, y0 + 0.4f, 4f, 0.22f, 2.5f, Pal.GLASS)
            // flowers at its foot in the warm months
            if (warm) for (j in 0..3) i.px(x0 + 0.08f + j * 0.28f, ny + 0.02f, 1.5f, if (j % 2 == 0) Pal.GERANIUM else Pal.PAINTED[1])
            // the roof: shingles as its scene's (the village's in a culture's look: coppi, shingles, slate)
            val roof = if (s.look.own) b.shingles() else b.villageRoof()
            b.roofY(x0 - 0.2f, y0 - 0.15f, 10f, bw + 0.4f, bw + 0.45f, 5.5f, roof, if (stone) s.look.style!!.walls[0][1] else walls?.get(0) ?: Pal.WALL_L)
            // the bell: in the little turret on the ridge over the front, or an ancona's on its iron bracket under the gable
            val tx = x0 + bw / 2f; val ty = y0 + bw + 0.1f
            val top = if (s.look.ancona) {
                i.line(tx, ny + 0.06f, 11.6f, tx + 0.28f, ny + 0.06f, 11.6f, IRON)
                i.px(tx + 0.28f, ny + 0.06f, 10.9f, Pal.GOLD)
                15.5f
            } else turret(tx, ty, 15.5f)
            val ax = i.ix(tx, ty + 0.2f); val ay = i.iy(tx, ty + 0.2f, top)
            s.sprite(ax, ay) { c.vline(ax, ay - 4, ay, IRON); c.hline(ax - 1, ax + 1, ay - 3, IRON) }
        } else {
            rafters(x0 - 0.2f, y0 - 0.15f, 10f, bw + 0.4f, bw + 0.45f, 5.5f)
            scaffoldFront(x0, y0, bw, bw, 11f)
        }
    }

    /**
     * The chapel's little turret on its ridge at ([tx], [ty], [z]), as its scene's: two wooden posts with the bell between them
     * under a little gable of shingles; Carinthia's under an onion cap of dark shingles ([Chapel.ONION]); the Lake District's
     * a bellcote of two stone piers under a stone cap ([Chapel.SLATE]). Returns the height its cross stands on.
     */
    private fun turret(tx: Float, ty: Float, z: Float): Float {
        val slate = s.look.chapel == Chapel.SLATE
        val pier = if (slate) Col.hex(0x8C949A) else Pal.WOOD_M
        val o = if (slate) 0.16f else 0.13f
        i.post(tx - o, ty, z, z + 2f, pier); i.post(tx + o, ty, z, z + 2f, if (slate) Col.scale(pier, 0.8f) else Pal.WOOD_D)
        if (slate && fine) { i.post(tx - o + 0.05f, ty, z, z + 2f, pier); i.post(tx + o - 0.05f, ty, z, z + 2f, Col.scale(pier, 0.8f)) }
        i.px(tx, ty, z + 1f, Pal.GOLD) // the bell
        val snow = env.snow
        if (s.look.chapel == Chapel.ONION) {
            val l = if (snow) Pal.SNOW_L else Pal.SHINGLE_L; val m = if (snow) Pal.SNOW_M else Pal.SHINGLE_M; val d = Pal.SHINGLE_D
            i.line(tx - 0.2f, ty, z + 2.2f, tx + 0.2f, ty, z + 2.2f, m)
            i.line(tx - 0.24f, ty, z + 2.9f, tx + 0.24f, ty, z + 2.9f, l); i.px(tx + 0.24f, ty, z + 2.9f, d)
            i.line(tx - 0.14f, ty, z + 3.6f, tx + 0.14f, ty, z + 3.6f, m)
            i.px(tx, ty, z + 4.2f, d)
            return z + 4.6f
        }
        val cap = when { snow -> Pal.SNOW_L; slate -> Col.hex(0x6E7880); else -> Pal.SHINGLE_D }
        i.line(tx - 0.26f, ty, z + 2.2f, tx + 0.26f, ty, z + 2.2f, cap)
        i.line(tx - 0.13f, ty, z + 2.8f, tx + 0.13f, ty, z + 2.8f, if (snow || slate) cap else Pal.SHINGLE_M)
        return z + 3.2f
    }

    /** A gable roof's rafters, with the ridge along y, before the tiles go on. */
    private fun rafters(x: Float, y: Float, z: Float, w: Float, d: Float, rise: Float) {
        i.line(x + w / 2f, y, z + rise, x + w / 2f, y + d, z + rise, Pal.WOOD_D)
        for (j in 0..3) {
            val v = y + d * j / 3f
            i.line(x, v, z, x + w / 2f, v, z + rise, Pal.WOOD_M); i.line(x + w / 2f, v, z + rise, x + w, v, z, Pal.WOOD_L)
        }
    }

    // ---------------------------------------------------------------- Terase v vinogradu · the vineyard terraces

    /**
     * Three terraces stepping up to the back, held by dry stone walls, a row of vines on posts and a wire along each:
     * green through the summer with grapes late in it, yellow and red in the autumn, bare canes in the winter; a
     * klopotec (the wind rattle that keeps the birds off the grapes) turning on its pole from August to Martinovo.
     */
    private fun vinograd(x: Float, y: Float, w: Float, d: Float, look: BuildLook) {
        val kind = s.look.vineyard
        if (kind == Vineyard.ORCHARD) { orchard(x, y, w, d, look); return }
        val t = d / 3f
        val soil = if (env.snow) Pal.SNOW_M else Col.mix(env.grass[1], Pal.SOIL_M, 0.5f)
        val front = if (env.snow) Pal.SNOW_L else Col.mix(env.grass[0], Pal.SOIL_L, 0.4f)
        for (j in 2 downTo 0) {
            val y0 = y + d - (j + 1) * t; val z = j * 3f
            if (j > 0) {
                i.box(x, y0, 0f, w, t, z, soil, Pal.STONE_M, Pal.STONE_D)
                if (fine) b.mortar(x, y0, 0f, w, t, z, Pal.STONE_D, Pal.STONE_X)
                else for (q in 0 until 6) i.px(x + 0.2f + q * 0.5f, y0 + t, 1f + (q % 2), if (q % 2 == 0) Pal.STONE_L else Pal.STONE_D)
            } else i.top(x, y0, 0f, w, t, front)
            if (look == BuildLook.FRAME) continue
            if (kind == Vineyard.DAMSONS) damsons(x, y0 + t * 0.5f, z, w, j, young = look != BuildLook.DONE)
            else vines(x, y0 + t * 0.5f, z, w, young = look != BuildLook.DONE)
        }
        if (look == BuildLook.FRAME) timberHeap(x + w - 1.55f, y + d - t + 0.02f)
        // the klopotec is the Slovene vineyards' (Friuli's Collio has none)
        if (look == BuildLook.DONE && env.month in 8..11 && kind == Vineyard.TERRACES) klopotec(x + w - 0.3f, y + 0.3f, 6f)
    }

    /**
     * A row of young damson trees along a terrace (the Lake District's damson orchard): small round crowns, white blossom in
     * April, the dark purple fruit in August and September, yellow leaves in October, bare in winter.
     */
    private fun damsons(x: Float, vy: Float, z: Float, w: Float, row: Int, young: Boolean) {
        var u = 0.45f + (row % 2) * 0.35f
        var n = 0
        while (u < w - 0.3f) {
            fruitTree(x + u, vy, z, if (young) 0.55f else 0.85f, row * 7 + n, DAMSON, blossom = Col.hex(0xF6F4F0))
            u += 0.85f; n++
        }
    }

    /**
     * Carinthia's orchard (der Obstgarten): old apple and pear trees standing on the meadow, a ladder against one at picking
     * time; young trees with their stakes while it is being planted.
     */
    private fun orchard(x: Float, y: Float, w: Float, d: Float, look: BuildLook) {
        val grass = if (env.snow) Pal.SNOW_M else Col.mix(env.grass[1], env.grass[0], 0.3f)
        ground { i.top(x, y, 0f, w, d, grass) }
        if (look == BuildLook.FRAME) { timberHeap(x + w - 1.55f, y + d - 0.9f); return }
        val young = look != BuildLook.DONE
        // back to front, so the nearer trees stand over the further
        val spots = ORCHARD.sortedBy { it[0] + it[1] }
        for ((n, sp) in spots.withIndex()) {
            val pear = n % 3 == 1
            fruitTree(x + sp[0] * w, y + sp[1] * d, 0f, if (young) 0.65f else 1.3f, n * 13 + 5, if (pear) PEAR else APPLE, blossom = if (pear) Col.hex(0xFFFFFF) else Col.hex(0xF4C8D4))
            if (young) i.post(x + sp[0] * w + 0.2f, y + sp[1] * d, 0f, 4f, Pal.WOOD_L)
        }
        if (look == BuildLook.DONE && env.month in 8..10) {
            // the ladder against the tree in front, a basket at its foot
            val lx = x + spots.last()[0] * w + 0.25f; val ly = y + spots.last()[1] * d + 0.25f
            i.line(lx, ly, 0f, lx - 0.1f, ly - 0.25f, 7f, Pal.WOOD_L); i.line(lx + 0.2f, ly - 0.1f, 0f, lx + 0.1f, ly - 0.35f, 7f, Pal.WOOD_L)
            i.box(lx + 0.3f, ly + 0.1f, 0f, 0.35f, 0.3f, 1f, Pal.WOOD_M, Pal.WOOD_L, Pal.WOOD_D)
            i.px(lx + 0.45f, ly + 0.25f, 1.3f, APPLE)
        }
    }

    /**
     * A fruit tree standing at ([x], [y]) on [z], [size] of an old one's: its trunk and round crown, the blossom in April,
     * leaves through the summer with its fruit ([fruit]) from August to October, yellow in October, bare in winter.
     */
    private fun fruitTree(x: Float, y: Float, z: Float, size: Float, seed: Int, fruit: Int, blossom: Int) {
        val m = env.month
        i.post(x, y, z, z + 3.5f * size, Pal.LOG_M)
        val ax = i.ix(x, y); val ay = i.iy(x, y, z + 3.5f * size + 2.2f * size)
        val r = 2.6f * size * k
        val leaf = when {
            env.snow || m <= 3 || m == 12 -> 0
            m == 11 -> if (seed % 2 == 0) Col.hex(0xC8A040) else 0
            m == 10 -> Col.hex(0xC8A84A)
            else -> Pal.LEAF
        }
        if (leaf == 0) {
            // bare: its branches
            c.line(ax, ay + (r / 2).toInt(), ax - r.toInt(), ay - (r / 2).toInt(), Col.hex(0x6A5244))
            c.line(ax, ay + (r / 2).toInt(), ax + r.toInt(), ay - (r / 3).toInt(), Col.hex(0x6A5244))
            c.line(ax, ay + (r / 2).toInt(), ax, ay - r.toInt(), Col.hex(0x6A5244))
            if (env.snow) c.block(ax - k, ay - r.toInt(), k, Pal.SNOW_L)
            return
        }
        val dark = Col.scale(leaf, 0.72f); val light = Col.mix(leaf, Col.hex(0xFFFFFF), 0.22f)
        val n = r.toInt() + 1
        for (dy in -n..n) for (dx in -n..n) {
            if (dx * dx + dy * dy * 1.3f > r * r) continue
            val v = (-dx - dy) / r + (Noise.rnd(ax + dx shr 1, ay + dy shr 1, seed) - 0.5f) * 0.8f
            var col = if (v > 0.45f) light else if (v > -0.35f) leaf else dark
            if (m == 4 && Noise.rnd(ax + dx, ay + dy, seed + 3) < 0.45f) col = blossom
            else if (m in 8..10 && Noise.rnd(ax + dx, ay + dy, seed + 4) < 0.1f) col = fruit
            c.set(ax + dx, ay + dy, col)
        }
    }

    private fun vines(x: Float, vy: Float, z: Float, w: Float, young: Boolean) {
        val m = env.month
        val leaf = when {
            env.snow -> 0
            m in 4..5 -> Col.hex(0x8CCB58)
            m in 6..8 -> Pal.LEAF
            m == 9 -> Col.hex(0x7FA83A)
            m == 10 -> Col.hex(0xE0A030)
            m == 11 -> Col.hex(0xB0502A)
            else -> 0
        }
        val leafL = when {
            m in 6..9 -> VINE_L
            m == 10 -> Col.hex(0xC8392F)
            else -> Col.mix(leaf, Col.hex(0xFFFFFF), 0.2f)
        }
        i.line(x + 0.25f, vy, z + 3.5f, x + w - 0.25f, vy, z + 3.5f, Pal.STONE_X)
        var u = 0.3f
        while (u < w - 0.2f) {
            i.post(x + u, vy, z, z + 4f, Pal.WOOD_D)
            when {
                young -> if (!env.snow) i.px(x + u + 0.14f, vy, z + 1.5f, VINE_L)
                leaf != 0 -> {
                    i.px(x + u + 0.14f, vy, z + 3f, leaf); i.px(x + u + 0.3f, vy, z + 3.5f, leafL); i.px(x + u + 0.42f, vy, z + 2.5f, leaf)
                    if (m in 8..9) i.px(x + u + 0.28f, vy + 0.05f, z + 1.8f, GRAPE)
                    if (finest && m in 8..9) c.set(i.ix(x + u + 0.28f, vy + 0.05f), i.iy(x + u + 0.28f, vy + 0.05f, z + 1.8f), GRAPE_L)
                }
                else -> i.line(x + u, vy, z + 3f, x + u + 0.5f, vy, z + 3f, Pal.WOOD_M) // bare canes
            }
            u += 0.55f
        }
    }

    private fun klopotec(x: Float, y: Float, z: Float) {
        i.post(x, y, z, z + 11f, Pal.WOOD_D)
        val ax = i.ix(x, y); val ay = i.iy(x, y, z + 11f)
        s.sprite(ax, ay) {
            c.line(ax, ay, ax - 4, ay - 2, Pal.WOOD_M); c.line(ax, ay, ax - 4, ay, Pal.WOOD_M); c.line(ax, ay, ax - 4, ay + 2, Pal.WOOD_M) // the tail
            val a = s.time * 5.0
            for (q in 0 until 4) {
                val aa = a + q * PI / 2
                c.line(ax + 1, ay, ax + 1 + (cos(aa) * 3).roundToInt(), ay + (sin(aa) * 3).roundToInt(), Pal.WOOD_L)
            }
            c.set(ax + 1, ay, IRON)
        }
    }

    // ---------------------------------------------------------------- Čebelji travnik · the bee meadow

    /** A patch of meadow flowers with three hives on posts among them, each under a little roof, its front painted. */
    private fun travnik(x: Float, y: Float, w: Float, d: Float, look: BuildLook) {
        ground { flowers(x, y, w, d, when (look) { BuildLook.DONE -> 1f; BuildLook.SCAFFOLD -> 0.5f; else -> 0f }) }
        // Friuli's sunflowers standing over its clover from July to October, as Il prato delle api sows them
        if (look == BuildLook.DONE && s.look.sunflowers && !env.snow && env.month in 7..10) for (q in 0 until 7) {
            val u = 0.12f + Noise.rnd(q, 57) * 0.76f; val v = 0.15f + Noise.rnd(q, 58) * 0.7f
            val fx = x + u * w; val fy = y + v * d; val hgt = 3.5f + Noise.rnd(q, 59) * 1.5f
            i.line(fx, fy, 0f, fx, fy, hgt, Pal.LEAF)
            i.px(fx, fy, hgt + 0.5f, if (env.month == 10) Col.hex(0x8A6A2A) else Pal.GOLD)
            if (fine) c.set(i.ix(fx, fy) + k / 2, i.iy(fx, fy, hgt + 0.5f) + k / 2, Col.hex(0x5A3A1A)) // its dark middle, closer up
        }
        // a row across the patch, level on the screen, each hive in the clear
        for (j in 0 until 3) hive(x + 0.2f + j * (w - 0.9f) / 2f, y + d - 0.75f - j * (d - 1f) / 2f, j, look)
        if (look == BuildLook.DONE && warm && env.dark < 0.5f) {
            val bx = i.sx(x + w / 2f, y + d / 2f); val by = i.sy(x + w / 2f, y + d / 2f, 5f)
            s.fx { b.bees(bx, by, 7) }
        }
    }

    private fun flowers(x: Float, y: Float, w: Float, d: Float, amount: Float) {
        if (env.snow) return
        if (amount <= 0f) {
            for (q in 0 until 10 * k) {
                val u = Noise.rnd(q, 53); val v = Noise.rnd(q, 54)
                c.set(i.ix(x + 0.2f + u * (w - 0.4f), y + 0.2f + v * (d - 0.4f)), i.iy(x + 0.2f + u * (w - 0.4f), y + 0.2f + v * (d - 0.4f), 0f) - 1, VINE_L) // sown: shoots
            }
            return
        }
        val m = env.month
        val heads = when (m) {
            in 3..5 -> intArrayOf(Col.hex(0xFFFFFF), Col.hex(0xF4B8CC), Pal.GOLD)
            in 6..8 -> intArrayOf(Pal.GOLD, Col.hex(0x6A7AD8), Col.hex(0xE84A3A), Col.hex(0xFFFFFF), Col.hex(0xF4B8CC))
            9 -> intArrayOf(Col.hex(0x9A6AC8), Pal.GOLD, Col.hex(0xFFFFFF))
            10 -> intArrayOf(Col.hex(0x9A6AC8))
            else -> return
        }
        val n = ((if (m in 4..8) 48 else 16) * amount * k).toInt()
        for (q in 0 until n) {
            val u = Noise.rnd(q, 51); val v = Noise.rnd(q, 52)
            val du = u * 2f - 1f; val dv = v * 2f - 1f
            if (du * du + dv * dv > 1f) continue
            val px = x + u * w; val py = y + v * d
            val sx = i.ix(px, py); val sy = i.iy(px, py, 0f)
            val col = heads[q % heads.size]
            if (k == 1) c.set(sx, sy - 1, col)
            else { c.set(sx, sy - 1, Pal.LEAF); c.set(sx, sy - 2, col); if (finest && q % 3 == 0) c.set(sx + 1, sy - 2, col) }
        }
    }

    private fun hive(hx: Float, hy: Float, j: Int, look: BuildLook) {
        if (s.look.hives == Hives.WBC) { wbc(hx, hy, look); return }
        i.post(hx + 0.3f, hy + 0.25f, 0f, 2f, Pal.WOOD_X)
        val painted = look == BuildLook.DONE || look == BuildLook.SCAFFOLD && j < 2
        // Friuli's hives in plain paint, without the Slovene painted fronts' pictures ([MapLook.hives])
        val plain = s.look.hives == Hives.PLAIN
        val front = if (painted) (if (plain) CultureBuildings.HIVES[j % CultureBuildings.HIVES.size] else Pal.PAINTED[intArrayOf(0, 2, 1)[j % 3]]) else Pal.WOOD_L
        i.box(hx, hy, 2f, 0.6f, 0.5f, 3f, Pal.WOOD_M, front, Pal.WOOD_D)
        if (painted && plain) i.px(hx + 0.25f, hy + 0.5f, 2.4f, IRON)
        else if (painted) {
            // the painted front (končnica): a border and a figure, at a closer look
            if (fine) {
                i.line(hx, hy + 0.5f, 4.6f, hx + 0.6f, hy + 0.5f, 4.6f, Col.scale(front, 0.7f))
                c.set(i.ix(hx + 0.35f, hy + 0.5f), i.iy(hx + 0.35f, hy + 0.5f, 3.8f), if (j == 2) Pal.FLAG_BLUE else Pal.FLAG_WHITE)
                if (finest) { c.set(i.ix(hx + 0.35f, hy + 0.5f), i.iy(hx + 0.35f, hy + 0.5f, 3.8f) - 1, Pal.SKIN); c.set(i.ix(hx + 0.2f, hy + 0.5f), i.iy(hx + 0.2f, hy + 0.5f, 3.4f), Pal.LEAF) }
            }
            i.px(hx + 0.25f, hy + 0.5f, 2.4f, IRON) // the flight hole
        } else if (look == BuildLook.SCAFFOLD) {
            i.px(hx + 0.7f, hy + 0.65f, 0.6f, Pal.FLAG_RED); i.px(hx + 0.7f, hy + 0.65f, 1.4f, IRON) // a pot of paint
        }
        if (look != BuildLook.FRAME) {
            val r = if (s.look.own) b.shingles() else b.villageRoof()
            i.gableX(hx - 0.1f, hy - 0.1f, 5f, 0.8f, 0.7f, 1.6f, r.back, r.front, Pal.WOOD_D, r.edge)
        }
    }

    /** A white WBC hive on its legs (the Lake District's), two lifts under its little roof; unpainted wood while it is made. */
    private fun wbc(hx: Float, hy: Float, look: BuildLook) {
        val wt = if (look == BuildLook.DONE || look == BuildLook.SCAFFOLD) CultureBuildings.WBC else intArrayOf(Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        i.post(hx + 0.08f, hy + 0.5f, 0f, 1f, Pal.WOOD_X); i.post(hx + 0.5f, hy + 0.5f, 0f, 1f, Pal.WOOD_X)
        i.box(hx - 0.04f, hy - 0.04f, 1f, 0.66f, 0.62f, 1.2f, wt[0], wt[1], wt[2])
        i.panelY(hx + 0.2f, hy + 0.58f, 1.1f, 0.2f, 0.3f, Col.hex(0x2A2420))
        for (q in 0..1) i.box(hx, hy, 2.2f + q * 1.5f, 0.58f, 0.54f, 1.4f, wt[0], wt[1], wt[2])
        if (look != BuildLook.FRAME) b.roofX(hx - 0.1f, hy - 0.1f, 5.2f, 0.78f, 0.74f, 1.5f, if (env.snow) BuildingPainter.Roof(Pal.SNOW_L, Pal.SNOW_M, wt[2], rows = false) else BuildingPainter.Roof(wt[0], wt[1], wt[2], rows = false), wt[1])
    }

    // ---------------------------------------------------------------- Gasilski dom · the fire station

    /**
     * A small white hall with a red gate for the pump cart, and beside it the tall tower where the hoses dry, as its scene builds
     * it (V gasilskem domu): plastered over a stone footing, the belfry's round arches open on its faces with the bell in them,
     * a stone cornice, a pyramid roof of shingles with an iron cross on its tip. In a culture's look the hall and the tower of
     * its walls and under its roof: Friuli's of river cobbles with bands of brick under coppi, the Lake District's mountain
     * rescue hut and its tower of slate stone under slate.
     */
    private fun gasilskiDom(x: Float, y: Float, look: BuildLook) {
        val hx = x + 0.2f; val hy = y + 0.5f; val hw = 1.5f; val hd = 1.45f
        val tx = x + 1.9f; val ty = y + 0.2f; val tw = 0.6f; val td = 0.65f
        shadow(hx, hy, hw, hd); shadow(tx, ty, tw, td)
        if (look == BuildLook.FRAME) {
            i.box(hx, hy, 0f, hw, hd, 4.5f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
            b.mortar(hx, hy, 0f, hw, hd, 4.5f, Pal.STONE_D, Pal.STONE_X)
            i.panelY(hx + 0.3f, hy + hd, 0f, 0.85f, 4.5f, INTERIOR)
            // the tower's timber frame, half up
            val h = 12f
            i.post(tx, ty, 0f, h, Pal.WOOD_D); i.post(tx + tw, ty, 0f, h, Pal.WOOD_D)
            i.line(tx + tw, ty, 1.5f, tx + tw, ty + td, h - 1f, Pal.WOOD_X)
            i.post(tx, ty + td, 0f, h, Pal.WOOD_D)
            i.line(tx, ty + td, 1.5f, tx + tw, ty + td, h - 1f, Pal.WOOD_M)
            i.post(tx + tw, ty + td, 0f, h, Pal.WOOD_D)
            i.line(tx, ty + td, h, tx + tw, ty + td, h, Pal.WOOD_L); i.line(tx + tw, ty, h, tx + tw, ty + td, h, Pal.WOOD_L)
            return
        }
        val done = look == BuildLook.DONE
        // the Lake District's mountain rescue hut: of slate stone under slate, its tower too
        val rescue = s.look.style?.takeIf { it.stone }
        // the hall
        i.box(hx, hy, 0f, hw, hd, 1.5f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        if (fine) b.mortar(hx, hy, 0f, hw, hd, 1.5f, Pal.STONE_D, Pal.STONE_X)
        val cobbles = s.look.cobbleStation
        if (rescue != null) b.looks.stoneBox(hx, hy, 1.5f, hw, hd, 7f, rescue.walls[0], 97)
        else if (cobbles) b.looks.cobbleBox(hx, hy, 1.5f, hw, hd, 7f, 97)
        else i.box(hx, hy, 1.5f, hw, hd, 7f, Pal.WALL_L, Pal.WALL_M, Pal.WALL_D)
        val gx = hx + 0.3f; val gw = 0.85f
        i.panelY(gx, hy + hd, 0f, gw, 6f, GATE)
        i.line(gx + gw / 2f, hy + hd, 0f, gx + gw / 2f, hy + hd, 6f, GATE_D)
        if (fine) {
            var u = gw / 6f
            while (u < gw - 0.05f) { i.line(gx + u, hy + hd, 0f, gx + u, hy + hd, 6f, GATE_D); u += gw / 6f }
            i.line(gx, hy + hd, 0.5f, gx + gw / 2f, hy + hd, 5.5f, GATE_D); i.line(gx + gw / 2f, hy + hd, 0.5f, gx + gw, hy + hd, 5.5f, GATE_D)
            i.panelY(gx + 0.15f, hy + hd, 6.6f, gw - 0.3f, 1f, Pal.WALL_L) // the sign
            c.set(i.ix(gx + gw / 2f, hy + hd), i.iy(gx + gw / 2f, hy + hd, 7.3f), GATE)
        }
        // the lamp over the gate, the window on the side
        if (done && env.windows > 0.2f) {
            glow { i.px(gx + gw / 2f, hy + hd, 7.4f, Pal.WINDOW_LIT) }
            s.light(i.sx(gx + gw / 2f, hy + hd), i.sy(gx + gw / 2f, hy + hd, 7f), 14f, 0.55f)
        }
        if (lit && done) {
            glow { i.panelX(hx + hw, hy + 0.5f, 3.5f, 0.45f, 2.5f, Pal.WINDOW_LIT) }
            s.light(i.sx(hx + hw, hy + 0.72f), i.sy(hx + hw, hy + 0.72f, 4.5f) + 3 * k, 11f, 0.55f * env.windows)
        } else i.panelX(hx + hw, hy + 0.5f, 3.5f, 0.45f, 2.5f, Pal.GLASS)
        if (fine) i.line(hx + hw, hy + 0.72f, 3.5f, hx + hw, hy + 0.72f, 6f, Pal.WOOD_X)
        if (done) b.roofX(hx - 0.2f, hy - 0.25f, 8.5f, hw + 0.35f, hd + 0.5f, 5f, b.villageRoof(), rescue?.walls?.get(0)?.get(2) ?: Pal.WALL_L)
        else roofFrameX(hx - 0.2f, hy - 0.25f, 8.5f, hw + 0.35f, hd + 0.5f, 5f)
        // the hose tower
        if (!done) scaffoldBack(tx, ty, 21f)
        val th = if (done) 25f else 20f
        when {
            rescue != null -> b.looks.stoneBox(tx, ty, 0f, tw, td, th, rescue.walls[0], 98)
            cobbles -> b.looks.cobbleBox(tx, ty, 0f, tw, td, th, 98)
            else -> {
                i.box(tx, ty, 0f, tw, td, th, Pal.WALL_L, Pal.WALL_M, Pal.WALL_D)
                i.box(tx, ty, 0f, tw, td, 1.5f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D) // its stone footing
            }
        }
        i.panelX(tx + tw, ty + 0.25f, 11f, 0.15f, 2f, Pal.GLASS)
        if (done) {
            // the belfry's round arches open on both faces, the bell in them; the stone cornice; the roof; the cross on its tip
            i.panelY(tx + 0.13f, ty + td, 20.4f, tw - 0.26f, 2.8f, BELFRY)
            i.panelY(tx + 0.2f, ty + td, 23.2f, tw - 0.4f, 0.6f, BELFRY)
            i.panelX(tx + tw, ty + 0.13f, 20.4f, td - 0.26f, 2.8f, Col.scale(BELFRY, 0.8f))
            i.panelX(tx + tw, ty + 0.2f, 23.2f, td - 0.4f, 0.6f, Col.scale(BELFRY, 0.8f))
            i.px(tx + tw / 2f, ty + td, 22.4f, Pal.GOLD) // the bell
            if (fine) i.px(tx + tw / 2f, ty + td, 21.6f, Pal.GOLD_L)
            i.box(tx - 0.08f, ty - 0.08f, 25f, tw + 0.16f, td + 0.16f, 0.8f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
            val r = if (s.look.own) b.shingles() else b.villageRoof()
            i.pyramid(tx - 0.16f, ty - 0.16f, 25.8f, tw + 0.32f, td + 0.32f, 5.5f, if (env.snow) Pal.SNOW_L else r.back, if (env.snow) Pal.SNOW_M else r.front)
            val cx = tx + tw / 2f; val cy = ty + td / 2f
            i.post(cx, cy, 31.3f, 34.3f, IRON)
            i.line(cx - 0.14f, cy + 0.14f, 33.3f, cx + 0.14f, cy - 0.14f, 33.3f, IRON)
        } else scaffoldFront(tx, ty, tw, td, 21f)
    }

    /** A gable roof's rafters, with the ridge along x, before the tiles go on. */
    private fun roofFrameX(x: Float, y: Float, z: Float, w: Float, d: Float, rise: Float) {
        i.line(x, y + d / 2f, z + rise, x + w, y + d / 2f, z + rise, Pal.WOOD_D)
        for (j in 0..3) {
            val u = x + w * j / 3f
            i.line(u, y, z, u, y + d / 2f, z + rise, Pal.WOOD_M); i.line(u, y + d / 2f, z + rise, u, y + d, z, Pal.WOOD_L)
        }
    }

    // ---------------------------------------------------------------- Otroško igrišče · the playground

    /**
     * The playground as its scene builds it (Na igrišču): the wooden slide at the back with its ladder up to the platform, the
     * swing on its A-frame with its two plank seats (the near one swinging by day), the sandpit with a bucket and a spade, the
     * red ball on the lawn; Friuli's fontanella, the green cast-iron drinking fountain, by its corner ([MapLook.fontanella]).
     */
    private fun igrisce(x: Float, y: Float, look: BuildLook) {
        val playing = look == BuildLook.DONE && !env.snow && env.dark < 0.5f
        // the slide, at the back left: the ladder up the back to its platform, the chute down toward us
        val sx = x + 0.45f; val sy = y + 0.35f
        if (look != BuildLook.FRAME) {
            i.post(sx, sy, 0f, 6.5f, Pal.WOOD_D); i.post(sx + 0.4f, sy, 0f, 6.5f, Pal.WOOD_D)
            for (z in 1..5) i.line(sx, sy, z.toFloat(), sx + 0.4f, sy, z.toFloat(), Pal.WOOD_M)
            i.top(sx, sy, 5.5f, 0.4f, 0.4f, if (env.snow) Pal.SNOW_L else Pal.WOOD_L)
            if (look == BuildLook.DONE) {
                i.quad(sx, sy + 0.4f, 5.5f, sx + 0.4f, sy + 0.4f, 5.5f, sx + 0.4f, sy + 1.6f, 0.4f, sx, sy + 1.6f, 0.4f, if (env.snow) Pal.SNOW_L else Pal.WOOD_L)
                i.line(sx + 0.4f, sy + 0.4f, 6.2f, sx + 0.4f, sy + 1.6f, 1.1f, Pal.WOOD_D) // its side
                i.line(sx, sy + 0.4f, 6.2f, sx, sy + 1.6f, 1.1f, Pal.WOOD_M)
            }
        } else i.post(sx, sy, 0f, 6.5f, Pal.WOOD_D)
        swing(x + 1.5f, x + 2.65f, y + 0.6f, look, playing)
        sandbox(x + 0.95f, y + 1.05f, 1.2f, 0.85f, look)
        if (look == BuildLook.DONE && !env.snow) i.px(x + 2.45f, y + 1.75f, 0.6f, Pal.FLAG_RED) // the ball
        if (look == BuildLook.DONE && s.look.fontanella) {
            val fx = x + 2.55f; val fy = y + 0.15f
            i.box(fx - 0.1f, fy - 0.1f, 0f, 0.2f, 0.2f, 3.5f, FONTANELLA_L, FONTANELLA, Col.scale(FONTANELLA, 0.75f))
            i.px(fx, fy + 0.18f, 2.6f, FONTANELLA)
            if (!env.snow) i.line(fx, fy + 0.22f, 2.4f, fx, fy + 0.25f, 0.4f, Pal.WATER_L)
        }
    }

    private fun swing(xa: Float, xb: Float, yc: Float, look: BuildLook, playing: Boolean) {
        val top = 11f; val spread = 0.55f
        val legs = if (look == BuildLook.FRAME) floatArrayOf(xb - 0.05f) else floatArrayOf(xa + 0.05f, xb - 0.05f)
        for (lx in legs) rod(lx, yc - spread, 0f, lx, yc, top, Pal.WOOD_M)
        if (look != BuildLook.FRAME) {
            i.box(xa, yc - 0.06f, top - 0.6f, xb - xa, 0.12f, 0.8f, if (env.snow) Pal.SNOW_L else Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
            if (look == BuildLook.DONE) {
                // its two plank seats: the far one still, the near one swinging by day
                for (n in 0..1) {
                    val phi = if (playing && n == 0) sin(s.time * 2.3).toFloat() * 0.42f else 0f
                    val seatY = yc + sin(phi) * 1.5f; val seatZ = top - cos(phi) * 8f
                    val r0 = xa + (xb - xa) * (if (n == 0) 0.25f else 0.62f) - 0.14f; val r1 = r0 + 0.28f
                    i.line(r0, yc, top - 0.3f, r0, seatY, seatZ, ROPE); i.line(r1, yc, top - 0.3f, r1, seatY, seatZ, ROPE)
                    i.box(r0 - 0.04f, seatY - 0.08f, seatZ - 0.4f, 0.36f, 0.16f, 0.4f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
                }
            }
        }
        for (lx in legs) rod(lx, yc + spread, 0f, lx, yc, top, Pal.WOOD_L)
    }

    private fun sandbox(x0: Float, y0: Float, w: Float, d: Float, look: BuildLook) {
        val bh = 1.2f; val t = 0.1f
        val board = if (env.snow) Pal.SNOW_L else Pal.WOOD_L
        i.box(x0, y0, 0f, w, t, bh, board, Pal.WOOD_M, Pal.WOOD_D); i.box(x0, y0, 0f, t, d, bh, board, Pal.WOOD_M, Pal.WOOD_D)
        if (look != BuildLook.FRAME) {
            i.top(x0 + t, y0 + t, 0.8f, w - 2 * t, d - 2 * t, if (env.snow) Pal.SNOW_M else SAND_M)
            if (fine && !env.snow) for (q in 0 until 6 * k) {
                val u = Noise.rnd(q, 61); val v = Noise.rnd(q, 62)
                c.set(i.ix(x0 + t + u * (w - 2 * t), y0 + t + v * (d - 2 * t)), i.iy(x0 + t + u * (w - 2 * t), y0 + t + v * (d - 2 * t), 0.8f), if (q % 2 == 0) SAND_D else SAND_L)
            }
            if (look == BuildLook.DONE && !env.snow) {
                i.px(x0 + 0.4f, y0 + 0.5f, 1.5f, Pal.FLAG_BLUE); i.px(x0 + 0.4f, y0 + 0.5f, 2.3f, Col.scale(Pal.FLAG_BLUE, 0.7f)) // a bucket
                i.line(x0 + 0.75f, y0 + 0.6f, 0.9f, x0 + 0.95f, y0 + 0.45f, 2.5f, Pal.FLAG_RED) // a spade
                i.px(x0 + 0.85f, y0 + 0.3f, 1.3f, SAND_L); if (fine) i.px(x0 + 0.85f, y0 + 0.3f, 2.1f, SAND_D) // a sand castle
            }
        }
        i.box(x0, y0 + d - t, 0f, w, t, bh, board, Pal.WOOD_M, Pal.WOOD_D); i.box(x0 + w - t, y0, 0f, t, d, bh, board, Pal.WOOD_M, Pal.WOOD_D)
    }

    // ---------------------------------------------------------------- Vodnjak na trgu · the fountain on the square

    /**
     * The fountain on the square, as its scene builds it (Pri vodnjaku): an eight-sided trough of white limestone round its
     * pillar, a stone knob on the pillar's cap, the iron pipes with the water arcing from them into it (frozen in winter), a
     * lamp by it. In a culture's look ([MapLook.fountain]): Friuli's pine cone of stone on the cap and a moulding under the
     * rim; St Florian with his bucket on Carinthia's; the Lake District's of green slate, its lion's head spouting.
     */
    private fun vodnjak(x: Float, y: Float, look: BuildLook) {
        val bx = x + 0.2f; val by = y + 0.2f; val bs = 1.5f
        val cx = bx + bs / 2f; val cy = by + bs / 2f; val r = bs / 2f
        val kind = s.look.fountain
        val st = if (kind == Fountain.SLATE) FOUNT_SLATE else FOUNT_LIME
        shadow(bx, by, bs, bs)
        if (look == BuildLook.FRAME) {
            octagon(cx, cy, r, 0f, 1.5f, st)
            i.top(bx + 0.3f, by + 0.3f, 1.5f, bs - 0.6f, bs - 0.6f, if (env.snow) Pal.SNOW_M else Pal.SOIL_M)
            i.box(cx - 0.2f, cy - 0.2f, 1.5f, 0.4f, 0.4f, 1.5f, st[0], st[1], st[2])
            return
        }
        val water = look == BuildLook.DONE
        if (!water) scaffoldBack(bx, by, 10f)
        // the trough: its eight faces, the rim, the water in it
        octagon(cx, cy, r, 0f, 2.6f, st)
        if (kind == Fountain.PIGNA) octagon(cx, cy, r + 0.06f, 2.1f, 0.4f, st) // the moulding under the rim
        val inner = if (!water) st[3] else if (env.snow) Pal.ICE_M else Pal.WATER_M
        octagonTop(cx, cy, r - 0.16f, 2.6f, inner)
        if (water && !env.snow) for (q in 0..1) {
            val ph = ((s.time * 0.7 + q * 0.5) % 1.0).toFloat()
            c.set(i.ix(bx + 0.45f + ph * 0.6f, by + 1.05f - q * 0.5f), i.iy(bx + 0.45f + ph * 0.6f, by + 1.05f - q * 0.5f, 2.6f), Pal.WATER_L)
        }
        // the pillar and its cap
        i.box(cx - 0.15f, cy - 0.15f, 2.6f, 0.3f, 0.3f, 6.4f, st[0], st[1], st[2])
        i.box(cx - 0.22f, cy - 0.22f, 9f, 0.44f, 0.44f, 0.6f, st[0], st[1], st[2])
        val capZ = 9.6f
        when (kind) {
            Fountain.PIGNA -> { // the pine cone of stone
                i.box(cx - 0.12f, cy - 0.12f, capZ, 0.24f, 0.24f, 1.6f, st[0], st[1], st[2])
                i.px(cx, cy, capZ + 2.1f, st[1])
                if (fine) for (q in 0..2) i.px(cx + 0.02f, cy + 0.13f, capZ + 0.4f + q * 0.5f, st[3])
            }
            Fountain.FLORIAN -> { // St Florian on the cap: his grey armour, his red cloak, his bucket pouring
                i.box(cx - 0.1f, cy - 0.1f, capZ, 0.2f, 0.2f, 2f, FLORIAN_ARMOUR, FLORIAN_CLOAK, Col.scale(FLORIAN_CLOAK, 0.75f))
                i.px(cx, cy, capZ + 2.5f, Pal.SKIN)
                i.px(cx + 0.18f, cy + 0.12f, capZ + 1.7f, Pal.WOOD_M)
                if (fine) i.px(cx, cy, capZ + 3.1f, Col.hex(0x8C8C94)) // his helmet
            }
            Fountain.SLATE -> i.box(cx - 0.17f, cy - 0.17f, capZ, 0.34f, 0.34f, 0.5f, st[0], st[1], st[2]) // the stepped cap
            Fountain.OWN -> i.px(cx, cy, capZ + 0.6f, if (env.snow) Pal.SNOW_L else st[0]) // the knob
        }
        // the spouts: four iron pipes (the two toward us seen), or the lion's head on the Lake District's pillar
        val pz = 6.2f
        if (kind == Fountain.SLATE) {
            i.px(cx, cy + 0.16f, pz, st[3]); i.px(cx + 0.16f, cy, pz, st[3])
            if (fine) { i.px(cx - 0.06f, cy + 0.16f, pz + 0.5f, st[2]); i.px(cx + 0.06f, cy + 0.16f, pz + 0.5f, st[2]) }
        } else {
            i.line(cx, cy + 0.15f, pz, cx, cy + 0.42f, pz - 0.2f, IRON)
            i.line(cx + 0.15f, cy, pz, cx + 0.42f, cy, pz - 0.2f, IRON)
        }
        if (water) spill(cx, cy, pz)
        if (!water) scaffoldFront(bx, by, bs, bs, 10f)
        else lamp(x + 1.85f, y + 1.85f)
    }

    /** An eight-sided trough round ([cx], [cy]) of radius [r], from [z] up [h], its faces toward us in [st]'s light and shade. */
    private fun octagon(cx: Float, cy: Float, r: Float, z: Float, h: Float, st: IntArray) {
        for (j in 0 until 8) {
            val a0 = (j * 45f + 22.5f) * PI.toFloat() / 180f; val a1 = a0 + PI.toFloat() / 4f
            val nx = cos((a0 + a1) / 2f); val ny = sin((a0 + a1) / 2f)
            if (nx + ny <= 0.01f) continue
            val col = when { ny > 0.6f -> st[0]; nx > 0.6f -> st[2]; else -> st[1] }
            val x0 = cx + cos(a0) * r; val y0 = cy + sin(a0) * r; val x1 = cx + cos(a1) * r; val y1 = cy + sin(a1) * r
            i.quad(x0, y0, z, x1, y1, z, x1, y1, z + h, x0, y0, z + h, col)
        }
        octagonTop(cx, cy, r, z + h, if (env.snow) Pal.SNOW_L else st[0])
    }

    /** The eight-sided top of radius [r] round ([cx], [cy]) at [z], in [col]. */
    private fun octagonTop(cx: Float, cy: Float, r: Float, z: Float, col: Int) {
        for (j in 0 until 8) {
            val a0 = (j * 45f + 22.5f) * PI.toFloat() / 180f; val a1 = a0 + PI.toFloat() / 4f
            i.tri(cx, cy, z, cx + cos(a0) * r, cy + sin(a0) * r, z, cx + cos(a1) * r, cy + sin(a1) * r, z, col)
        }
    }

    /** The water arcing from the two spouts toward us into the trough at [z], or icicles at their mouths in winter. */
    private fun spill(cx: Float, cy: Float, z: Float) {
        for (e in 0..1) {
            val ex = if (e == 0) cx else cx + 0.42f; val ey = if (e == 0) cy + 0.42f else cy
            val ox = if (e == 0) 0f else 0.14f; val oy = if (e == 0) 0.14f else 0f
            if (env.snow) { i.line(ex, ey, z - 0.3f, ex, ey, z - 1.5f, Pal.ICE_L); continue }
            val steps = 6 * k
            for (q in 0..steps) {
                val t = q / steps.toFloat()
                val zz = z - 0.3f - (z - 2.9f) * t * t; val px = ex + ox * t; val py = ey + oy * t
                c.set(i.ix(px, py), i.iy(px, py, zz), if (((q + (s.time * 12).toInt()) % 4) == 0) Pal.WATER_L else Pal.WATER_M)
            }
            c.set(i.ix(ex + ox, ey + oy) - k, i.iy(ex + ox, ey + oy, 2.8f), Pal.WATER_L) // the splash
        }
    }

    /** A lamp on an iron post, lit from dusk. */
    private fun lamp(x: Float, y: Float) {
        i.post(x, y, 0f, 9f, IRON)
        val on = env.windows > 0.2f
        val lx = i.ix(x, y); val ly = i.iy(x, y, 9f)
        s.sprite(lx, ly) {
            c.hline(lx - 1, lx + 1, ly - 3, IRON)
            if (on) glow { c.fillRect(lx - 1, ly - 2, 3, 2, Pal.WINDOW_LIT) } else c.fillRect(lx - 1, ly - 2, 3, 2, Pal.GLASS)
            c.set(lx, ly - 4, IRON)
        }
        if (on) s.light(lx.toFloat(), ly - 2f * k, 16f, 0.6f)
    }

    // ---------------------------------------------------------------- Toplar · the double hayrack

    /**
     * The great Slovene double hayrack, as the field's scene builds it (Pri toplarju): two racks of oak, each of four posts on
     * stone feet with six rails between them, under one roof of boards, the passage between them open for a cart below and
     * boarded above for a hayloft; hay drying on the racks from June to November. In a culture's look
     * ([MapLook.toplar], as the field's scene builds it): Friuli's with bunches of golden maize drying from its front tie
     * beam from August to March; Carinthia's Doppelharpfe, its two racks of larch weathered silver-grey, taller, nine thin
     * rails close together like a harp's strings, under a steeper roof of dark shingles; the Lake District's bank barn.
     */
    private fun toplar(x: Float, y: Float, look: BuildLook) {
        val kind = s.look.toplar
        if (kind == Toplar.BANK) { bankBarn(x, y, look); return }
        val harpfe = kind == Toplar.DOPPELHARPFE
        val wL = if (harpfe) Pal.HARPFE_L else Pal.WOOD_L; val wM = if (harpfe) Pal.HARPFE_M else Pal.WOOD_M
        val wD = if (harpfe) Pal.HARPFE_D else Pal.WOOD_D; val wX = if (harpfe) Pal.HARPFE_X else Pal.WOOD_X
        val rungs = if (harpfe) HARPFE_RUNGS else RUNGS
        val x0 = x + 0.35f; val len = 3.7f; val yb = y + 0.5f; val yf = y + 1.7f; val top = if (harpfe) 13f else 12f
        val bays = HAY.size
        shadow(x0, yb, len, yf - yb)
        fun posts(yy: Float) {
            for (j in 0..bays) {
                val px = x0 + j * len / bays
                i.box(px - 0.15f, yy - 0.15f, 0f, 0.3f, 0.3f, 0.7f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D) // its stone foot
                i.box(px - 0.1f, yy - 0.1f, 0.7f, 0.2f, 0.2f, top - 0.7f, wL, wM, wD)
            }
        }
        if (look == BuildLook.FRAME) {
            posts(yb)
            i.line(x0, yb, top - 0.5f, x0 + len, yb, top - 0.5f, wD)
            i.line(x0 + len, yb, top - 0.5f, x0 + len, yf, top - 0.5f, wD)
            posts(yf)
            i.line(x0, yf, top - 0.5f, x0 + len, yf, top - 0.5f, wL)
            i.line(x0, yf, 1f, x0 + len, yf, 1f, wM)
            return
        }
        val done = look == BuildLook.DONE
        val hay = done && env.month in 6..11
        // the back rack, seen through the passage
        i.panelY(x0, yb, 0.5f, len, top - 1f, INTERIOR)
        for (z in rungs) i.line(x0, yb, z, x0 + len, yb, z, wM)
        if (hay) for (j in 0 until 14) i.px(x0 + Noise.rnd(j, 81) * len, yb, 1.5f + Noise.rnd(j, 82) * 5f, Pal.HAY_D)
        posts(yb)
        // the gable end: open below for the cart, the hayloft's boards above with a hatch
        val loft = if (harpfe) 7.5f else 7f
        i.panelX(x0 + len, yb, 0.5f, yf - yb, loft, INTERIOR)
        i.panelX(x0 + len, yb, loft, yf - yb, top - loft, wM)
        i.line(x0 + len, yb, loft, x0 + len, yf, loft, wX)
        if (fine) { var v = 0.2f; while (v < yf - yb) { i.line(x0 + len, yb + v, loft, x0 + len, yb + v, top, wD); v += 0.23f } }
        i.panelX(x0 + len, yb + 0.42f, loft + 1f, 0.35f, 2.5f, INTERIOR)
        // the front rack: the hay hung over its rungs to dry, the rungs across it
        if (hay) {
            val bay = len / bays
            for ((j, h) in HAY.withIndex()) if (h > 0f) i.panelY(x0 + j * bay + 0.06f, yf, 1f, bay - 0.12f, h, Pal.HAY_M)
            for (j in 0 until 12 * k) {
                val u = Noise.rnd(j, 83); val v = Noise.rnd(j, 84)
                val bj = j % bays; val h = HAY[bj]
                if (h > 0f) c.set(i.ix(x0 + bj * bay + 0.1f + u * (bay - 0.2f), yf), i.iy(x0 + bj * bay + 0.1f + u * (bay - 0.2f), yf, 1f + v * h), if (j % 2 == 0) Pal.HAY_L else Pal.HAY_D)
            }
        }
        for (z in rungs) {
            i.line(x0, yf, z, x0 + len, yf, z, wL)
            if (fine && !harpfe) i.line(x0, yf, z - 0.5f, x0 + len, yf, z - 0.5f, wD)
        }
        posts(yf)
        if (done) {
            if (harpfe) b.roofX(x0 - 0.35f, yb - 0.45f, top - 0.5f, len + 0.7f, yf - yb + 0.9f, 6.5f, b.shingles(), wX)
            else b.roofX(x0 - 0.35f, yb - 0.45f, top - 0.5f, len + 0.7f, yf - yb + 0.9f, 5f, b.planks(), Pal.WOOD_D)
            if (kind == Toplar.MAIZE && (env.month >= 8 || env.month <= 3)) maize(x0, len, yf + 0.05f, top - 0.6f, bays)
        } else roofFrameX(x0 - 0.35f, yb - 0.45f, top - 0.5f, len + 0.7f, yf - yb + 0.9f, 5f)
    }

    /**
     * Friuli's maize drying on the toplar (as the field's, il grande fienile's): a bunch of golden cobs hung by their pale
     * husks from the front tie beam at [z] in each bay, two in each from August to December.
     */
    private fun maize(x0: Float, len: Float, yf: Float, z: Float, bays: Int) {
        val two = env.month in 8..12
        val bay = len / bays
        for (j in 0 until bays) for (n in 0 until if (two) 2 else 1) {
            val mx = x0 + j * bay + bay * (if (two) 0.3f + 0.4f * n else 0.5f)
            i.px(mx, yf, z, MAIZE_HUSK)
            i.line(mx, yf, z - 0.8f, mx, yf, z - 2.6f, MAIZE)
            if (fine) { i.px(mx + 0.06f, yf, z - 1.2f, MAIZE_L); i.px(mx - 0.04f, yf, z - 2.2f, MAIZE_D) }
        }
    }

    /**
     * The Lake District's bank barn, as At the bank barn builds it: a long barn of slate stone under slate built into the
     * slope, the ramp of earth along its front, held by a dry-stone wall, up to the cart door of the upper floor at its left
     * end (the hay goes in there), the byre's two green doors under their lintels and a small window below, slits above.
     */
    private fun bankBarn(x: Float, y: Float, look: BuildLook) {
        val st = s.look.style ?: return
        val x0 = x + 0.35f; val y0 = y + 0.45f; val len = 3.6f; val d = 1.5f; val h = 9f
        val t = st.walls[0]
        shadow(x0, y0, len, d)
        if (look == BuildLook.FRAME) {
            b.looks.stoneBox(x0, y0, 0f, len, d, 4.5f, t, 71, quoins = false)
            i.panelY(x0 + 0.6f, y0 + d, 0f, 0.5f, 3f, INTERIOR)
            stoneHeap(x0 + len - 1.1f, y0 + d + 0.1f)
            return
        }
        val done = look == BuildLook.DONE
        // the slope it is dug into, against its left end
        val grass = if (env.snow) Pal.SNOW_L else env.grass[0]
        val bank = if (env.snow) Pal.SNOW_M else env.grass[1]
        i.quad(x0 - 1.0f, y0 + 0.2f, 0f, x0, y0 + 0.2f, 4.5f, x0, y0 + 1.3f, 4.5f, x0 - 1.0f, y0 + 1.3f, 0f, grass)
        i.panelY(x0 - 1.0f, y0 + 1.3f, 0f, 0.02f, 0.1f, bank)
        i.tri(x0 - 1.0f, y0 + 1.3f, 0f, x0, y0 + 1.3f, 0f, x0, y0 + 1.3f, 4.5f, bank)
        b.looks.stoneBox(x0, y0, 0f, len, d, h, t, 73)
        val fy = y0 + d
        val lintel = Col.mix(t[0], Col.hex(0xFFFFFF), 0.3f)
        // the byre's two doors, painted green, under their lintels, a small window between them; the upper floor's slits
        for (u in floatArrayOf(len * 0.48f - 0.25f, len * 0.82f - 0.25f)) {
            i.panelY(x0 + u, fy, 0f, 0.5f, 3.6f, if (done) BYRE else BARN)
            if (fine && done) i.line(x0 + u + 0.25f, fy, 0f, x0 + u + 0.25f, fy, 3.6f, Col.scale(BYRE, 0.78f))
            i.line(x0 + u - 0.08f, fy, 3.9f, x0 + u + 0.58f, fy, 3.9f, lintel)
        }
        i.panelY(x0 + len * 0.65f - 0.1f, fy, 1.8f, 0.2f, 1.2f, Col.hex(0x1E1A18))
        for (f in floatArrayOf(0.5f, 0.67f, 0.84f)) i.line(x0 + len * f, fy, 5.3f, x0 + len * f, fy, 7.5f, Col.hex(0x1E1A18))
        // the cart door of the upper floor at the front's left end, the ramp's top: its oak frame, dark inside, the hay in it
        i.panelY(x0 + 0.2f, fy, 4.6f, 0.8f, 3.4f, Pal.WOOD_D)
        i.panelY(x0 + 0.27f, fy, 4.6f, 0.66f, 3.1f, Col.hex(0x241E1C))
        if (done) { i.panelY(x0 + 0.3f, fy, 4.6f, 0.6f, 1.6f, Pal.HAY_M); i.px(x0 + 0.55f, fy, 5.8f, Pal.HAY_L) }
        if (done) b.roofX(x0 - 0.25f, y0 - 0.3f, h, len + 0.5f, d + 0.6f, 5.5f, b.slate(), t[2])
        else roofFrameX(x0 - 0.25f, y0 - 0.3f, h, len + 0.5f, d + 0.6f, 5.5f)
        // the ramp of earth along the front up to the cart door, grass on its top, held toward us by a dry-stone wall
        if (done || look == BuildLook.SCAFFOLD) {
            val rx0 = x0 - 1.1f; val rx1 = x0 + 0.2f; val ry = fy + 0.55f
            i.quad(rx0, fy, 0f, rx1, fy, 4.5f, rx1, ry, 4.5f, rx0, ry, 0f, grass)
            i.tri(rx0, ry, 0f, rx1, ry, 0f, rx1, ry, 4.5f, Pal.STONE_M)
            i.panelX(rx1, fy, 0f, 0.55f, 4.5f, Pal.STONE_D)
            if (fine) for (q in 1..3) {
                val z = q * 1.1f
                i.line(rx0 + (rx1 - rx0) * z / 4.5f, ry, z, rx1, ry, z, Pal.STONE_X)
                i.line(rx1, fy, z, rx1, ry, z, Pal.STONE_X)
            }
        }
    }

    // ---------------------------------------------------------------- Razgledni stolp · the lookout tower

    /**
     * A tall larch lattice tower, its four legs leaning in, braced in three storeys, stairs zigzagging up inside, an
     * open platform with a railing under a little tiled pyramid roof.
     */
    private fun stolp(x: Float, y: Float, look: BuildLook) {
        if (s.look.style?.stone == true) { station(x, y, look); return }
        val f0 = 0.15f; val f1 = 1.65f; val h0 = 0.55f; val h1 = 1.25f; val hH = 32f
        val up = if (look == BuildLook.FRAME) 16f else hH
        fun pt(fu: Float, fv: Float, hu: Float, hv: Float, z: Float) = floatArrayOf(x + fu + (hu - fu) * z / hH, y + fv + (hv - fv) * z / hH)
        fun leg(fu: Float, fv: Float, hu: Float, hv: Float, col: Int) { val p = pt(fu, fv, hu, hv, up); rod(x + fu, y + fv, 0f, p[0], p[1], up, col) }
        for (p in arrayOf(floatArrayOf(f0, f0), floatArrayOf(f1, f0), floatArrayOf(f0, f1), floatArrayOf(f1, f1))) {
            i.box(x + p[0] - 0.15f, y + p[1] - 0.15f, 0f, 0.3f, 0.3f, 1f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        }
        leg(f0, f0, h0, h0, Pal.WOOD_M)
        if (fine) {
            // the stairs inside, back and forth
            var z = 0f; var side = 0
            while (z < up - 1f) {
                val z1 = minOf(z + 5.3f, up)
                val a = pt(0.7f, 0.7f, 0.8f, 0.8f, z); val b2 = pt(1.1f, 1.1f, 0.95f, 0.95f, z1)
                if (side == 0) i.line(a[0], a[1] + 0.3f, z, b2[0], b2[1] - 0.2f, z1, Pal.WOOD_L) else i.line(b2[0], b2[1] - 0.2f, z, a[0], a[1] + 0.3f, z1, Pal.WOOD_L)
                z = z1; side = 1 - side
            }
        }
        leg(f1, f0, h1, h0, Pal.WOOD_L); leg(f0, f1, h0, h1, Pal.WOOD_M)
        val zs = if (look == BuildLook.FRAME) floatArrayOf(0f, 8f, 16f) else floatArrayOf(0f, 11f, 22f, hH)
        for (q in 0 until zs.size - 1) {
            val za = zs[q]; val zb = zs[q + 1]
            // the face we see on the left (+y) and the one on the right (+x): a cross in each storey, a tie at its top
            val l0 = pt(f0, f1, h0, h1, za); val l1 = pt(f1, f1, h1, h1, za); val u0 = pt(f0, f1, h0, h1, zb); val u1 = pt(f1, f1, h1, h1, zb)
            val r0 = pt(f1, f0, h1, h0, za); val r1 = pt(f1, f0, h1, h0, zb)
            i.line(r0[0], r0[1], za, u1[0], u1[1], zb, Pal.WOOD_X); i.line(l1[0], l1[1], za, r1[0], r1[1], zb, Pal.WOOD_X)
            i.line(r1[0], r1[1], zb, u1[0], u1[1], zb, Pal.WOOD_M)
            i.line(l0[0], l0[1], za, u1[0], u1[1], zb, Pal.WOOD_D); i.line(l1[0], l1[1], za, u0[0], u0[1], zb, Pal.WOOD_D)
            i.line(u0[0], u0[1], zb, u1[0], u1[1], zb, Pal.WOOD_L)
        }
        leg(f1, f1, h1, h1, Pal.WOOD_L)
        if (look == BuildLook.FRAME) return
        i.box(x + 0.35f, y + 0.35f, hH, 1.1f, 1.1f, 1.2f, if (env.snow) Pal.SNOW_L else Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        val zp = hH + 1.2f
        if (look == BuildLook.SCAFFOLD) {
            // hauling up the last beams: a rope from a jib at the top
            i.line(x + 1.45f, y + 1.45f, zp, x + 1.9f, y + 1.9f, zp + 2f, Pal.WOOD_D)
            i.line(x + 1.9f, y + 1.9f, zp + 2f, x + 1.9f, y + 1.9f, 3f, Pal.CANVAS_M)
            i.box(x + 1.6f, y + 1.8f, 2f, 0.6f, 0.15f, 0.8f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
            return
        }
        i.post(x + 0.42f, y + 0.42f, zp, zp + 5f, Pal.WOOD_D)
        i.post(x + 1.38f, y + 0.42f, zp, zp + 5f, Pal.WOOD_D); i.post(x + 0.42f, y + 1.38f, zp, zp + 5f, Pal.WOOD_D)
        i.line(x + 0.35f, y + 1.45f, zp + 2.5f, x + 1.45f, y + 1.45f, zp + 2.5f, Pal.WOOD_L)
        i.line(x + 1.45f, y + 0.35f, zp + 2.5f, x + 1.45f, y + 1.45f, zp + 2.5f, Pal.WOOD_M)
        if (fine) {
            i.line(x + 0.35f, y + 1.45f, zp + 1.2f, x + 1.45f, y + 1.45f, zp + 1.2f, Pal.WOOD_M)
            for (j in 1..4) { val u = 0.35f + j * 0.22f; i.line(x + u, y + 1.45f, zp, x + u, y + 1.45f, zp + 2.5f, Pal.WOOD_M); i.line(x + 1.45f, y + u, zp, x + 1.45f, y + u, zp + 2.5f, Pal.WOOD_D) }
        }
        i.post(x + 1.38f, y + 1.38f, zp, zp + 5f, Pal.WOOD_D)
        // its roof of shingles, as its scene's (the village's in a culture's look)
        val roof = if (s.look.own) b.shingles() else b.villageRoof()
        i.pyramid(x + 0.2f, y + 0.2f, zp + 5f, 1.4f, 1.4f, 6f, if (env.snow) Pal.SNOW_L else roof.back, if (env.snow) Pal.SNOW_M else roof.front)
        if (fine && !env.snow && s.look.own) for (j in 1..2) {
            val t = j / 3f; val zz = zp + 5f + t * 6f; val a = 0.2f + t * 0.7f; val bb = 1.6f - t * 0.7f
            i.line(x + a, y + bb, zz, x + bb, y + bb, zz, Col.scale(Pal.SHINGLE_L, 0.86f)); i.line(x + bb, y + a, zz, x + bb, y + bb, zz, Col.scale(Pal.SHINGLE_D, 0.85f))
        }
        i.post(x + 0.9f, y + 0.9f, zp + 11f, zp + 13f, IRON)
        // the village's flag on the roof, as its lookout's scene flies it (Slovenia's, Friuli's eagle, Austria's)
        roofFlag(x + 0.9f, y + 0.9f, zp + 13f)
    }

    /** The village's flag ([MapLook.flag]) on its pole at ([fx], [fy], [z]), the pole's gold knob over it. */
    private fun roofFlag(fx: Float, fy: Float, z: Float) {
        val flag = s.look.flag
        val ax = i.ix(fx, fy); val ay = i.iy(fx, fy, z)
        val wave = ((s.time * 3).toInt() and 1)
        s.sprite(ax, ay) {
            c.vline(ax, ay - 3, ay, IRON)
            c.set(ax, ay - 4, Pal.GOLD)
            for (u in 0 until 4) for (v in 0 until 3) c.set(ax + 1 + u, ay - 3 + v + if (u == 3) wave else 0, flag.at(u, v, 4, 3))
        }
    }

    /**
     * The Lake District's viewing station (as the first tourists had them, Claife's over Windermere): a little house of stone
     * on its rock, under a slate pyramid with St George's cross on its pole, its windows of coloured glass (each pane a
     * colour, as the lake was seen through them: green, yellow, orange and blue as its scene's), lit at night.
     */
    private fun station(x: Float, y: Float, look: BuildLook) {
        val st = s.look.style ?: return
        val t = st.walls[0]
        val x0 = x + 0.4f; val y0 = y + 0.4f; val bw = 1.2f
        // the rock it stands on
        i.box(x0 - 0.25f, y0 - 0.2f, 0f, bw + 0.5f, bw + 0.45f, 3f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        i.box(x0 + 0.2f, y0 + bw + 0.25f, 0f, 0.6f, 0.4f, 1.5f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        if (look == BuildLook.FRAME) { b.looks.stoneBox(x0, y0, 3f, bw, bw, 3f, t, 83, quoins = false); return }
        val done = look == BuildLook.DONE
        if (!done) scaffoldBack(x0, y0, 14f)
        b.looks.stoneBox(x0, y0, 3f, bw, bw, 8f, t, 83)
        // the coloured panes, two on each side we see
        // as its scene's: green for spring, yellow for summer, orange for autumn, blue for winter
        val panes = intArrayOf(Col.hex(0x5CC840), Col.hex(0xF8D820), Col.hex(0xFF7A10), Col.hex(0x3A80E8))
        for ((n, u) in floatArrayOf(0.2f, 0.7f).withIndex()) {
            val a = panes[n]; val bb = panes[n + 2]
            if (lit && done) glow { i.panelY(x0 + u, y0 + bw, 5.5f, 0.3f, 3f, Col.mix(a, Pal.WINDOW_LIT, 0.45f)); i.panelX(x0 + bw, y0 + u, 5.5f, 0.3f, 3f, Col.mix(bb, Pal.WINDOW_LIT, 0.45f)) }
            else { i.panelY(x0 + u, y0 + bw, 5.5f, 0.3f, 3f, Col.scale(a, 0.8f)); i.panelX(x0 + bw, y0 + u, 5.5f, 0.3f, 3f, Col.scale(bb, 0.7f)) }
            if (fine) { i.line(x0 + u + 0.15f, y0 + bw, 5.5f, x0 + u + 0.15f, y0 + bw, 8.5f, CultureBuildings.WHITE_FRAME); i.line(x0 + bw, y0 + u + 0.15f, 5.5f, x0 + bw, y0 + u + 0.15f, 8.5f, CultureBuildings.WHITE_FRAME) }
        }
        if (lit && done) s.light(i.sx(x0 + bw, y0 + bw), i.sy(x0 + bw, y0 + bw, 7f), 14f, 0.5f * env.windows)
        if (done) {
            val r = b.slate()
            i.pyramid(x0 - 0.15f, y0 - 0.15f, 11f, bw + 0.3f, bw + 0.3f, 4.5f, r.back, r.front)
            i.post(x0 + bw / 2f, y0 + bw / 2f, 15.5f, 17f, IRON)
            roofFlag(x0 + bw / 2f, y0 + bw / 2f, 17f) // St George's cross, as At the viewing station flies it
        } else scaffoldFront(x0, y0, bw, bw, 14f)
    }

    // ---------------------------------------------------------------- Mestna ura · the town clock

    /**
     * The church's tower with the town clock, as the square's scene has it (Pod uro): a slender white tower on a stone foot,
     * a clock face on each side we see (telling the real time, lit at night), a belfry, and a spire of dark slate with a gold
     * ball and cross on top; Carinthia's white under its onion dome of green copper ([MapLook.clock] [Tower.ONION]).
     */
    private fun ura(x: Float, y: Float, look: BuildLook) {
        val x0 = x + 0.2f; val y0 = y + 0.2f; val tw = 1.2f
        shadow(x0, y0, tw, tw)
        val tower = s.look.clock
        if (tower != null && tower != si.lanisce.lani.game.render.scene.Tower.ONION && look != BuildLook.FRAME) { clockTower(x0, y0, tw, tower, look); return }
        val onion = s.look.style?.takeIf { tower == si.lanisce.lani.game.render.scene.Tower.ONION }
        val wall = onion?.towerWall ?: intArrayOf(Pal.WALL_L, Pal.WALL_M, Pal.WALL_D)
        if (look == BuildLook.FRAME) {
            i.box(x0, y0, 0f, tw, tw, 14f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
            b.mortar(x0, y0, 0f, tw, tw, 14f, Pal.STONE_D, Pal.STONE_X)
            if (!fine) for (z in intArrayOf(4, 9)) { i.line(x0, y0 + tw, z.toFloat(), x0 + tw, y0 + tw, z.toFloat(), Pal.STONE_D); i.line(x0 + tw, y0, z.toFloat(), x0 + tw, y0 + tw, z.toFloat(), Pal.STONE_X) }
            i.panelY(x0 + 0.4f, y0 + tw, 0f, 0.4f, 3.5f, INTERIOR)
            return
        }
        val done = look == BuildLook.DONE
        if (!done) scaffoldBack(x0, y0, 30f)
        i.box(x0 - 0.05f, y0 - 0.05f, 0f, tw + 0.1f, tw + 0.1f, 4f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        b.mortar(x0 - 0.05f, y0 - 0.05f, 0f, tw + 0.1f, tw + 0.1f, 4f, Pal.STONE_D, Pal.STONE_X)
        i.box(x0, y0, 4f, tw, tw, 22f, wall[0], wall[1], wall[2])
        if (fine) {
            // the corner stones up the front corner and a string course of stone
            var z = 5f; var n = 0
            while (z < 25f) { i.line(x0 + tw - if (n % 2 == 0) 0.3f else 0.17f, y0 + tw, z, x0 + tw, y0 + tw, z, Col.mix(wall[0], Pal.STONE_L, 0.5f)); z += 1.2f; n++ }
            i.line(x0, y0 + tw, 15f, x0 + tw, y0 + tw, 15f, Pal.STONE_L); i.line(x0 + tw, y0, 15f, x0 + tw, y0 + tw, 15f, Pal.STONE_M)
        }
        i.panelY(x0 + 0.4f, y0 + tw + 0.05f, 0f, 0.4f, 3.5f, Pal.DOOR)
        for (face in 0..1) {
            val on = lit && done
            val col = if (on) Pal.WINDOW_LIT else Pal.GLASS
            if (on) glow { if (face == 0) i.panelY(x0 + 0.5f, y0 + tw, 8.5f, 0.2f, 2.5f, col) else i.panelX(x0 + tw, y0 + 0.5f, 8.5f, 0.2f, 2.5f, col) }
            else if (face == 0) i.panelY(x0 + 0.5f, y0 + tw, 8.5f, 0.2f, 2.5f, col) else i.panelX(x0 + tw, y0 + 0.5f, 8.5f, 0.2f, 2.5f, col)
        }
        i.box(x0 - 0.1f, y0 - 0.1f, 26f, tw + 0.2f, tw + 0.2f, 1f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        clockFace(x0 + tw / 2f, y0 + tw, 21f, 0.5f, blank = !done)
        clockFace(x0 + tw, y0 + tw / 2f, 21f, -0.5f, blank = !done)
        // the belfry
        i.box(x0 + 0.05f, y0 + 0.05f, 27f, tw - 0.1f, tw - 0.1f, 6f, if (env.snow && !done) Pal.SNOW_L else wall[0], wall[1], wall[2])
        i.panelY(x0 + 0.35f, y0 + tw - 0.05f, 28f, 0.5f, 3.5f, INTERIOR); i.panelX(x0 + tw - 0.05f, y0 + 0.35f, 28f, 0.5f, 3.5f, INTERIOR)
        if (done) {
            i.px(x0 + 0.55f, y0 + tw - 0.05f, 29.5f, Pal.GOLD)
            if (onion != null) TowerTops.onion(s, i.ix(x0 + tw / 2f, y0 + tw / 2f), i.iy(x0 + tw / 2f, y0 + tw / 2f, 33f), onion.towerTop)
            else spire(x0, y0, tw, 33f)
        } else scaffoldFront(x0, y0, tw, tw, 30f)
    }

    /** The church tower's spire of dark slate over [z], as the square's scene has it, a gold ball and cross on its tip. */
    private fun spire(x0: Float, y0: Float, tw: Float, z: Float) {
        val h = 9f
        i.pyramid(x0, y0, z, tw, tw, h, if (env.snow) Pal.SNOW_L else SPIRE_L, if (env.snow) Pal.SNOW_M else SPIRE_D)
        val ax = i.ix(x0 + tw / 2f, y0 + tw / 2f); val top = i.iy(x0 + tw / 2f, y0 + tw / 2f, z + h)
        s.sprite(ax, top) {
            c.set(ax, top - 1, Pal.GOLD_L); c.set(ax - 1, top - 1, Pal.GOLD)
            c.vline(ax, top - 5, top - 2, Pal.GOLD); c.hline(ax - 1, ax + 1, top - 4, Pal.GOLD)
        }
    }

    /**
     * The town clock in a culture's tower ([MapLook.clock]): Friuli's in the brick campanile under its low cap, the Lake
     * District's in the church's square tower of stone with its battlements; the clock's faces on both sides we see.
     */
    private fun clockTower(x0: Float, y0: Float, tw: Float, tower: si.lanisce.lani.game.render.scene.Tower, look: BuildLook) {
        val st = s.look.style ?: return
        val done = look == BuildLook.DONE
        if (!done) scaffoldBack(x0, y0, 30f)
        if (tower == si.lanisce.lani.game.render.scene.Tower.CAMPANILE) {
            b.looks.campanile(x0, y0, tw, 28f, st.towerWall, st.towerTop, 89)
            i.panelY(x0 + 0.4f, y0 + tw, 0f, 0.4f, 3.5f, Pal.DOOR)
            clockFace(x0 + tw / 2f, y0 + tw, 16f, 0.5f, blank = !done)
            clockFace(x0 + tw, y0 + tw / 2f, 16f, -0.5f, blank = !done)
        } else {
            TowerTops.battlemented(s, b, x0, y0, tw, 22f, st.towerWall, 89)
            i.panelY(x0 + 0.4f, y0 + tw, 0f, 0.4f, 3.5f, Pal.DOOR)
            clockFace(x0 + tw / 2f, y0 + tw, 12.5f, 0.5f, blank = !done)
            clockFace(x0 + tw, y0 + tw / 2f, 12.5f, -0.5f, blank = !done)
        }
        if (!done) scaffoldFront(x0, y0, tw, tw, 30f)
    }

    /**
     * A clock face on a tower's side: a disc on the wall (sheared like the wall, [shear] px down per px right), the hour and
     * minute hands at the frame's hour; lit at night. [blank]: the round hole the clock will go in.
     */
    private fun clockFace(wx: Float, wy: Float, z: Float, shear: Float, blank: Boolean) {
        val ax = i.ix(wx, wy); val ay = i.iy(wx, wy, z)
        val r = 2.4f * k
        val night = lit && !blank
        val slate = s.look.slateClock
        val face = if (blank) INTERIOR else if (slate) SLATE_FACE else if (night) Pal.WINDOW_LIT_HI else Pal.FLAG_WHITE
        val rim = if (blank) INTERIOR else Pal.GOLD
        val was = c.penEmissive
        if (night && !slate) c.penEmissive = true
        val n = ceil(r).toInt()
        for (v in -n..n) for (u in -n..n) {
            val d2 = u * u + v * v
            if (d2 > r * r) continue
            val col = if (k >= 2 && d2 > (r - 1f) * (r - 1f)) rim else face
            c.set(ax + u, ay + v + (shear * u).roundToInt(), col)
        }
        if (!blank) {
            val hands = if (slate) Pal.GOLD else IRON
            if (finest) for (q in 0 until 12) {
                val a = q * PI / 6
                val u = (sin(a) * (r - 2f)).roundToInt(); val v = (-cos(a) * (r - 2f)).roundToInt()
                if (q % 3 == 0) c.set(ax + u, ay + v + (shear * u).roundToInt(), hands)
            }
            val h = ((s.hour % 24f) + 24f) % 24f
            hand(ax, ay, shear, (h % 12f) / 12f * 2 * PI, r * 0.5f, hands)
            hand(ax, ay, shear, (h - floor(h)) * 2 * PI, r * 0.8f, hands)
            c.set(ax, ay, hands)
        }
        c.penEmissive = was
        if (night) s.light(ax.toFloat(), ay.toFloat(), 8f, 0.35f)
    }

    private fun hand(ax: Int, ay: Int, shear: Float, a: Double, len: Float, col: Int) {
        val steps = max(1, len.roundToInt())
        for (q in 0..steps) {
            val u = (sin(a) * len * q / steps).roundToInt(); val v = (-cos(a) * len * q / steps).roundToInt()
            c.set(ax + u, ay + v + (shear * u).roundToInt(), col)
        }
    }

    // ---------------------------------------------------------------- a landmark the renderer doesn't know

    /** A memorial stone with a bronze plaque. */
    private fun monument(x: Float, y: Float, w: Float, d: Float, look: BuildLook) {
        val cx = x + w / 2f; val cy = y + d / 2f
        if (look == BuildLook.FRAME) { stoneHeap(cx - 0.6f, cy - 0.55f); return }
        i.box(cx - 0.45f, cy - 0.45f, 0f, 0.9f, 0.9f, 1f, Pal.STONE_M, Pal.STONE_D, Pal.STONE_X)
        i.box(cx - 0.3f, cy - 0.25f, 1f, 0.6f, 0.5f, 7f, if (env.snow) Pal.SNOW_L else Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        i.panelY(cx - 0.2f, cy + 0.25f, 3.5f, 0.4f, 2.5f, BRONZE)
        if (look == BuildLook.SCAFFOLD) scaffoldFront(cx - 0.3f, cy - 0.25f, 0.6f, 0.5f, 8f)
    }

    private companion object {
        val STRIPPED_L = Col.hex(0xE2C696); val STRIPPED_D = Col.hex(0xB38A58)
        val END = Col.hex(0xE0C48A)
        val GREEN_D = Col.hex(0x2E6A2E)
        val SAND_L = Col.hex(0xEAD9A8); val SAND_M = Col.hex(0xD8C08A); val SAND_D = Col.hex(0xB99F6A)
        val GATE = Col.hex(0xA8322A); val GATE_D = Col.hex(0x7E241E)
        val NICHE = Col.hex(0x3A2A30)
        val MANTLE = Col.hex(0x2F5FA8)
        val GRAPE = Col.hex(0x4A2A5A); val GRAPE_L = Col.hex(0x7A4A8A)
        val VINE_L = Col.hex(0x6DBF4A)
        val INTERIOR = Col.hex(0x3A2A1E)
        /** The belfry's dark inside, through its arches. */
        val BELFRY = Col.hex(0x2A2026)
        val IRON = Col.hex(0x2E2A34)
        val ROPE = Col.hex(0x8C7A50)
        /** The bocce balls of steel, one side's a shade darker, the wooden jack, as At the bocce court's; the Stockbahn's Daube. */
        val BALL_STEEL = Col.hex(0xB8BCC4); val BALL_STEEL_D = Col.hex(0x8A8E98); val JACK = Col.hex(0xC8A060); val DAUBE = Col.hex(0x1E1E22)
        val BRONZE = Col.hex(0xB08A2E)
        /** The Stockschießbahn's lane, the bowling green's two mown greens, its woods. */
        val LANE_L = Col.hex(0xB8B8BE); val LANE_M = Col.hex(0x7A7A82)
        val GREEN_L = Col.hex(0x6FB452); val GREEN_M = Col.hex(0x5A9E44)
        val WOOD_BALL = Col.hex(0x4A3428); val WOOD_BALL_D = Col.hex(0x2A2020)
        /** The bank barn's doors before they're painted, and the byre's painted green (its scene's). */
        val BARN = Col.hex(0x4E3A2A); val BYRE = Col.hex(0x5E7A5A)
        /** The fruit: apples, pears, damsons. */
        val APPLE = Col.hex(0xD8342A); val PEAR = Col.hex(0xD8C84A); val DAMSON = Col.hex(0x4A2A5A)
        /** Where the orchard's trees stand on its patch (fractions of it). */
        val ORCHARD = arrayOf(floatArrayOf(0.18f, 0.25f), floatArrayOf(0.5f, 0.2f), floatArrayOf(0.82f, 0.3f), floatArrayOf(0.3f, 0.72f), floatArrayOf(0.66f, 0.75f))

        /** How high the hay hangs in each of the toplar's three bays of its front rack (px), the first hay in it. */
        val HAY = floatArrayOf(9.5f, 8f, 9f)

        /** The heights of the toplar's six rails (px), as the field's (0.85 to 4.05 m under its eave). */
        val RUNGS = floatArrayOf(2.1f, 3.7f, 5.3f, 6.9f, 8.4f, 9.9f)
        /** The Doppelharpfe's nine rails (px), close together like a harp's strings (Carinthia's toplar). */
        val HARPFE_RUNGS = floatArrayOf(1.4f, 2.6f, 3.8f, 5f, 6.2f, 7.4f, 8.6f, 9.8f, 11f)
        /** Friuli's maize drying on the toplar: the cobs' gold, lit and dark, and the husks they hang by (the field's). */
        val MAIZE = Col.hex(0xE88A18); val MAIZE_L = Col.hex(0xF8B032); val MAIZE_D = Col.hex(0xA85A10); val MAIZE_HUSK = Col.hex(0xF0E6C4)

        /**
         * The bridge: how thick its pillars are along the road (cells) and how high they stand, its deck's underside and
         * top (px): it lies low and flat over the water, the road going on at both its ends.
         */
        const val PILLAR = 0.3f
        const val PILLAR_H = 0.8f
        const val DECK_UNDER = 0.4f
        const val DECK = 1.2f

        /** Carinthia's larch, light, mid and dark: reddish brown ([Bridge.LARCH]). */
        val LARCH = intArrayOf(Col.hex(0xC47E4C), Col.hex(0x9E5C36), Col.hex(0x6E3C22))

        /** Friuli's piers of river cobbles ([Bridge.COBBLES]): how thick along the road (cells) and how high (px), and their brick. */
        const val COBBLE_PIER = 0.46f
        const val COBBLE_PIER_H = 1.5f
        val BRICK_L = Col.hex(0xD08050)
        val BRICK = Col.hex(0xB0603E)
        val BRICK_D = Col.hex(0x8C4A2C)

        /**
         * The packhorse bridge ([Bridge.PACKHORSE]): its walls on the banks (cells along the way, px high), its arch's
         * underside from its foot to its crown, how far its top humps over the walls, its parapets (cells thick, px high).
         */
        const val ABUTMENT = 0.42f
        const val WALL_H = 2.6f
        const val ARCH_FOOT = 0.2f
        const val ARCH_TOP = 2.9f
        const val HUMP = 1.5f

        /** The water in the arch's shade, under its opening. */
        val ARCH_SHADE = Col.hex(0x23394E)
        const val PARAPET = 0.14f
        const val PARAPET_H = 1.3f

        /** Lakeland slate, light, mid and dark, its joints, and the way over it (worn paler). */
        val SLATE = intArrayOf(Col.hex(0x8C929C), Col.hex(0x6C727E), Col.hex(0x4E525C))
        val SLATE_D = Col.hex(0x3C4048)
        val SLATE_WAY = Col.hex(0xA4A49A)

        /** The church tower's spire of slate, its lit and its shaded side (the square's scene's). */
        val SPIRE_L = Col.hex(0x5C5560); val SPIRE_D = Col.hex(0x3E3842)
        /** Friuli's fontanella, green cast iron, and its lit side. */
        val FONTANELLA = Col.hex(0x2E5E3A); val FONTANELLA_L = Col.hex(0x4E8A5A)
        /** The fountain's white limestone, lit, mid, in shade and its joints; the Lake District's green Honister slate (the square's). */
        val FOUNT_LIME = intArrayOf(Col.hex(0xF6F3EA), Col.hex(0xE4DED0), Col.hex(0xC8C0AE), Col.hex(0xA49A86))
        val FOUNT_SLATE = intArrayOf(Col.hex(0x9AAE9C), Col.hex(0x7E9282), Col.hex(0x627668), Col.hex(0x46564C))
        /** St Florian on Carinthia's fountain: his armour and his red cloak. */
        val FLORIAN_ARMOUR = Col.hex(0xB4B8C0); val FLORIAN_CLOAK = Col.hex(0xC0302C)
        /** The Lake District's clock face of dark slate (its square's). */
        val SLATE_FACE = Col.hex(0x3A4250)
    }
}
