package si.lanisce.lani.game.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The village's centre as its age has it (companion/GAME.md "Hearths"): the fire ring with its benches (Vas), the square's
 * iron fire basket and the kres on a feast evening (Trg), the lanterns round the square (Mesto); the campfire is
 * [Effects.hearth]'s. Drawn round the fire's middle ([fx], [fy] in this canvas's pixels) at its [scale]
 * ([TownAnchors.FIRE_SCALE]), each piece a sprite at the lens's detail.
 */
internal class CentrePainter(private val s: SceneCtx) {
    private val c get() = s.canvas
    private val k get() = s.k
    private val t get() = s.time

    /**
     * The fire ring's stones, the half behind the fire ([back]) or in front: wider than the campfire's and set close, big
     * flat stones, alternately lighter and darker, a glint on each at a closer look.
     */
    fun ring(fx: Int, fy: Int, scale: Float, back: Boolean) {
        val n = 14
        val rx = 7.2f * scale * k; val ry = 3.5f * scale * k
        for (j in 0 until n) {
            val a = 2 * PI * j / n + 0.2
            if ((sin(a) < 0) != back) continue
            val x = (fx + cos(a) * rx).toInt(); val y = (fy + sin(a) * ry).toInt()
            val light = j % 2 == 0
            s.sprite(x, y) {
                c.fillRect(x - 1, y - 1, 3, 2, if (light) Pal.STONE_L else Pal.STONE_M)
                c.hline(x - 1, x + 1, y, Pal.STONE_D)
                c.set(x - 1, y - 1, if (light) Col.mix(Pal.STONE_L, Col.hex(0xFFFFFF), 0.25f) else Pal.STONE_L)
            }
            if (k >= 2) c.set(x - 1, y - 1, Col.mix(Pal.STONE_L, Col.hex(0xFFFFFF), 0.35f))
        }
        // the ash and two half-burnt logs inside it
        if (back) s.sprite(fx, fy) {
            c.fillRect(fx - 4, fy - 1, 9, 2, Col.hex(0x4E4844))
            c.line(fx - 3, fy - 1, fx + 2, fy, Pal.LOG_D); c.line(fx - 2, fy, fx + 3, fy - 1, Pal.WOOD_X)
        }
    }

    /**
     * A plank bench whose seat is [half] nominal px to either side of ([bx], [by]) (its feet's row), its legs at the ends:
     * where the evening's gathering sits round the fire ring.
     */
    fun bench(bx: Int, by: Int, half: Int) {
        s.sprite(bx, by) {
            c.hline(bx - half, bx + half, by - 3, Pal.WOOD_L)
            c.hline(bx - half, bx + half, by - 2, Pal.WOOD_D)
            c.vline(bx - half + 1, by - 1, by, Pal.WOOD_X); c.vline(bx + half - 1, by - 1, by, Pal.WOOD_X)
        }
    }

    /** A few coals glowing in the ash (the ring by day, between its gatherings), a thin thread of smoke from them. */
    fun embers(fx: Int, fy: Int, seed: Int, smoke: Boolean) {
        c.penEmissive = true
        s.sprite(fx, fy) {
            for (j in -2..2) if ((j + (t * 3).toInt() + seed) % 3 != 0) c.set(fx + j, fy - 1, if (j == 0) Pal.FLAME[2] else Pal.FLAME[3])
        }
        c.penEmissive = false
        s.light(fx.toFloat(), fy - 1f * k, 10f, 0.25f)
        if (smoke) s.smoke(fx.toFloat(), fy - 2f * k, 0.35f, seed)
    }

    /**
     * The square's fire basket: three iron legs and a bowl of bars [BASKET_LEGS] nominal px up, its back bars ([back]) or
     * its front; returns the bowl's middle row (where its fire burns) in this canvas's pixels.
     */
    fun basket(fx: Int, fy: Int, scale: Float, back: Boolean): Int {
        val legs = (BASKET_LEGS * scale).roundToInt()
        val top = fy - legs * k
        val half = (3.4f * scale).roundToInt()
        s.sprite(fx, fy) {
            val bowl = fy - legs
            if (back) {
                // the back leg and the bowl's back rim
                c.vline(fx, bowl, fy - 1, IRON_D)
                c.hline(fx - half, fx + half, bowl - 3, IRON_D)
                for (dx in -half..half step 2) c.set(fx + dx, bowl - 2, IRON_D)
            } else {
                // the front legs, splayed, the bowl's front bars and its rim
                c.line(fx - half + 1, bowl, fx - half - 1, fy, IRON); c.line(fx + half - 1, bowl, fx + half + 1, fy, IRON)
                c.hline(fx - half + 1, fx + half - 1, bowl, IRON)
                for (dx in -half..half step 2) c.vline(fx + dx, bowl - 3, bowl - 1, IRON)
                c.hline(fx - half, fx + half, bowl - 3, IRON_L)
            }
        }
        return top - 2 * k
    }

    /**
     * The kres of a feast evening on the square (in place of the basket): a tall cone of logs leaning together; returns its
     * top row, where its big fire burns from.
     */
    fun kres(fx: Int, fy: Int, scale: Float): Int {
        val h = (12f * scale).roundToInt()
        val half = (5f * scale).roundToInt()
        s.sprite(fx, fy) {
            for (j in -half..half step 2) {
                val col = if (j % 4 == 0) Pal.LOG_D else Pal.LOG_M
                c.line(fx + j, fy, fx + j / 3, fy - h, col)
            }
            c.hline(fx - half - 1, fx + half + 1, fy, Pal.LOG_D)
        }
        return fy - (h / 2) * k
    }

    /** A lantern on its post at world ([x], [y]): an iron post with a glass lantern on top, lit from dusk ([lit]). */
    fun lantern(x: Float, y: Float, lit: Boolean, seed: Int) {
        s.iso.post(x, y, 0f, LANTERN_POST, IRON)
        val ax = s.iso.ix(x, y); val ay = s.iso.iy(x, y, LANTERN_POST + 2f)
        val f = 0.8f + 0.2f * sin(t * 6 + seed).toFloat()
        s.sprite(ax, ay) {
            c.hline(ax - 1, ax + 1, ay - 2, IRON_D)
            if (lit) {
                c.penEmissive = true
                c.set(ax, ay, Pal.WINDOW_LIT_HI); c.set(ax, ay + 1, Pal.WINDOW_LIT)
                c.set(ax - 1, ay, Pal.WINDOW_LIT); c.set(ax + 1, ay, Pal.WINDOW_LIT)
                c.penEmissive = false
            } else {
                c.set(ax, ay, Pal.GLASS_HI); c.set(ax, ay + 1, Pal.GLASS)
                c.set(ax - 1, ay, Pal.GLASS); c.set(ax + 1, ay, Pal.GLASS)
            }
            c.hline(ax - 1, ax + 1, ay + 2, IRON_D)
        }
        if (lit) s.light(ax.toFloat(), ay.toFloat(), 22f, 0.55f * f)
    }

    companion object {
        /** The fire basket's legs, nominal px at scale 1. */
        const val BASKET_LEGS = 5f
        /** A lantern's post, px. */
        const val LANTERN_POST = 12f
        val IRON = Col.hex(0x3A3A40)
        val IRON_D = Col.hex(0x26262B)
        val IRON_L = Col.hex(0x5C5C66)
    }
}

/**
 * How the centre burns in a frame ([of]): its flames' strength, 0..1 (0: none), coals glowing in it ([embers]), the kres of
 * a feast evening ([kres], the square's).
 */
internal data class CentreLook(val flames: Float, val embers: Boolean = false, val kres: Boolean = false) {
    companion object {
        /** The campfire without wood: it smoulders, never quite out (the village's first fire). */
        const val SMOULDER = 0.15f
        /** From when the centre's evening gathering has its fire, and until when (hours: 17:30 to the storyteller's 23:30). */
        const val EVENING = 17.5f
        const val LATE = 23.5f

        /**
         * The centre of [kind] in [frame]: the campfire burns all day while lit ([lit]: its wood paid today), smoulders
         * without; the fire ring and the fire basket burn for a gathering (the evening's, the lunch by the fire, a party or a
         * festival, an age's moment's gathering, the wolves or a bear about in the dark, a cheer after a fight won), else their coals
         * glow (by day a thread of smoke); cold when their wood wasn't paid. On a feast evening (the village's feast, a
         * festival's day, an age's party) the square burns its kres.
         */
        fun of(state: si.lanisce.lani.game.GameState, frame: Frame, kind: si.lanisce.lani.game.HearthKind, lit: Boolean, env: Env): CentreLook {
            if (kind == si.lanisce.lani.game.HearthKind.CAMPFIRE) return CentreLook(if (lit) 1f else SMOULDER)
            if (!lit) return CentreLook(0f)
            val h = frame.hour
            val stir = si.lanisce.lani.game.villagers.Reactions.stir(state, frame.wall)
            val party = frame.festive?.gather == true || state.event?.kind == si.lanisce.lani.game.EventKind.FESTIVAL
            val gathering = party ||
                (si.lanisce.lani.game.villagers.Reactions.threat(state.event?.kind) && env.dark > 0.3f) ||
                stir?.phase == si.lanisce.lani.game.villagers.Stir.Phase.WON
            val evening = h >= EVENING && h < LATE
            val lunch = h >= 12.5f && h < 13.2f
            val feast = party || state.lastFeast == frame.date.toString() ||
                si.lanisce.lani.game.Calendar.on(state, frame.date).any { it.late == 0 && !it.festival.quiet }
            val kres = kind == si.lanisce.lani.game.HearthKind.FIRE_BASKET && feast && (evening || env.dark > 0.3f)
            return when {
                kres -> CentreLook(1f, kres = true)
                gathering -> CentreLook(1f)
                evening || lunch -> CentreLook(0.7f)
                else -> CentreLook(0f, embers = true)
            }
        }
    }
}
