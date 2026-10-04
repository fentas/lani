package si.lanisce.lani.game.render

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

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
        if (look == BuildLook.SITE && m.id != "most") { site(x, y, w, d, LandmarkLayout.shapeOf(m.id).stone); return }
        when (m.id) {
            "mlaj" -> mlaj(x, y, look)
            "most" -> most(x, y, w, d, look)
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
            else -> {
                wreath(cx, cy, top - 6f, front = false)
                pole(cx, cy, top)
                wreath(cx, cy, top - 6f, front = true)
                ribbons(cx, cy, top - 6f)
            }
        }
        stones(cx, cy, back = false)
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
        val cols = intArrayOf(Pal.FLAG_RED, Pal.FLAG_WHITE, Pal.FLAG_BLUE, Pal.GOLD, Pal.PAINTED[3])
        for (j in 0 until 5) {
            val a = -0.6 + j * 0.55
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

    // ---------------------------------------------------------------- Most · the footbridge

    /**
     * A stone arch over the stream, humped, with low parapets; the water shows dark under the arch. Laid across the
     * stream along x ([w]), [d] wide.
     */
    private fun most(x: Float, y: Float, w: Float, d: Float, look: BuildLook) {
        val n = 12
        val y1 = y + d
        val span = w / 2f - 0.9f
        fun deck(u: Float) = 3.5f + 2.5f * sin(PI * u / w).toFloat()
        fun arch(u: Float): Float { val t = (u - w / 2f) / span; return if (abs(t) >= 1f) 0f else 3.4f * sqrt(1f - t * t) }
        fun u(j: Int) = w * j / n
        fun a(j: Int) = 0.9f + (w - 1.8f) * j / n
        if (look == BuildLook.SITE) {
            // the crossing measured out: pegs on both banks, a line over the water, stone on the near bank
            ground { i.line(x + 0.3f, y + d / 2f, 2f, x + w - 0.3f, y + d / 2f, 2f, if (env.snow) Pal.WOOD_X else Pal.CANVAS_L) }
            for (uu in floatArrayOf(0.3f, w - 0.3f)) { i.post(x + uu, y + 0.2f, 0f, 3f, Pal.WOOD_L); i.post(x + uu, y1 - 0.2f, 0f, 3f, Pal.WOOD_L) }
            i.box(x + w - 0.75f, y + 0.3f, 0f, 0.55f, 0.5f, 1.5f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
            i.box(x + w - 0.7f, y + 0.35f, 1.5f, 0.4f, 0.35f, 1f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
            return
        }
        if (look == BuildLook.FRAME) {
            centering(x, y + 0.15f, ::arch, ::a, n)
            abutment(x, y, 0.9f, d); abutment(x + w - 0.9f, y, 0.9f, d)
            centering(x, y1 - 0.15f, ::arch, ::a, n)
            return
        }
        val done = look == BuildLook.DONE
        val top = if (env.snow) Pal.SNOW_L else if (done) Pal.STONE_L else Pal.DIRT_M
        // the deck, and the back parapet on it
        for (j in 0 until n) i.quad(x + u(j), y, deck(u(j)), x + u(j + 1), y, deck(u(j + 1)), x + u(j + 1), y1, deck(u(j + 1)), x + u(j), y1, deck(u(j)), top)
        if (fine && done && !env.snow) for (j in 1 until n) i.line(x + u(j), y, deck(u(j)), x + u(j), y1, deck(u(j)), Pal.STONE_M) // the paving
        if (done) parapet(x, y + 0.15f, n, ::deck, ::u, Pal.STONE_M)
        // the front face with the arch through it
        for (j in 0 until n) i.quad(x + u(j), y1, 0f, x + u(j + 1), y1, 0f, x + u(j + 1), y1, deck(u(j + 1)), x + u(j), y1, deck(u(j)), Pal.STONE_M)
        if (fine) for (z in 1..5) for (j in 0 until n) {
            val z0 = z.toFloat()
            if (z0 < deck(u(j)) - 0.5f && z0 < deck(u(j + 1)) - 0.5f && z0 > arch(u(j)) + 1.2f && z0 > arch(u(j + 1)) + 1.2f) {
                i.line(x + u(j), y1, z0, x + u(j + 1), y1, z0, Pal.STONE_D)
            }
        }
        ground {
            val under = if (env.snow) Col.scale(Pal.ICE_M, 0.62f) else Col.scale(Pal.WATER_D, 0.62f)
            for (j in 0 until n) i.quad(x + a(j), y1, 0f, x + a(j + 1), y1, 0f, x + a(j + 1), y1, arch(a(j + 1)), x + a(j), y1, arch(a(j)), under)
        }
        for (j in 0 until n) i.line(x + a(j), y1, arch(a(j)) + 0.5f, x + a(j + 1), y1, arch(a(j + 1)) + 0.5f, Pal.STONE_L) // the ring of arch stones
        if (fine) for (j in 1 until n step 2) {
            val uu = a(j); val out = (uu - w / 2f) / span
            i.line(x + uu, y1, arch(uu) + 0.5f, x + uu + out * 0.2f, y1, arch(uu) + 1.8f, Pal.STONE_D)
        }
        if (!done) centering(x, y1 - 0.02f, ::arch, ::a, n)
        i.panelX(x + w, y, 0f, d, deck(w), Pal.STONE_D)
        if (done) parapet(x, y1, n, ::deck, ::u, Pal.STONE_M)
        else {
            i.post(x + 0.9f, y1 + 0.12f, 0f, 9f, Pal.WOOD_D); i.post(x + w - 0.9f, y1 + 0.12f, 0f, 9f, Pal.WOOD_D)
            i.line(x + 0.9f, y1 + 0.12f, 8.5f, x + w - 0.9f, y1 + 0.12f, 8.5f, Pal.WOOD_L)
        }
    }

    private fun abutment(x: Float, y: Float, w: Float, d: Float) {
        i.box(x, y, 0f, w, d, 3.5f, if (env.snow) Pal.SNOW_L else Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        b.mortar(x, y, 0f, w, d, 3.5f, Pal.STONE_D, Pal.STONE_X)
    }

    /** The wooden centering an arch is built on: its curve and the props under it. */
    private fun centering(x: Float, y: Float, arch: (Float) -> Float, at: (Int) -> Float, n: Int) {
        for (j in 0 until n) i.line(x + at(j), y, arch(at(j)), x + at(j + 1), y, arch(at(j + 1)), Pal.WOOD_L)
        for (j in intArrayOf(n / 4, n / 2, 3 * n / 4)) i.line(x + at(j), y, 0f, x + at(j), y, arch(at(j)), Pal.WOOD_M)
    }

    private fun parapet(x: Float, y: Float, n: Int, deck: (Float) -> Float, u: (Int) -> Float, col: Int) {
        for (j in 0 until n) i.quad(x + u(j), y, deck(u(j)), x + u(j + 1), y, deck(u(j + 1)), x + u(j + 1), y, deck(u(j + 1)) + 2f, x + u(j), y, deck(u(j)) + 2f, col)
        val coping = if (env.snow) Pal.SNOW_L else Pal.STONE_L
        for (j in 0 until n) i.line(x + u(j), y, deck(u(j)) + 2f, x + u(j + 1), y, deck(u(j + 1)) + 2f, coping)
    }

    // ---------------------------------------------------------------- Mlin · the mill on the stream

    /**
     * France's water mill on the village's bank of the stream: a timber house on a stone footing under a shingled roof,
     * a mill race dug off the stream past its front (its end toward the water, -x), the wheel standing in the race.
     * Half-built, the footing with the frame going up on it and the race dug, dry; then the walls under bare rafters in
     * scaffolding, the wheel standing still; finished, the race runs, the wheel turns, a sack of flour waits by the door
     * and the window glows at night.
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
        i.box(hx, hy, 0f, hw, hd, 2f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        if (fine) b.mortar(hx, hy, 0f, hw, hd, 2f, Pal.STONE_D, Pal.STONE_X)
        i.box(hx, hy, 2f, hw, hd, 6f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        if (fine) for (z in 3..7) {
            i.line(hx, hy + hd, z.toFloat(), hx + hw, hy + hd, z.toFloat(), Pal.WOOD_D)
            i.line(hx + hw, hy, z.toFloat(), hx + hw, hy + hd, z.toFloat(), Pal.WOOD_X)
        }
        // the door on the front, away from the wheel; a small window on the side, lit at night once it grinds
        i.panelY(hx + hw * 0.62f, hy + hd, 2f, 0.32f, 4.5f, Pal.DOOR)
        if (done && lit) {
            glow { i.panelX(hx + hw, hy + 0.3f, 4.5f, 0.3f, 2f, Pal.WINDOW_LIT) }
            s.light(i.sx(hx + hw, hy + 0.45f), i.sy(hx + hw, hy + 0.45f, 5.5f), 11f, 0.5f * env.windows)
        } else i.panelX(hx + hw, hy + 0.3f, 4.5f, 0.3f, 2f, Pal.GLASS)
        if (done) {
            b.roofX(hx - 0.2f, hy - 0.2f, 8f, hw + 0.4f, hd + 0.4f, 4.5f, b.shingles(), Pal.WOOD_L)
            if (!env.snow) { i.px(hx + hw * 0.62f + 0.45f, hy + hd + 0.1f, 1f, Pal.CANVAS_L); i.px(hx + hw * 0.62f + 0.45f, hy + hd + 0.1f, 2f, Pal.CANVAS_L) } // a sack of flour
        } else {
            roofFrameX(hx - 0.2f, hy - 0.2f, 8f, hw + 0.4f, hd + 0.4f, 4.5f)
            scaffoldFront(hx, hy, hw, hd, 11f)
        }
        // the wheel in the race by the house's front, its axle into the wall
        val cx = hx + 0.5f; val cz = 3.8f
        i.line(cx, hy + hd, cz, cx, ry, cz, IRON)
        millWheel(cx, ry, cz, 0.55f, 4f, turning = done)
        if (done && !env.snow) {
            // the water it throws up where it dips into the race
            val fx = i.ix(cx + 0.35f, ry); val fy = i.iy(cx + 0.35f, ry, 0.5f)
            s.sprite(fx, fy) { for (q in 0..2) c.set(fx + q, fy - (((s.time * 8).toInt() + q) % 3), if (q == 1) Pal.FLAG_WHITE else Pal.WATER_L) }
        }
    }

    /** An undershot wheel in the plane y = [y] round ([cx], [cz]): its rim, spokes and paddles, turning when [turning]. */
    private fun millWheel(cx: Float, y: Float, cz: Float, rx: Float, rz: Float, turning: Boolean) {
        val a0 = if (turning) (-s.time * 1.3).toFloat() else 0.3f
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
        val sand = if (env.snow) Pal.SNOW_M else SAND_M
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
        if (!env.snow) {
            if (fine) for (q in 1..3) line(0.35f, wide * q / 4f, 0.6f, len - 0.35f, wide * q / 4f, 0.6f, SAND_D) // raked
            else line(0.4f, wide / 2f, 0.6f, len - 0.4f, wide / 2f, 0.6f, SAND_D)
        }
        if (look == BuildLook.DONE && !env.snow) {
            ball(len * 0.8f, wide * 0.45f, Pal.FLAG_WHITE) // the jack
            ball(len * 0.74f, wide * 0.28f, BALL_RED); ball(len * 0.87f, wide * 0.64f, BALL_BLUE); ball(len * 0.71f, wide * 0.7f, BALL_BLUE)
            // one on its way down the court, slowing to a stop by the others
            val ph = ((s.time / 9.0) % 1.0).toFloat()
            val roll = if (ph < 0.5f) 1f - (1f - ph / 0.5f).let { it * it } else 1f
            ball(len * (0.12f + 0.54f * roll), wide * 0.5f, BALL_RED)
        }
        box(0f, wide - t, 0f, len, t, bh); box(len - t, 0f, 0f, t, wide, bh)
        if (look == BuildLook.SCAFFOLD) {
            // the rake left leaning on the end board
            line(len - 0.35f, wide * 0.75f, 0.6f, len + 0.05f, wide * 0.3f, 6f, Pal.WOOD_M)
            line(len - 0.45f, wide * 0.55f, 0.7f, len - 0.25f, wide * 0.95f, 0.7f, Pal.STONE_X)
        }
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
        i.box(x0, y0, 1f, bw, bw, 9f, Pal.WALL_L, Pal.WALL_M, Pal.WALL_D)
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
            b.roofY(x0 - 0.2f, y0 - 0.15f, 10f, bw + 0.4f, bw + 0.45f, 5.5f, b.tiles(), Pal.WALL_L)
            val ax = i.ix(x0 + bw / 2f, y0 + bw + 0.3f); val ay = i.iy(x0 + bw / 2f, y0 + bw + 0.3f, 15.5f)
            s.sprite(ax, ay) { c.vline(ax, ay - 4, ay, IRON); c.hline(ax - 1, ax + 1, ay - 3, IRON) }
        } else {
            rafters(x0 - 0.2f, y0 - 0.15f, 10f, bw + 0.4f, bw + 0.45f, 5.5f)
            scaffoldFront(x0, y0, bw, bw, 11f)
        }
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
            vines(x, y0 + t * 0.5f, z, w, young = look != BuildLook.DONE)
        }
        if (look == BuildLook.FRAME) timberHeap(x + w - 1.55f, y + d - t + 0.02f)
        if (look == BuildLook.DONE && env.month in 8..11) klopotec(x + w - 0.3f, y + 0.3f, 6f)
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
        i.post(hx + 0.3f, hy + 0.25f, 0f, 2f, Pal.WOOD_X)
        val painted = look == BuildLook.DONE || look == BuildLook.SCAFFOLD && j < 2
        val front = if (painted) Pal.PAINTED[intArrayOf(0, 2, 1)[j % 3]] else Pal.WOOD_L
        i.box(hx, hy, 2f, 0.6f, 0.5f, 3f, Pal.WOOD_M, front, Pal.WOOD_D)
        if (painted) {
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
            val r = b.shingles()
            i.gableX(hx - 0.1f, hy - 0.1f, 5f, 0.8f, 0.7f, 1.6f, r.back, r.front, Pal.WOOD_D, r.edge)
        }
    }

    // ---------------------------------------------------------------- Gasilski dom · the fire station

    /** A small white hall with a red gate for the pump cart, and beside it the tall tower where the hoses dry, a bell in it. */
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
        // the hall
        i.box(hx, hy, 0f, hw, hd, 1.5f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        if (fine) b.mortar(hx, hy, 0f, hw, hd, 1.5f, Pal.STONE_D, Pal.STONE_X)
        i.box(hx, hy, 1.5f, hw, hd, 7f, Pal.WALL_L, Pal.WALL_M, Pal.WALL_D)
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
        if (done) b.roofX(hx - 0.2f, hy - 0.25f, 8.5f, hw + 0.35f, hd + 0.5f, 5f, b.tiles(), Pal.WALL_L)
        else roofFrameX(hx - 0.2f, hy - 0.25f, 8.5f, hw + 0.35f, hd + 0.5f, 5f)
        // the hose tower
        if (!done) scaffoldBack(tx, ty, 21f)
        i.box(tx, ty, 0f, tw, td, 20f, Pal.WALL_L, Pal.WALL_M, Pal.WALL_D)
        if (fine) i.line(tx, ty + td, 1.5f, tx + tw, ty + td, 1.5f, Pal.STONE_M)
        i.panelX(tx + tw, ty + 0.25f, 11f, 0.15f, 2f, Pal.GLASS)
        if (done) {
            i.box(tx, ty, 20f, tw, td, 5f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
            for (z in floatArrayOf(21.5f, 23.2f)) { i.line(tx, ty + td, z, tx + tw, ty + td, z, INTERIOR); i.line(tx + tw, ty, z, tx + tw, ty + td, z, INTERIOR) }
            i.px(tx + tw / 2f, ty + td, 22f, Pal.GOLD) // the bell
            val r = b.tiles()
            i.pyramid(tx - 0.12f, ty - 0.12f, 25f, tw + 0.24f, td + 0.24f, 6f, if (env.snow) Pal.SNOW_L else Pal.ROOF_L, if (env.snow) Pal.SNOW_M else r.front)
            i.post(tx + tw / 2f, ty + td / 2f, 31f, 33f, IRON)
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

    /** A swing on an A-frame (swinging by day), a seesaw going up and down, a sandbox with a bucket and a spade. */
    private fun igrisce(x: Float, y: Float, look: BuildLook) {
        val playing = look == BuildLook.DONE && !env.snow && env.dark < 0.5f
        // the seesaw, at the back left
        val sx0 = x + 0.15f; val sy = y + 0.45f
        if (look != BuildLook.FRAME) {
            i.box(sx0 + 0.55f, sy - 0.1f, 0f, 0.2f, 0.2f, 1.6f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
            if (look == BuildLook.DONE) {
                val tilt = if (playing) sin(s.time * 1.6).toFloat() * 1.3f else 1.3f
                val za = 1.8f - tilt; val zb = 1.8f + tilt
                i.line(sx0, sy, za - 0.5f, sx0 + 1.3f, sy, zb - 0.5f, Pal.WOOD_D)
                i.line(sx0, sy, za, sx0 + 1.3f, sy, zb, SEAT)
                i.post(sx0 + 0.15f, sy, za + 0.2f * tilt, za + 1.5f, IRON); i.post(sx0 + 1.15f, sy, zb - 0.2f * tilt, zb + 1.5f, IRON) // the handles
            }
        }
        swing(x + 1.5f, x + 2.65f, y + 0.6f, look, playing)
        sandbox(x + 0.2f, y + 1.0f, 1.25f, 0.9f, look)
    }

    private fun swing(xa: Float, xb: Float, yc: Float, look: BuildLook, playing: Boolean) {
        val top = 11f; val spread = 0.55f
        val legs = if (look == BuildLook.FRAME) floatArrayOf(xb - 0.05f) else floatArrayOf(xa + 0.05f, xb - 0.05f)
        for (lx in legs) rod(lx, yc - spread, 0f, lx, yc, top, Pal.WOOD_M)
        if (look != BuildLook.FRAME) {
            i.box(xa, yc - 0.06f, top - 0.6f, xb - xa, 0.12f, 0.8f, if (env.snow) Pal.SNOW_L else Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
            if (look == BuildLook.DONE) {
                val phi = if (playing) sin(s.time * 2.3).toFloat() * 0.42f else 0f
                val seatY = yc + sin(phi) * 1.5f; val seatZ = top - cos(phi) * 8f
                val r0 = (xa + xb) / 2f - 0.18f; val r1 = r0 + 0.36f
                i.line(r0, yc, top - 0.3f, r0, seatY, seatZ, ROPE); i.line(r1, yc, top - 0.3f, r1, seatY, seatZ, ROPE)
                i.box(r0 - 0.04f, seatY - 0.08f, seatZ - 0.5f, 0.44f, 0.16f, 0.5f, SEAT, Col.scale(SEAT, 0.8f), Col.scale(SEAT, 0.62f))
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
                i.px(x0 + 0.4f, y0 + 0.5f, 1.5f, Pal.FLAG_RED); i.px(x0 + 0.4f, y0 + 0.5f, 2.3f, Col.scale(Pal.FLAG_RED, 0.7f)) // a bucket
                i.line(x0 + 0.75f, y0 + 0.6f, 0.9f, x0 + 0.95f, y0 + 0.45f, 2.5f, Pal.GOLD) // a spade
                i.px(x0 + 0.85f, y0 + 0.3f, 1.3f, SAND_L); if (fine) i.px(x0 + 0.85f, y0 + 0.3f, 2.1f, SAND_D) // a sand castle
            }
        }
        i.box(x0, y0 + d - t, 0f, w, t, bh, board, Pal.WOOD_M, Pal.WOOD_D); i.box(x0 + w - t, y0, 0f, t, d, bh, board, Pal.WOOD_M, Pal.WOOD_D)
    }

    // ---------------------------------------------------------------- Vodnjak na trgu · the fountain on the square

    /** A square stone basin, a column with a bowl spilling water into it (frozen in winter), a gold finial, a lamp by it. */
    private fun vodnjak(x: Float, y: Float, look: BuildLook) {
        val bx = x + 0.2f; val by = y + 0.2f; val bs = 1.5f
        val cx = bx + bs / 2f; val cy = by + bs / 2f
        shadow(bx, by, bs, bs)
        if (look == BuildLook.FRAME) {
            i.box(bx, by, 0f, bs, bs, 1.5f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
            i.top(bx + 0.2f, by + 0.2f, 1.5f, bs - 0.4f, bs - 0.4f, if (env.snow) Pal.SNOW_M else Pal.SOIL_M)
            b.mortar(bx, by, 0f, bs, bs, 1.5f, Pal.STONE_D, Pal.STONE_X)
            i.box(cx - 0.2f, cy - 0.2f, 1.5f, 0.4f, 0.4f, 1.5f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
            return
        }
        val water = look == BuildLook.DONE
        if (!water) scaffoldBack(bx, by, 10f)
        i.box(bx, by, 0f, bs, bs, 3f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        b.mortar(bx, by, 0f, bs, bs, 3f, Pal.STONE_D, Pal.STONE_X)
        val inner = if (!water) Pal.STONE_D else if (env.snow) Pal.ICE_M else Pal.WATER_M
        i.top(bx + 0.18f, by + 0.18f, 3f, bs - 0.36f, bs - 0.36f, inner)
        if (fine) { i.line(bx + 0.18f, by + 0.18f, 3f, bx + bs - 0.18f, by + 0.18f, 3f, Pal.STONE_X); i.line(bx + 0.18f, by + 0.18f, 3f, bx + 0.18f, by + bs - 0.18f, 3f, Pal.STONE_X) }
        if (water && !env.snow) for (q in 0..1) {
            val ph = ((s.time * 0.7 + q * 0.5) % 1.0).toFloat()
            c.set(i.ix(bx + 0.4f + ph * 0.8f, by + 1.1f - q * 0.6f), i.iy(bx + 0.4f + ph * 0.8f, by + 1.1f - q * 0.6f, 3f), Pal.WATER_L)
        }
        i.box(cx - 0.14f, cy - 0.14f, 3f, 0.28f, 0.28f, 6f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        i.box(cx - 0.4f, cy - 0.4f, 9f, 0.8f, 0.8f, 1f, Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
        if (water) i.top(cx - 0.28f, cy - 0.28f, 10f, 0.56f, 0.56f, if (env.snow) Pal.ICE_M else Pal.WATER_M)
        i.post(cx, cy, 10f, 12.5f, Pal.STONE_L); i.px(cx, cy, 13f, Pal.GOLD)
        if (water) spill(cx, cy)
        if (!water) scaffoldFront(bx, by, bs, bs, 10f)
        else lamp(x + 1.85f, y + 1.85f)
    }

    /** Water spilling from the bowl's two front edges into the basin, or icicles in winter. */
    private fun spill(cx: Float, cy: Float) {
        for (e in 0..1) {
            val ex = if (e == 0) cx else cx + 0.4f; val ey = if (e == 0) cy + 0.4f else cy
            val ox = if (e == 0) 0f else 0.22f; val oy = if (e == 0) 0.22f else 0f
            if (env.snow) { i.line(ex, ey, 9f, ex, ey, 7.5f, Pal.ICE_L); continue }
            val steps = 6 * k
            for (q in 0..steps) {
                val t = q / steps.toFloat()
                val z = 9.2f - 6f * t * t; val px = ex + ox * t; val py = ey + oy * t
                c.set(i.ix(px, py), i.iy(px, py, z), if (((q + (s.time * 12).toInt()) % 4) == 0) Pal.WATER_L else Pal.WATER_M)
            }
            c.set(i.ix(ex + ox, ey + oy) - k, i.iy(ex + ox, ey + oy, 3.2f), Pal.WATER_L) // the splash
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
     * The great Slovene double hayrack: two racks of four bays under one tiled roof, the passage between them open for a
     * cart below and boarded above for a hayloft; hay drying on the racks from June to November.
     */
    private fun toplar(x: Float, y: Float, look: BuildLook) {
        val x0 = x + 0.35f; val len = 3.7f; val yb = y + 0.5f; val yf = y + 1.7f; val top = 12f
        val bays = HAY.size
        shadow(x0, yb, len, yf - yb)
        fun posts(yy: Float) { for (j in 0..bays) i.box(x0 + j * len / bays - 0.09f, yy - 0.09f, 0f, 0.18f, 0.18f, top, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D) }
        if (look == BuildLook.FRAME) {
            posts(yb)
            i.line(x0, yb, top - 0.5f, x0 + len, yb, top - 0.5f, Pal.WOOD_D)
            i.line(x0 + len, yb, top - 0.5f, x0 + len, yf, top - 0.5f, Pal.WOOD_D)
            posts(yf)
            i.line(x0, yf, top - 0.5f, x0 + len, yf, top - 0.5f, Pal.WOOD_L)
            i.line(x0, yf, 1f, x0 + len, yf, 1f, Pal.WOOD_M)
            return
        }
        val done = look == BuildLook.DONE
        val hay = done && env.month in 6..11
        // the back rack, seen through the passage
        i.panelY(x0, yb, 0.5f, len, top - 1f, INTERIOR)
        for (z in RUNGS) i.line(x0, yb, z, x0 + len, yb, z, Pal.WOOD_M)
        if (hay) for (j in 0 until 14) i.px(x0 + Noise.rnd(j, 81) * len, yb, 1.5f + Noise.rnd(j, 82) * 5f, Pal.HAY_D)
        posts(yb)
        // the gable end: open below for the cart, the hayloft's boards above with a hatch
        i.panelX(x0 + len, yb, 0.5f, yf - yb, 7f, INTERIOR)
        i.panelX(x0 + len, yb, 7f, yf - yb, top - 7f, Pal.WOOD_M)
        i.line(x0 + len, yb, 7f, x0 + len, yf, 7f, Pal.WOOD_X)
        if (fine) { var v = 0.2f; while (v < yf - yb) { i.line(x0 + len, yb + v, 7f, x0 + len, yb + v, top, Pal.WOOD_D); v += 0.23f } }
        i.panelX(x0 + len, yb + 0.42f, 8f, 0.35f, 2.5f, INTERIOR)
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
        for (z in RUNGS) {
            i.line(x0, yf, z, x0 + len, yf, z, Pal.WOOD_L)
            if (fine) i.line(x0, yf, z - 0.5f, x0 + len, yf, z - 0.5f, Pal.WOOD_D)
        }
        posts(yf)
        if (done) {
            b.roofX(x0 - 0.35f, yb - 0.45f, top - 0.5f, len + 0.7f, yf - yb + 0.9f, 5f, b.tilesSideLit(), Pal.WOOD_D)
            if (!env.snow) i.line(x0 - 0.35f, yf + 0.45f, top, x0 + len + 0.35f, yf + 0.45f, top, Col.hex(0x7A3220))
        } else roofFrameX(x0 - 0.35f, yb - 0.45f, top - 0.5f, len + 0.7f, yf - yb + 0.9f, 5f)
    }

    // ---------------------------------------------------------------- Razgledni stolp · the lookout tower

    /**
     * A tall larch lattice tower, its four legs leaning in, braced in three storeys, stairs zigzagging up inside, an
     * open platform with a railing under a little tiled pyramid roof.
     */
    private fun stolp(x: Float, y: Float, look: BuildLook) {
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
        i.pyramid(x + 0.2f, y + 0.2f, zp + 5f, 1.4f, 1.4f, 6f, if (env.snow) Pal.SNOW_L else Pal.ROOF_L, if (env.snow) Pal.SNOW_M else Pal.ROOF_D)
        if (fine && !env.snow) for (j in 1..2) {
            val t = j / 3f; val zz = zp + 5f + t * 6f; val a = 0.2f + t * 0.7f; val bb = 1.6f - t * 0.7f
            i.line(x + a, y + bb, zz, x + bb, y + bb, zz, Pal.ROOF_M); i.line(x + bb, y + a, zz, x + bb, y + bb, zz, Col.scale(Pal.ROOF_D, 0.85f))
        }
        i.post(x + 0.9f, y + 0.9f, zp + 11f, zp + 13f, IRON)
    }

    // ---------------------------------------------------------------- Mestna ura · the town clock

    /**
     * A slender ochre tower on a stone foot, a clock face on each side we see (telling the real time, lit at night), a
     * belfry, and a green copper onion dome with a gold ball and cross on top.
     */
    private fun ura(x: Float, y: Float, look: BuildLook) {
        val x0 = x + 0.2f; val y0 = y + 0.2f; val tw = 1.2f
        shadow(x0, y0, tw, tw)
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
        i.box(x0, y0, 4f, tw, tw, 22f, Pal.OCHRE_L, Pal.OCHRE_M, Pal.OCHRE_D)
        if (fine) {
            // the pilaster on the front corner and a string course
            i.line(x0 + tw, y0 + tw, 4f, x0 + tw, y0 + tw, 26f, Pal.WALL_L)
            i.line(x0, y0 + tw, 15f, x0 + tw, y0 + tw, 15f, Pal.WALL_L); i.line(x0 + tw, y0, 15f, x0 + tw, y0 + tw, 15f, Pal.WALL_M)
        }
        i.panelY(x0 + 0.4f, y0 + tw + 0.05f, 0f, 0.4f, 3.5f, Pal.DOOR)
        for (face in 0..1) {
            val on = lit && done
            val col = if (on) Pal.WINDOW_LIT else Pal.GLASS
            if (on) glow { if (face == 0) i.panelY(x0 + 0.5f, y0 + tw, 8.5f, 0.2f, 2.5f, col) else i.panelX(x0 + tw, y0 + 0.5f, 8.5f, 0.2f, 2.5f, col) }
            else if (face == 0) i.panelY(x0 + 0.5f, y0 + tw, 8.5f, 0.2f, 2.5f, col) else i.panelX(x0 + tw, y0 + 0.5f, 8.5f, 0.2f, 2.5f, col)
        }
        i.box(x0 - 0.1f, y0 - 0.1f, 26f, tw + 0.2f, tw + 0.2f, 1f, Pal.WALL_L, Pal.WALL_M, Pal.WALL_D)
        clockFace(x0 + tw / 2f, y0 + tw, 21f, 0.5f, blank = !done)
        clockFace(x0 + tw, y0 + tw / 2f, 21f, -0.5f, blank = !done)
        // the belfry
        i.box(x0 + 0.05f, y0 + 0.05f, 27f, tw - 0.1f, tw - 0.1f, 6f, if (env.snow && !done) Pal.SNOW_L else Pal.OCHRE_L, Pal.OCHRE_M, Pal.OCHRE_D)
        i.panelY(x0 + 0.35f, y0 + tw - 0.05f, 28f, 0.5f, 3.5f, INTERIOR); i.panelX(x0 + tw - 0.05f, y0 + 0.35f, 28f, 0.5f, 3.5f, INTERIOR)
        if (done) {
            i.px(x0 + 0.55f, y0 + tw - 0.05f, 29.5f, Pal.GOLD)
            dome(i.ix(x0 + tw / 2f, y0 + tw / 2f), i.iy(x0 + tw / 2f, y0 + tw / 2f, 33f))
        } else scaffoldFront(x0, y0, tw, tw, 30f)
    }

    /**
     * A clock face on a tower's side: a disc on the wall (sheared like the wall, [shear] px down per px right), the hour and
     * minute hands at the frame's hour; lit at night. [blank]: the round hole the clock will go in.
     */
    private fun clockFace(wx: Float, wy: Float, z: Float, shear: Float, blank: Boolean) {
        val ax = i.ix(wx, wy); val ay = i.iy(wx, wy, z)
        val r = 2.4f * k
        val night = lit && !blank
        val face = if (blank) INTERIOR else if (night) Pal.WINDOW_LIT_HI else Pal.FLAG_WHITE
        val rim = if (blank) INTERIOR else Pal.GOLD
        val was = c.penEmissive
        if (night) c.penEmissive = true
        val n = ceil(r).toInt()
        for (v in -n..n) for (u in -n..n) {
            val d2 = u * u + v * v
            if (d2 > r * r) continue
            val col = if (k >= 2 && d2 > (r - 1f) * (r - 1f)) rim else face
            c.set(ax + u, ay + v + (shear * u).roundToInt(), col)
        }
        if (!blank) {
            val hands = IRON
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

    /** An onion dome of green copper on the tower's top ([ax], [ay], canvas px), a small lantern, a gold ball and a cross. */
    private fun dome(ax: Int, ay: Int) {
        val light = Col.mix(DOME_L, Col.hex(0xFFFFFF), 0.3f)
        for (row in 0 until DOME.size * k) {
            val r = row / k
            val hw = DOME[r] * k
            val yy = ay - row
            for (xx in (ax - hw).roundToInt() until (ax + hw).roundToInt()) {
                val rel = (xx + 0.5f - ax) / max(hw, 1f)
                var col = when { rel < -0.4f -> DOME_L; rel < 0.35f -> DOME_M; else -> DOME_D }
                if (k >= 2 && r in 1..5 && rel in -0.32f..-0.12f) col = light
                if (env.snow && r in 3..5 && rel < 0.45f) col = if (rel < -0.2f) Pal.SNOW_L else Pal.SNOW_M
                c.set(xx, yy, col)
            }
        }
        val top = ay - DOME.size * k
        s.sprite(ax, top) {
            c.set(ax, top - 1, Pal.GOLD_L); c.set(ax - 1, top - 1, Pal.GOLD)
            c.vline(ax, top - 5, top - 2, Pal.GOLD); c.hline(ax - 1, ax + 1, top - 4, Pal.GOLD)
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
        val IRON = Col.hex(0x2E2A34)
        val ROPE = Col.hex(0x8C7A50)
        val SEAT = Col.hex(0xC8392F)
        val BALL_RED = Col.hex(0xC8392F); val BALL_BLUE = Col.hex(0x2E63B0)
        val BRONZE = Col.hex(0xB08A2E)
        val DOME_L = Col.hex(0x5E8A58); val DOME_M = Pal.SHUTTER; val DOME_D = Col.hex(0x2C4A2A)

        /** How high the hay hangs in each bay of the toplar's front rack (px; 0: an empty bay). */
        val HAY = floatArrayOf(9f, 6f, 9.5f, 0f, 7f)

        /** The heights of a hayrack's rungs (px). */
        val RUNGS = floatArrayOf(2.5f, 5f, 7.5f, 10f)

        /** The onion dome's half width by row from its foot (nominal px). */
        val DOME = floatArrayOf(5f, 6f, 6.5f, 6.4f, 5.8f, 4.8f, 3.4f, 2.2f, 1.3f, 1.1f, 1.6f, 1.2f)
    }
}
