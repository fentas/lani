package si.lanisce.lani.game.render

import si.lanisce.lani.game.render.scene.Looks
import si.lanisce.lani.game.scene.Pose
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How someone looks in the village's small figures (7 px at the nominal detail, 14 px at k = 2): colours and
 * the few marks that tell the villagers apart (a kerchief, a hat, an apron, a beard, what they carry). The
 * scene's people sprites (k = 3) have their own looks.
 */
class Look(
    val shirt: Int, val pants: Int, val hair: Int, val kerchief: Boolean = false, val hat: Int = 0, val straw: Boolean = false,
    val apron: Int = 0, val prop: Int = Effects.PROP_NONE, val skirt: Boolean = false, val beard: Int = 0, val small: Boolean = false,
    val skin: Int = Pal.SKIN,
    /** The pedlar's krošnja on the back, loaded above the hat; a shoulder bag's colour (0: none); a shell on the hat. */
    val pack: Boolean = false, val bag: Int = 0, val shell: Boolean = false,
)

/** Animated bits: the fire, smoke, sparks, people, animals, weather and fireworks. */
internal class Effects(private val s: SceneCtx) {
    private val c get() = s.canvas
    private val t get() = s.time
    private val k get() = s.k

    // ------------------------------------------------------------------ fire

    /** Stone ring + logs. Returns nothing; flames are drawn by [flames] under a separate id. */
    fun hearth(fx: Int, fy: Int, scale: Float, back: Boolean) {
        val n = 10
        val rx = 6.5f * scale * k; val ry = 3.2f * scale * k
        for (j in 0 until n) {
            val a = 2 * PI * j / n + 0.3
            val isBack = sin(a) < 0
            if (isBack != back) continue
            val x = (fx + cos(a) * rx).toInt(); val y = (fy + sin(a) * ry).toInt()
            val big = scale > 1.2f
            s.sprite(x, y) {
                c.fillRect(x - 1, y - 1, if (big) 3 else 2, 2, Pal.STONE_M)
                c.set(x - 1, y - 1, Pal.STONE_L); c.set(x, y - (if (big) 2 else 1), if (big) Pal.STONE_L else Pal.STONE_M)
                c.set(x + (if (big) 1 else 0), y, Pal.STONE_D)
            }
            if (k >= 2) c.set(x - 1, y - 1, Col.mix(Pal.STONE_L, Col.hex(0xFFFFFF), 0.3f)) // a glint on the stone
        }
        if (back) s.sprite(fx, fy) {
            val lw = (4 * scale).toInt()
            c.line(fx - lw, fy - 1, fx + lw - 1, fy + 1, Pal.LOG_D); c.line(fx - lw, fy - 2, fx + lw - 1, fy, Pal.LOG_M)
            c.line(fx - lw + 1, fy + 1, fx + lw, fy - 1, Pal.LOG_D); c.line(fx - lw + 1, fy, fx + lw, fy - 2, Pal.LOG_L)
            c.set(fx - lw, fy - 2, Pal.WOOD_L); c.set(fx + lw, fy - 2, Pal.WOOD_L)
        }
    }

    fun flames(fx: Int, fy: Int, strength: Float, scale: Float): Int {
        val fh = (5f + strength * 10f) * scale * k
        val fw = fh * 0.5f
        val flick = 0.85f + 0.15f * sin(t * 11).toFloat()
        c.penEmissive = true
        // embers
        s.sprite(fx, fy) { for (j in -3..3) c.set(fx + j, fy - 1, if ((j + (t * 8).toInt()) % 3 == 0) Pal.FLAME[1] else Pal.FLAME[3]) }
        val top = (fy - fh - 3 * k).toInt()
        for (y in top..fy - 1) {
            val v = (fy - y) / fh
            val wob = sin(t * 8 + v * 4).toFloat() * v * 1.4f * k + (Noise.v1((t * 3).toFloat(), 2) - 0.5f) * v * 2f * k
            val half = fw * (1f - v * 0.85f) + 0.4f * k
            for (x in (fx - fw - 3 * k).toInt()..(fx + fw + 3 * k).toInt()) {
                val dx = (x + 0.5f - fx - wob) / half
                val base = 1f - abs(dx)
                if (base <= -0.3f) continue
                val n = Noise.v2(x * 0.5f / k, y * 0.35f / k + (t * 9).toFloat(), 17)
                val i = base * (1.15f - v) + (n - 0.5f) * 0.7f - v * 0.2f
                val col = when {
                    i > 0.78f -> Pal.FLAME[0]
                    i > 0.55f -> Pal.FLAME[1]
                    i > 0.32f -> Pal.FLAME[2]
                    i > 0.14f -> Pal.FLAME[3]
                    i > 0.06f && v > 0.35f -> Pal.FLAME[4]
                    else -> 0
                }
                if (col != 0) c.set(x, y, col)
            }
        }
        c.penEmissive = false
        s.light(fx.toFloat(), fy - fh * 0.4f, (30f + strength * 34f) * (0.7f + scale * 0.3f), (0.55f + strength * 0.65f) * flick)
        return fh.toInt()
    }

    fun smoke(x: Float, y: Float, amount: Float, seed: Int, dark: Boolean, tint: Int? = null) {
        val n = 6
        val col = tint ?: if (dark) Col.hex(0x4A4448) else Pal.SMOKE
        for (j in 0 until n) {
            val a = ((t / 3.4 + j.toFloat() / n + seed * 0.137) % 1.0).toFloat()
            val px = x + (a * 7f + sin(a * 5 + seed + j).toFloat() * 1.3f) * k
            val py = y - (a * 20f * amount + 1) * k
            val r = (0.8f + a * 2.6f * amount) * k
            // a tinted smoke (the charcoal pile's white, yellow, blue) keeps its colour and shows thicker
            c.ditherCircle(px, py, r, Col.mix(col, Col.hex(0xD8D8E0), if (dark || tint != null) a * 0.2f else a * 0.5f), (1f - a) * if (tint != null) 0.95f else 0.75f)
        }
    }

    /**
     * A hearth's plume from a chimney or a roof (companion/GAME.md "Hearths"): a few soft puffs rising and drifting off, thick
     * near the chimney so it shows by day, thinning as they rise.
     */
    fun plume(x: Float, y: Float, seed: Int) {
        val n = 5
        for (j in 0 until n) {
            val a = ((t / 4.0 + j.toFloat() / n + seed * 0.137) % 1.0).toFloat()
            val px = x + (a * 6f + sin(a * 4 + seed + j).toFloat()) * k
            val py = y - (1f + a * 14f) * k
            val r = (1.1f + a * 2.2f) * k
            c.ditherCircle(px, py, r, Col.mix(PLUME, Col.hex(0xE8E8EE), a * 0.5f), 1.15f - a)
        }
    }

    fun sparks(x: Float, y: Float, n: Int, seed: Int) {
        c.penEmissive = true
        for (j in 0 until n) {
            val a = ((t / 1.3 + j.toFloat() / n + seed * 0.29 + Noise.rnd(j, seed) * 0.3) % 1.0).toFloat()
            if (Noise.rnd(j, seed, (t / 1.3 + j.toFloat() / n).toInt()) < 0.3f) continue
            val px = x + (sin(a * 8 + j).toFloat() * 1.5f + (Noise.rnd(j, seed, 5) - 0.5f) * a * 8f) * k
            val py = y - a * 16f * k
            c.block(px.toInt(), py.toInt(), k, if (a < 0.4f) Pal.FLAME[0] else if (a < 0.7f) Pal.FLAME[1] else Pal.FLAME[2])
        }
        c.penEmissive = false
    }

    // ------------------------------------------------------------------ people

    /** An unnamed villager (the population beyond the people who live here by name), coloured by [seed]. */
    fun villager(bx: Int, by: Int, seed: Int, walking: Boolean, flip: Boolean, bob: Int = 0) {
        person(bx, by, null, lookOf(null, seed), seed, if (walking) Activity.WALK else Activity.STAND, flip, bob)
    }

    fun seated(bx: Int, by: Int, seed: Int, flip: Boolean) {
        person(bx, by, null, lookOf(null, seed), seed, Activity.WARM, flip)
    }

    /**
     * Someone with their feet at ([bx], [by]) at the lens's detail: the 7 px figure, the 14 px one with a hat and
     * a prop at k = 2, the scene's people (faces, clothes) at k = 3; doing what [activity] says: sitting, walking,
     * fishing, chopping, at work (a hoe, the anvil, tending, washing), carrying [load], playing, chatting or waving to
     * the learner. [lantern]: a lit lantern in the hand (or set down beside them while their hands are busy), with its
     * warm light round them. [art] is their sprite (null: one by [seed]). Returns the figure's height in canvas px.
     */
    fun person(
        bx: Int, by: Int, art: String?, look: Look, seed: Int, activity: Activity, flip: Boolean, bob: Int = 0,
        lantern: Boolean = false, load: Load = Load.NONE,
    ): Int {
        // a child running at play: walking's legs, quicker, with a little hop
        if (activity == Activity.RUN) return person(bx, by, art, look, seed, Activity.WALK, flip, bob + ((((t * 8).toInt() + seed) and 1)), lantern, load)
        val h = when (k) {
            1 -> small(bx, by, look, seed, activity, flip, bob, lantern, load)
            2 -> middle(bx, by, look, seed, activity, flip, bob, lantern, load)
            else -> fine(bx, by, art, seed, activity, flip, bob, lantern, load)
        }
        when (activity) {
            Activity.FISH -> rod(bx, by, h)
            Activity.CHOP -> axe(bx, by, flip, h)
            else -> Unit
        }
        return h
    }

    // ---- the moments of the work, each person at their own point of it (by their seed), so neighbours don't move in step

    /** Where [seed]'s cycle of [period] s is at, 0 until 1. */
    private fun cyc(seed: Int, period: Double, salt: Double = 0.0): Float {
        val v = (t / period + ((seed ushr 4) and 0xFF) / 256.0 + salt) % 1.0
        return (if (v < 0.0) v + 1.0 else v).toFloat()
    }

    /** Which round of [seed]'s cycle of [period] s it is (the sparks of each strike fly their own way). */
    private fun round(seed: Int, period: Double, salt: Double = 0.0): Int = floor(t / period + ((seed ushr 4) and 0xFF) / 256.0 + salt).toInt()

    /** Waiting for the learner: 1.5 s of every 8 an arm up, waving; the seconds into the wave, or -1 between. */
    private fun waving(seed: Int): Float = waving(t, seed)

    /** Chatting: talking about half the time (the other listening), a hand going now and then while talking. */
    private fun talking(seed: Int): Boolean = cyc(seed, 5.0, 0.37) < 0.5f
    private fun gesturing(seed: Int): Boolean = talking(seed) && cyc(seed, 1.7, 0.21) < 0.45f

    /** At play: both arms up for a moment every few seconds. */
    private fun cheering(seed: Int): Boolean = cyc(seed, 4.3, 0.61) < 0.21f

    /** Cheering after a won fight: the arms up most of the time, down for a breath. */
    private fun cheerUp(seed: Int): Boolean = cyc(seed, 1.9, 0.17) < 0.62f

    /** A defender's tool shaken up a pixel for a moment every couple of seconds (0 or 1). */
    private fun shaking(seed: Int): Int = if (cyc(seed, 2.3, 0.43) < 0.18f) 1 else 0

    /** The harmonika's bellows drawn out (or pressed in). */
    private fun bellows(seed: Int): Boolean = cyc(seed, 0.9, 0.11) < 0.5f

    /**
     * The notes of a tune going up from ([x], [y]), at detail [k]: two at a time, each rising a few pixels and fading as it
     * goes, drifting a little sideways; light, warm, lit at night.
     */
    private fun notes(x: Int, y: Int, seed: Int, k: Int) {
        glow {
            for (j in 0..1) {
                val u = cyc(seed + j * 131, 1.7, j * 0.5)
                if (u > 0.85f) continue
                val nx = x + (sin(u * 6 + j * 2).toFloat() * k).toInt() + j * 2 * k
                val ny = y - (u * 6 * k).toInt()
                val col = if (j == 0) Pal.GOLD else Pal.FLAG_WHITE
                // the head, the stem up from its right; at a closer look a flag
                c.set(nx, ny, col)
                if (k >= 2) { c.set(nx + 1, ny, col); c.vline(nx + 1, ny - 2 * k + 1, ny - 1, col) } else c.set(nx + 1, ny - 1, col)
                if (k >= 3) { c.set(nx + 2, ny - 2 * k + 1, col); c.set(nx + 3, ny - 2 * k + 2, col); c.set(nx, ny + 1, col); c.set(nx + 1, ny + 1, col) }
            }
        }
    }

    /** A torch at the nominal detail, its stick's top at ([x], [y]): two pixels of flame over it, flickering, and its light. */
    private fun torch1(x: Int, y: Int, seed: Int) {
        val f = flicker(seed)
        c.set(x, y, Pal.WOOD_M); c.set(x, y + 1, Pal.WOOD_M)
        glow { c.set(x, y - 1, if (f > 0.5f) Pal.FLAME[0] else Pal.FLAME[1]); c.set(x, y - 2, Pal.FLAME[2]) }
        s.light(x + 0.5f, y - 1f, TORCH_R, TORCH_I * (0.8f + 0.2f * f))
    }

    /** A torch at the middle detail: its flame over the stick's top at ([x], [y]), three rows of it, flickering, and its light. */
    private fun torch2(x: Int, y: Int, seed: Int) {
        val f = flicker(seed)
        glow {
            c.set(x, y, Pal.FLAME[1]); c.set(x - 1, y, Pal.FLAME[2]); c.set(x + 1, y, Pal.FLAME[2])
            c.set(x, y - 1, if (f > 0.5f) Pal.FLAME[0] else Pal.FLAME[1]); c.set(x + (if (f > 0.6f) 1 else 0), y - 2, Pal.FLAME[2])
        }
        s.light(x + 0.5f, y - 1f, TORCH_R * 1.2f, TORCH_I * (0.8f + 0.2f * f))
    }

    /** A torch at the finest detail: a flame over the stick's top at ([x], [y]), licking up, flickering, and its light. */
    private fun torch3(x: Int, y: Int, seed: Int) {
        val f = flicker(seed)
        c.fillRect(x - 1, y, 3, 2, Pal.LOG_D)
        glow {
            for (r in 0..5) {
                val half = (if (r < 2) 2 else if (r < 4) 1 else 0)
                val wob = if (r >= 3 && f > 0.5f) 1 else 0
                for (dx in -half..half) c.set(x + dx + wob, y - 1 - r, if (r < 2) Pal.FLAME[if (dx == 0) 0 else 1] else if (r < 4) Pal.FLAME[1] else Pal.FLAME[2])
            }
        }
        s.light(x + 0.5f, y - 3f, TORCH_R * 1.5f, TORCH_I * (0.8f + 0.2f * f))
    }

    /** The hoe up (raised over the shoulder) or down (in the soil), and how far the clod has flown (0 until 1, or -1). */
    private fun hoeUp(seed: Int): Boolean = cyc(seed, 1.4) < 0.5f
    private fun clod(seed: Int): Float { val u = cyc(seed, 1.4); return if (u in 0.5f..0.86f) (u - 0.5f) / 0.36f else -1f }

    /** The hammer up, or down on the iron; how far the sparks of this strike have flown (0 until 1, or -1). */
    private fun hammerUp(seed: Int): Boolean = cyc(seed, 0.9) < 0.55f
    private fun strike(seed: Int): Float { val u = cyc(seed, 0.9); return if (u in 0.55f..0.92f) (u - 0.55f) / 0.37f else -1f }

    /** Scrubbing forward (true) or drawing the cloth back; a splash now and then (0 until 1, or -1). */
    private fun scrubOut(seed: Int): Boolean = cyc(seed, 1.1) < 0.5f
    private fun splash(seed: Int): Float { val u = cyc(seed, 2.9, 0.5); return if (u < 0.16f) u / 0.16f else -1f }

    /** The hand at the work going to and fro (tending). */
    private fun tendOut(seed: Int): Boolean = cyc(seed, 0.8) < 0.5f

    /** The broom out (a stroke away) or back. */
    private fun sweepOut(seed: Int): Boolean = cyc(seed, 1.1, 0.27) < 0.5f

    /** Up at the line (pegging the washing) or down at the basket for the next piece. */
    private fun reachUp(seed: Int): Boolean = cyc(seed, 2.6, 0.13) < 0.55f

    /** The carpet beater: where its swing is (0 until 1: up until 0.5, down on the rug from 0.5, the dust from it). */
    private fun beat(seed: Int): Float = cyc(seed, 1.0, 0.31)

    /** The grain thrown: the hand out for a moment of every throw. */
    private fun throwing(seed: Int): Boolean = cyc(seed, 1.8, 0.4) < 0.3f

    /** A hen's head down, pecking. */
    private fun pecking(seed: Int, j: Int): Boolean = cyc(seed + j * 977, 0.9 + j * 0.37, j * 0.29) < 0.35f

    /** The needle (the twig) drawn up out of the work in the lap, or in it. */
    private fun stitchUp(seed: Int): Boolean = cyc(seed, 0.75, 0.6) < 0.45f

    /** What's in a picker's basket by the month: wild strawberries in June, bilberries in summer, mushrooms in autumn. */
    private fun picked(): Int = when (s.env.month) {
        6 -> Pal.GERANIUM
        7, 8 -> BILBERRY
        else -> MUSHROOM
    }

    /** The lantern's flame, 0..1, flickering a little. */
    private fun flicker(seed: Int): Float = Noise.v1((t * 7).toFloat(), seed and 0xFFF)

    private inline fun glow(block: () -> Unit) { val was = c.penEmissive; c.penEmissive = true; block(); c.penEmissive = was }

    // ---- the 7 px figure (k = 1)

    /** Someone at the nominal detail: the tiny figure, a pixel or two for the tool, the load and the lantern. */
    private fun small(bx: Int, by0: Int, L: Look, seed: Int, a: Activity, flip: Boolean, bob: Int, lantern: Boolean, load: Load): Int {
        val d = if (flip) -1 else 1
        if (a == Activity.SIT || a == Activity.WARM) {
            seatedTiny(bx, by0, L, flip)
            if (lantern) lamp1(bx - 3 * d, by0 - 3, seed, ground = true)
            return 5
        }
        if (a == Activity.MEND) {
            // sitting with the work in the lap (a basket half woven, a net), the hand going in and out of it
            seatedTiny(bx, by0, L, flip)
            c.set(bx + d, by0 - 3, Pal.WOOD_L); c.set(bx, by0 - 3, Pal.WOOD_M)
            c.set(bx + 2 * d, by0 - (if (stitchUp(seed)) 4 else 3), L.skin)
            if (lantern) lamp1(bx - 3 * d, by0 - 3, seed, ground = true)
            return 5
        }
        if (a == Activity.HIDE) {
            // crouched in the hiding place, keeping still
            lowTiny(bx, by0, L, flip)
            return 5
        }
        if (a == Activity.TEND || a == Activity.WASH || a == Activity.GATHER) {
            // crouched (tending, picking) or kneeling at the bank (washing), bent over the work, a hand going
            lowTiny(bx, by0, L, flip)
            if (a == Activity.GATHER) { c.set(bx - 3 * d, by0 - 1, Pal.WOOD_M); c.set(bx - 3 * d, by0 - 2, picked()) } // the basket
            if (a == Activity.TEND || a == Activity.GATHER) {
                if (tendOut(seed)) c.set(bx + 3 * d, by0 - 1, L.skin) else c.set(bx + 2 * d, by0 - 2, L.skin)
            } else {
                val out = scrubOut(seed)
                val hx = bx + (if (out) 3 else 2) * d
                c.set(hx, by0 - 1, CLOTH); c.set(hx + d, by0 - 1, CLOTH_D); c.set(hx, by0 - 2, L.skin)
                val sp = splash(seed)
                if (sp >= 0f) { c.set(bx + 5 * d, by0 - 2 - (if (sp > 0.5f) 1 else 0), Pal.WATER_L); c.set(bx + 6 * d, by0 - 1 - (if (sp > 0.3f) 1 else 0), CLOTH) }
            }
            if (lantern) lamp1(bx - 3 * d, by0 - 3, seed, ground = true)
            return 5
        }
        val by = by0 - bob
        val step = ((t * 6).toInt() + seed) and 1 // the tiny figure's step
        when (a) {
            Activity.HOE -> {
                if (hoeUp(seed)) {
                    // raised over the shoulder, behind the head: the handle, the blade up behind
                    c.line(bx + 2 * d, by - 5, bx - 2 * d, by - 10, Pal.WOOD_M)
                    c.set(bx - 3 * d, by - 10, Pal.SLATE_M); c.set(bx - 3 * d, by - 9, Pal.SLATE_D)
                    tiny(bx, by, L, false, flip, seed, hand = false)
                    c.set(bx + 2 * d, by - 5, L.skin)
                } else {
                    // brought down: the blade in the soil in front
                    tiny(bx, by, L, false, flip, seed, hand = false)
                    c.line(bx + 2 * d, by - 4, bx + 5 * d, by - 2, Pal.WOOD_M)
                    c.set(bx + 5 * d, by - 1, Pal.SLATE_M); c.set(bx + 4 * d, by - 1, Pal.SLATE_L)
                    c.set(bx + 2 * d, by - 4, L.skin)
                }
            }
            Activity.SWEEP -> {
                // the broom slanting down in front, its head going to and fro on the ground, a puff of dust
                tiny(bx, by, L, false, flip, seed, hand = false)
                val out = sweepOut(seed)
                val ex = bx + (if (out) 5 else 3) * d
                c.line(bx + 2 * d, by - 4, ex, by - 2, Pal.WOOD_M)
                c.set(bx + 2 * d, by - 4, L.skin)
                c.set(ex, by - 1, Pal.HAY_M); c.set(ex + d, by - 1, Pal.HAY_D)
                if (out) c.set(ex + 2 * d, by - 2, DUST)
            }
            Activity.HANG -> {
                // at the washing line beside them: up to peg the washing, down to the basket at their feet for the next
                line1(bx, by, d, rug = false)
                tiny(bx, by, L, false, flip, seed, hand = false)
                if (reachUp(seed)) { c.set(bx + 2 * d, by - 5, L.shirt); c.set(bx + 2 * d, by - 6, L.skin); c.set(bx + 3 * d, by - 6, CLOTH) }
                else c.set(bx + 2 * d, by - 3, L.skin)
                c.set(bx - 2 * d, by - 1, Pal.WOOD_M); c.set(bx - 2 * d, by - 2, CLOTH)
            }
            Activity.BEAT -> {
                // the rug over the line in front, the beater up and down on it, the dust flying off
                line1(bx, by, d, rug = true)
                tiny(bx, by, L, false, flip, seed, hand = false)
                val u = beat(seed)
                if (u < 0.5f) { c.set(bx + 2 * d, by - 5, L.skin); c.line(bx + 2 * d, by - 6, bx + d, by - 8, Pal.WOOD_M) }
                else {
                    c.set(bx + 2 * d, by - 4, L.skin); c.line(bx + 3 * d, by - 4, bx + 3 * d, by - 5, Pal.WOOD_M)
                    if (u < 0.85f) { c.set(bx + 5 * d, by - 7 - (if (u > 0.68f) 1 else 0), DUST); c.set(bx + 7 * d, by - 6 - (if (u > 0.68f) 1 else 0), DUST) }
                }
            }
            Activity.FEED -> {
                // a handful of grain thrown to the hens pecking about, the bowl on the far arm
                tiny(bx, by, L, false, flip, seed, hand = false)
                if (throwing(seed)) { c.set(bx + 3 * d, by - 4, L.skin); c.set(bx + 5 * d, by - 3, Pal.GOLD); c.set(bx + 6 * d, by - 1, Pal.GOLD) }
                else c.set(bx + 2 * d, by - 3, L.skin)
                c.set(bx - 2 * d, by - 3, Pal.WOOD_L)
                hen1(bx + 5 * d, by + 1, -d, pecking(seed, 0), white = true)
                hen1(bx + 3 * d, by + 2, d, pecking(seed, 1), white = (seed ushr 9) % 2 == 0)
            }
            Activity.COUNT -> {
                // counting with a hand over the eyes
                tiny(bx, by, L, false, flip, seed, hand = false)
                c.set(bx + d, by - (if (L.small) 4 else 5), L.skin)
            }
            Activity.HAMMER -> {
                tiny(bx, by, L, false, flip, seed, hand = false)
                // the anvil on its stump in front, the iron glowing on it
                val ax = bx + 4 * d
                c.set(ax, by - 1, Pal.LOG_M); c.set(ax + d, by - 1, Pal.LOG_D)
                c.set(ax, by - 2, Pal.SLATE_L); c.set(ax + d, by - 2, Pal.SLATE_M)
                glow { c.set(ax + d, by - 3, Pal.FLAME[2]) }
                if (hammerUp(seed)) {
                    c.set(bx + 2 * d, by - 5, L.skin); c.set(bx + 2 * d, by - 6, Pal.WOOD_M)
                    c.set(bx + 2 * d, by - 7, Pal.SLATE_M); c.set(bx + 3 * d, by - 7, Pal.SLATE_L)
                } else {
                    c.set(bx + 2 * d, by - 3, L.skin); c.set(bx + 3 * d, by - 3, Pal.WOOD_M); c.set(ax, by - 3, Pal.SLATE_L); c.set(ax, by - 4, Pal.SLATE_M)
                    val p = strike(seed)
                    if (p >= 0f) sparksAt(ax + d, by - 4, p, 3, 4f, seed, round(seed, 0.9))
                }
            }
            Activity.CARRY -> {
                val b = step // the load bobs with the step
                val top = by - (if (L.small) 6 else 7)
                when (load) {
                    Load.HAY -> {
                        // a sheaf over the shoulders: yellow above and behind the head, the ears at the back
                        c.hline(bx - 3 * d, bx + d, top - 1 - b, Pal.HAY_M); c.hline(bx - 2 * d, bx, top - 2 - b, Pal.HAY_L)
                        c.set(bx - 3 * d, top - b, Pal.HAY_M); c.set(bx - 2 * d, top - b, Pal.HAY_L); c.set(bx - 3 * d, top + 1 - b, Pal.HAY_D)
                        c.set(bx + 2 * d, top - b, Pal.HAY_D)
                        tiny(bx, by, L, true, flip, seed, hand = false)
                        c.set(bx + 2 * d, top + 1 - b, L.skin)
                    }
                    Load.WOOD -> {
                        // logs held in both arms in front, their cut ends pale
                        tiny(bx, by, L, true, flip, seed, hand = false)
                        c.hline(bx - d, bx + 2 * d, by - 4 - b, Pal.LOG_M); c.set(bx + 2 * d, by - 4 - b, Pal.WOOD_L)
                        c.hline(bx - d, bx + d, by - 3 - b, Pal.LOG_D); c.set(bx + 2 * d, by - 3 - b, Pal.WOOD_L)
                    }
                    Load.WATER -> {
                        // a pail in the hand, a glint of water in it
                        tiny(bx, by, L, true, flip, seed, held = false)
                        c.set(bx + 2 * d, by - 2 - step, Pal.WOOD_L); c.set(bx + 3 * d, by - 2 - step, if (((t * 3).toInt() and 1) == 0) Pal.WATER_L else Pal.WATER_M)
                        c.set(bx + 2 * d, by - 1 - step, Pal.WOOD_M); c.set(bx + 3 * d, by - 1 - step, Pal.WOOD_D)
                    }
                    Load.BASKET -> {
                        // a basket on the arm, an apple on top
                        tiny(bx, by, L, true, flip, seed, held = false)
                        c.set(bx + 3 * d, by - 3 - step, Pal.GERANIUM)
                        c.set(bx + 2 * d, by - 2 - step, Pal.WOOD_L); c.set(bx + 3 * d, by - 2 - step, Pal.WOOD_M)
                    }
                    Load.NONE -> tiny(bx, by, L, true, flip, seed)
                }
                if (lantern) {
                    // in the far hand
                    val hy = by - 3 - (1 - step)
                    c.set(bx - 2 * d, hy, L.skin)
                    lamp1(bx - 3 * d, hy, seed)
                }
                return if (L.hat != 0) 8 else 7
            }
            Activity.PLAY, Activity.CHEER -> {
                val up = if (a == Activity.CHEER) cheerUp(seed) else cheering(seed)
                val hop = if (sin(t * 9 + (seed and 0xFF) * 0.1) > 0.2) 1 else 0
                val y = by - hop
                tiny(bx, y, L, !up, flip, seed, hand = !up)
                if (up) {
                    // both arms up
                    val top = y - (if (L.small) 6 else 7)
                    for (side in intArrayOf(-1, 1)) { c.set(bx + 2 * side, top, L.skin); c.set(bx + 2 * side, top + 1, L.shirt) }
                }
            }
            Activity.GUARD -> {
                // facing them, the near hand up with the tool (shaken now and then): a torch at night, the hammer, a stick
                tiny(bx, by, L, false, flip, seed, hand = false, held = false)
                val hx = bx + 2 * d; val hy = by - 5 - shaking(seed)
                c.set(hx, hy, L.skin)
                when {
                    lantern -> torch1(hx, hy - 1, seed)
                    L.prop == PROP_HAMMER -> { c.set(hx, hy - 1, Pal.WOOD_M); c.set(hx, hy - 2, Pal.SLATE_M); c.set(hx + d, hy - 2, Pal.SLATE_L) }
                    else -> c.vline(hx, hy - 3, by - 1, Pal.WOOD_M)
                }
                return if (L.hat != 0) 8 else 7
            }
            Activity.HAT -> {
                // hurrying, a hand on the head to keep the hat on
                val top = by - (if (L.small) 6 else 7)
                tiny(bx, by, L, true, flip, seed, hand = false, held = false)
                c.set(bx + 2 * d, top + 1, L.shirt); c.set(bx + d, top - 1, L.skin)
                return if (L.hat != 0) 8 else 7
            }
            Activity.REPAIR -> {
                // at the wall in front, the hammer up and down on a plank, more planks leaning behind
                tiny(bx, by, L, false, flip, seed, hand = false, held = false)
                c.vline(bx - 3 * d, by - 4, by - 1, Pal.WOOD_L); c.vline(bx - 4 * d, by - 3, by - 1, Pal.WOOD_M)
                c.vline(bx + 4 * d, by - 6, by - 2, Pal.WOOD_L)
                if (hammerUp(seed)) { c.set(bx + 2 * d, by - 5, L.skin); c.set(bx + 2 * d, by - 6, Pal.WOOD_M); c.set(bx + 3 * d, by - 7, Pal.SLATE_M) }
                else { c.set(bx + 2 * d, by - 4, L.skin); c.set(bx + 3 * d, by - 4, Pal.SLATE_M) }
                return if (L.hat != 0) 8 else 7
            }
            Activity.MUSIC -> {
                // the harmonika in both hands in front, its bellows going in and out, the notes going up
                tiny(bx, by, L, false, flip, seed, hand = false, held = false)
                val out = bellows(seed)
                c.set(bx + d, by - 4, Pal.FLAG_RED); c.set(bx + 2 * d, by - 4, if (out) Pal.FLAG_WHITE else Pal.FLAG_RED)
                if (out) c.set(bx + 3 * d, by - 4, Pal.FLAG_RED)
                notes(bx + 2 * d, by - 9, seed, 1)
                return if (L.hat != 0) 8 else 7
            }
            Activity.CHAT -> {
                val g = gesturing(seed)
                tiny(bx, by, L, false, flip, seed, hand = !g, held = !g && !lantern)
                val (hx, hy) = if (g) {
                    // the hand going as they talk
                    val hi = ((t * 4).toInt() and 1) == 0
                    val p = intArrayOf(bx + (if (hi) 2 else 3) * d, by - (if (hi) 5 else 4))
                    c.set(p[0], p[1], L.skin); p[0] to p[1]
                } else (bx + 2 * d) to (by - 3)
                if (lantern) lamp1(hx + d, hy, seed)
                return if (L.hat != 0) 8 else 7
            }
            Activity.WAVE -> {
                val w = waving(seed)
                val y = by - (if (w in 0f..0.3f) 1 else 0) // a little hop as the wave starts
                tiny(bx, y, L, false, flip, seed, hand = w < 0f, held = w < 0f && !lantern)
                if (w >= 0f) {
                    // the arm up, the hand waving to and fro
                    val wob = if (((t * 6).toInt() and 1) == 0) 0 else d
                    c.set(bx + 2 * d, y - 4, L.shirt); c.set(bx + 2 * d, y - 5, L.shirt)
                    c.set(bx + 2 * d + wob, y - 6, L.skin); c.set(bx + 2 * d + wob, y - 7, L.skin)
                    if (lantern) lamp1(bx + 2 * d + wob, y - 9, seed, up = true) // swung up in the hand
                } else if (lantern) lamp1(bx + 3 * d, y - 3, seed)
                return if (L.hat != 0) 8 else 7
            }
            else -> {
                // standing, walking, fishing, chopping: as ever; a lantern in the near hand when standing or walking
                val walking = a == Activity.WALK
                val inHand = lantern && (a == Activity.WALK || a == Activity.STAND)
                tiny(bx, by, L, walking, flip, seed, held = !inHand)
                if (inHand) lamp1(bx + 3 * d, by - 3 - (if (walking) step else 0), seed)
            }
        }
        if (lantern && a != Activity.WALK && a != Activity.STAND) lamp1(bx - 3 * d, by0 - 3, seed, ground = true)
        return if (L.hat != 0) 8 else 7
    }

    /**
     * A lantern at the nominal detail: its glowing pane from ([x], [y]) two pixels down (they stay lit at night, the
     * flame's the brighter), its dark foot under it (or, [up], held up: the hand is its foot), set down on the ground
     * ([ground]) with its cap over it too; and its warm light round it.
     */
    private fun lamp1(x: Int, y: Int, seed: Int, ground: Boolean = false, up: Boolean = false) {
        val f = flicker(seed)
        if (ground) c.set(x, y - 1, LAMP)
        if (!up) c.set(x, y + 2, LAMP)
        glow { c.set(x, y, if (f > 0.55f) Pal.FLAME[0] else Pal.WINDOW_LIT_HI); c.set(x, y + 1, Pal.WINDOW_LIT) }
        s.light(x + 0.5f, y + 1f, LAMP_R, LAMP_I * (0.85f + 0.15f * f))
    }

    /** Crouched or kneeling at the nominal detail: the head bent forward over the work, the body low, a knee and a heel. */
    private fun lowTiny(bx: Int, by: Int, L: Look, flip: Boolean) {
        val d = if (flip) -1 else 1
        tinyHead(bx + d, by - 5, L, flip)
        c.hline(bx - 1, bx + 1, by - 2, if (L.apron != 0) L.apron else L.shirt)
        if (L.apron != 0) c.set(bx - d, by - 2, L.shirt)
        val legs = if (L.skirt) L.shirt else L.pants
        c.set(bx - d, by - 1, Col.hex(0x2E2018)); c.set(bx, by - 1, legs); c.set(bx + d, by - 1, legs)
    }

    // ---- the 14 px figure (k = 2)

    /** Someone at the middle detail: the 14 px figure, its arms going to the work, the tool, the load and the lantern. */
    private fun middle(bx: Int, by0: Int, L: Look, seed: Int, a: Activity, flip: Boolean, bob: Int, lantern: Boolean, load: Load): Int {
        val d = if (flip) -1 else 1
        val by = by0 - bob * 2
        val legH = if (L.small) 2 else 3; val torsoH = if (L.small) 3 else 4
        val step = ((t * 6).toInt() + seed) and 1 // the figure's step
        // the figure's frame standing with its feet on [y] (see figure): the hips' row, the shoulders', the head's top
        fun baseAt(y: Int) = if (L.skirt) y - legH - 1 else y - legH
        fun tTopAt(y: Int) = baseAt(y) - torsoH
        val held = by - 5 // a lantern held out: the hand's row, the lantern hanging from it down to the ground
        when (a) {
            Activity.SIT, Activity.WARM -> {
                figure(bx, by, L, false, flip, true, seed)
                if (lantern) lamp2(bx - 5 * d, by0 - 4, seed, ground = true)
                return 11
            }
            Activity.TEND, Activity.GATHER -> {
                // crouched, bent over the work, the hand going to and fro (what they hold in it too: the smoker's puff); picking
                // berries or mushrooms, the basket on the ground behind
                val out = tendOut(seed)
                if (a == Activity.GATHER) basketOnGround2(bx - 6 * d, by0, d)
                figure(bx, by0, L, false, flip, true, seed, reach = intArrayOf(bx + (if (out) 5 else 4) * d, by0 - (if (out) 2 else 3)), held = a == Activity.TEND, lean = 1)
                if (a == Activity.GATHER && out) c.set(bx + 5 * d, by0 - 1, picked())
                if (lantern) lamp2(bx - 5 * d, by0 - 4, seed, ground = true)
                return 11
            }
            Activity.MEND -> {
                // sitting, the work in the lap (a basket half woven), the near hand drawing the twig through it and back
                figure(bx, by, L, false, flip, true, seed, reach = intArrayOf(bx + 3 * d, by - (if (stitchUp(seed)) 6 else 4)), held = false)
                c.hline(bx - 1, bx + 2, by - 3, Pal.WOOD_L); c.hline(bx - 1, bx + 2, by - 4, Pal.WOOD_M); c.set(bx, by - 4, Pal.WOOD_L)
                if (lantern) lamp2(bx - 5 * d, by0 - 4, seed, ground = true)
                return 11
            }
            Activity.HIDE -> {
                // crouched in the hiding place, still
                figure(bx, by0, L, false, flip, true, seed, lean = 1)
                return 11
            }
            Activity.SWEEP -> {
                // both hands on the broom slanting down in front, its head going to and fro on the ground, a puff of dust
                val tTop = tTopAt(by)
                val out = sweepOut(seed)
                val ex = bx + (if (out) 11 else 8) * d; val ey = by - 2
                val mx = bx + 5 * d; val my = tTop + 3
                c.line(bx + 3 * d, tTop, ex, ey, Pal.WOOD_M)
                figure(bx, by, L, false, flip, false, seed, reach = intArrayOf(mx, my, bx + 3 * d, tTop + 1), held = false)
                c.hline(ex - d, ex + 2 * d, by - 1, Pal.HAY_D); c.hline(ex, ex + d, by - 2, Pal.HAY_M); c.set(ex + 2 * d, by - 2, Pal.HAY_L)
                if (out) { c.set(ex + 3 * d, by - 2, DUST); c.set(ex + 4 * d, by - 3, DUST) }
            }
            Activity.HANG -> {
                // at the washing line beside them: up to peg a cloth, down to the basket at their feet for the next
                line2(bx, by, d, rug = false)
                val up = reachUp(seed)
                val hTop = tTopAt(by) - 5
                figure(bx, by, L, false, flip, false, seed, reach = if (up) intArrayOf(bx + 4 * d, hTop - 1) else intArrayOf(bx + 4 * d, tTopAt(by) + 4), held = false)
                if (up) { c.fillRect(if (d > 0) bx + 4 else bx - 6, hTop, 3, 3, CLOTH); c.set(bx + 5 * d, hTop + 2, CLOTH_D) }
                basketOnGround2(bx - 5 * d, by, d, CLOTH)
            }
            Activity.BEAT -> {
                // the rug over the line in front, the beater up over the shoulder and down on it, the dust flying off
                line2(bx, by, d, rug = true)
                val u = beat(seed)
                val tTop = tTopAt(by); val hTop = tTop - 5
                if (u < 0.5f) {
                    figure(bx, by, L, false, flip, false, seed, reach = intArrayOf(bx + 3 * d, hTop + 1), held = false)
                    c.line(bx + 3 * d, hTop, bx + d, hTop - 5, Pal.WOOD_M); c.fillRect(if (d > 0) bx - 1 else bx, hTop - 7, 2, 2, Pal.WOOD_L)
                } else {
                    figure(bx, by, L, false, flip, false, seed, reach = intArrayOf(bx + 5 * d, tTop + 1), held = false)
                    c.line(bx + 5 * d, tTop, bx + 8 * d, tTop - 2, Pal.WOOD_M); c.fillRect(if (d > 0) bx + 8 else bx - 9, tTop - 4, 2, 2, Pal.WOOD_L)
                    if (u < 0.9f) {
                        val p = (u - 0.5f) / 0.4f
                        for (j in 0..3) c.set(bx + (9 + j * 2 + (p * 3).toInt()) * d, tTop - 3 - (p * 4).toInt() + j % 2, DUST)
                    }
                }
            }
            Activity.FEED -> {
                // a handful of grain thrown out to the hens pecking about, the bowl in the far hand
                val tTop = tTopAt(by)
                val out = throwing(seed)
                figure(bx, by, L, false, flip, false, seed, reach = intArrayOf(bx + (if (out) 6 else 4) * d, tTop + (if (out) 1 else 4), bx - 3 * d, tTop + 4), held = false)
                c.hline(bx - 5 * d, bx - 3 * d, tTop + 5, Pal.WOOD_L); c.set(bx - 4 * d, tTop + 4, Pal.GOLD)
                if (out) for (j in 0..3) c.set(bx + (8 + j * 2) * d, tTop + 2 + j * 2, Pal.GOLD)
                hen2(bx + 9 * d, by + 2, -d, pecking(seed, 0), white = true)
                hen2(bx + 5 * d, by + 4, d, pecking(seed, 1), white = (seed ushr 9) % 2 == 0)
                hen2(bx + 12 * d, by + 5, -d, pecking(seed, 2), white = (seed ushr 10) % 2 == 0)
            }
            Activity.COUNT -> {
                // counting, both hands over the eyes
                val hTop = tTopAt(by) - 5
                figure(bx, by, L, false, flip, false, seed, reach = intArrayOf(bx + 2 * d, hTop + 3, bx - d, hTop + 3), held = false)
            }
            Activity.WASH -> {
                // kneeling at the bank, both hands scrubbing the cloth to and fro on the stones, a splash now and then
                val out = scrubOut(seed)
                val nx = bx + (if (out) 6 else 4) * d; val ny = by0 - 2
                c.hline(nx - d, nx + 2 * d, by0 - 1, CLOTH); c.set(nx + 2 * d, by0 - 1, CLOTH_D); c.set(nx + d, by0 - 2, CLOTH)
                figure(bx, by0, L, false, flip, true, seed, reach = intArrayOf(nx, ny, nx - 2 * d, ny + 1), held = false, lean = 1)
                val sp = splash(seed)
                if (sp >= 0f) {
                    val sx = nx + 4 * d; val up = (sin(sp * PI) * 4).toInt()
                    c.set(sx, by0 - 2 - up, Pal.WATER_L); c.set(sx + d, by0 - 1 - up / 2, CLOTH); c.set(sx - d, by0 - 3 - up / 2, Pal.WATER_L)
                }
                if (lantern) lamp2(bx - 5 * d, by0 - 4, seed, ground = true)
                return 11
            }
            Activity.HOE -> {
                val tTop = tTopAt(by); val hTop = tTop - 5
                if (hoeUp(seed)) {
                    // the handle up over the shoulder behind the head, the blade up behind it
                    c.line(bx + 4 * d, hTop + 2, bx - 2 * d, hTop - 5, Pal.WOOD_M)
                    c.fillRect(if (d > 0) bx - 4 else bx + 3, hTop - 5, 2, 3, Pal.SLATE_M); c.set(bx - 3 * d, hTop - 5, Pal.SLATE_L)
                    figure(bx, by, L, false, flip, false, seed, reach = intArrayOf(bx + 4 * d, hTop + 1), held = false)
                } else {
                    // brought down, the blade in the soil in front, a clod flying
                    figure(bx, by, L, false, flip, false, seed, reach = intArrayOf(bx + 5 * d, tTop + 2), held = false)
                    c.line(bx + 4 * d, tTop + 1, bx + 10 * d, by - 3, Pal.WOOD_M)
                    c.set(bx + 5 * d, tTop + 2, L.skin)
                    c.hline(bx + 8 * d, bx + 10 * d, by - 2, Pal.SLATE_L); c.hline(bx + 8 * d, bx + 10 * d, by - 1, Pal.SLATE_M)
                    c.set(bx + 11 * d, by - 1, Pal.SOIL_D)
                    val p = clod(seed)
                    if (p >= 0f) {
                        val cx = bx + 12 * d + (p * 5).toInt() * d; val cy = by - 2 - (sin(p * PI) * 5).toInt()
                        c.set(cx, cy, Pal.SOIL_M); c.set(cx + d, cy, Pal.SOIL_D); c.set(cx - 2 * d, cy + 2, Pal.SOIL_L)
                    }
                }
            }
            Activity.HAMMER -> {
                val hTop = tTopAt(by) - 5
                val ax = bx + 7 * d
                val up = hammerUp(seed)
                figure(bx, by, L, false, flip, false, seed, reach = if (up) intArrayOf(bx + 4 * d, hTop + 1) else intArrayOf(bx + 4 * d, by - 8), held = false)
                anvil2(ax, by, d)
                if (up) {
                    c.vline(bx + 4 * d, hTop - 2, hTop, Pal.WOOD_M)
                    c.fillRect(if (d > 0) bx + 3 else bx - 5, hTop - 4, 3, 2, Pal.SLATE_M); c.hline(bx + 3 * d, bx + 5 * d, hTop - 4, Pal.SLATE_L)
                } else {
                    c.hline(bx + 5 * d, ax - 2 * d, by - 8, Pal.WOOD_M)
                    c.fillRect(if (d > 0) ax - 1 else ax, by - 10, 2, 3, Pal.SLATE_M); c.set(ax - d, by - 10, Pal.SLATE_L)
                    val p = strike(seed)
                    if (p >= 0f) sparksAt(ax, by - 8, p, 4, 6f, seed, round(seed, 0.9))
                }
            }
            Activity.CARRY -> {
                val b = step // the load bobs with the step
                val base = baseAt(by); val tTop = base - torsoH; val hTop = tTop - 5
                val swing = if (step == 0) 1 else -1
                when (load) {
                    Load.HAY -> {
                        // a sheaf over the shoulders, behind the head: the cut stalks in front, the ears behind; a hand holds it
                        sheaf(bx + 6 * d, tTop - b, bx - 7 * d, hTop - 1 - b, 3)
                        figure(bx, by, L, true, flip, false, seed, reach = intArrayOf(bx + 4 * d, tTop - 1 - b), held = false)
                    }
                    Load.WOOD -> {
                        // two logs in the arms in front, the cut ends pale, the near hand under them
                        figure(bx, by, L, true, flip, false, seed, reach = intArrayOf(bx + 4 * d, base - b), held = false)
                        log(bx - 2 * d, base - 2 - b, 7, 2, d); log(bx - d, base - 4 - b, 7, 2, d)
                    }
                    Load.WATER -> {
                        // a pail in the swinging hand
                        figure(bx, by, L, true, flip, false, seed, held = false)
                        pail2(bx + 5 * d, base - d * swing, d)
                    }
                    Load.BASKET -> {
                        // a basket hanging on the forearm
                        val hy = base - 3 - b
                        figure(bx, by, L, true, flip, false, seed, reach = intArrayOf(bx + 4 * d, hy), held = false)
                        basket2(bx + 5 * d, hy + 1, d)
                    }
                    Load.NONE -> figure(bx, by, L, true, flip, false, seed)
                }
                if (lantern) lamp2(bx - 4 * d, base + d * swing, seed) // in the far hand
                return 14
            }
            Activity.PLAY, Activity.CHEER -> {
                // skipping in place; now and then both arms up (cheering: most of the time)
                val up = if (a == Activity.CHEER) cheerUp(seed) else cheering(seed)
                val y = by - (if (sin(t * 9 + (seed and 0xFF) * 0.1) > 0.2) 2 else 0)
                val hTop = tTopAt(y) - 5
                figure(bx, y, L, !up, flip, false, seed, reach = if (up) intArrayOf(bx + 5 * d, hTop, bx - 5 * d, hTop) else null, held = !up)
            }
            Activity.GUARD -> {
                // facing them, the near hand up with the tool (shaken now and then): a torch at night, the hammer, a stick
                val hTop = tTopAt(by) - 5
                val hx = bx + 4 * d; val hy = hTop + 2 - shaking(seed)
                figure(bx, by, L, false, flip, false, seed, reach = intArrayOf(hx, hy), held = false)
                when {
                    lantern -> { c.vline(hx, hy - 3, hy, Pal.WOOD_M); torch2(hx, hy - 4, seed) }
                    L.prop == PROP_HAMMER -> { c.vline(hx, hy - 3, hy, Pal.WOOD_M); c.fillRect(if (d > 0) hx - 1 else hx - 1, hy - 5, 3, 2, Pal.SLATE_M); c.hline(hx - 1, hx + 1, hy - 5, Pal.SLATE_L) }
                    else -> c.vline(hx, hy - 4, by - 1, Pal.WOOD_M)
                }
            }
            Activity.HAT -> {
                // hurrying, the near hand on the head keeping the hat on
                val hTop = tTopAt(by) - 5
                figure(bx, by, L, true, flip, false, seed, reach = intArrayOf(bx + 2 * d, hTop - 1), held = false)
            }
            Activity.REPAIR -> {
                // at the wall in front, the hammer up and down on a plank held against it, more planks leaning behind
                val hTop = tTopAt(by) - 5
                val up = hammerUp(seed)
                for (j in 0..1) { c.vline(bx - (5 + j * 2) * d, by - 7 + j, by - 1, if (j == 0) Pal.WOOD_L else Pal.WOOD_M) }
                c.vline(bx + 7 * d, by - 11, by - 3, Pal.WOOD_L); c.vline(bx + 8 * d, by - 11, by - 3, Pal.WOOD_D)
                figure(bx, by, L, false, flip, false, seed, reach = if (up) intArrayOf(bx + 4 * d, hTop + 1) else intArrayOf(bx + 5 * d, by - 8), held = false)
                if (up) { c.vline(bx + 4 * d, hTop - 2, hTop, Pal.WOOD_M); c.fillRect(if (d > 0) bx + 3 else bx - 5, hTop - 4, 3, 2, Pal.SLATE_M) }
                else { c.hline(bx + 5 * d, bx + 6 * d, by - 8, Pal.WOOD_M); c.fillRect(if (d > 0) bx + 6 else bx - 7, by - 10, 2, 3, Pal.SLATE_M) }
            }
            Activity.MUSIC -> {
                // the harmonika held in both hands in front, its bellows going in and out, the notes going up
                val tTop = tTopAt(by)
                val out = bellows(seed)
                figure(bx, by, L, false, flip, false, seed, reach = intArrayOf(bx + (if (out) 6 else 5) * d, tTop + 3, bx + 2 * d, tTop + 3), held = false)
                val x0 = bx + 2 * d; val x1 = bx + (if (out) 6 else 5) * d
                c.hline(minOf(x0, x1), maxOf(x0, x1), tTop + 2, Pal.FLAG_RED); c.hline(minOf(x0, x1), maxOf(x0, x1), tTop + 4, Pal.FLAG_RED)
                for (x in minOf(x0, x1)..maxOf(x0, x1)) c.set(x, tTop + 3, if ((x + (if (out) 1 else 0)) % 2 == 0) Pal.FLAG_WHITE else Col.scale(Pal.FLAG_RED, 0.7f))
                notes(bx + 4 * d, tTop - 8, seed, 2)
            }
            Activity.CHAT -> {
                val g = gesturing(seed)
                val tTop = tTopAt(by)
                val r = when {
                    g -> { val hi = ((t * 4).toInt() and 1) == 0; intArrayOf(bx + (if (hi) 4 else 5) * d, tTop + (if (hi) 0 else 1)) }
                    lantern -> intArrayOf(bx + 4 * d, held)
                    else -> null
                }
                val open = talking(seed) && ((t * 7).toInt() and 1) == 0
                figure(bx, by, L, false, flip, false, seed, reach = r, held = r == null, open = open)
                if (lantern && r != null) lamp2(r[0], r[1] + 1, seed)
                return 14
            }
            Activity.WAVE -> {
                val w = waving(seed)
                val y = by - (if (w in 0f..0.3f) 2 else 0) // a little hop as the wave starts
                val hTop = tTopAt(y) - 5
                if (w >= 0f) {
                    // the arm up, the open hand waving to and fro over the head's side
                    val hx = bx + 5 * d + (if (((t * 6).toInt() and 1) == 0) 0 else d)
                    figure(bx, y, L, false, flip, false, seed, reach = intArrayOf(hx, hTop + 1), held = false)
                    c.set(hx, hTop, L.skin)
                    if (lantern) lamp2(hx, hTop - 4, seed) // held up high
                } else if (lantern) {
                    figure(bx, y, L, false, flip, false, seed, reach = intArrayOf(bx + 4 * d, held), held = false)
                    lamp2(bx + 4 * d, held + 1, seed)
                } else figure(bx, y, L, false, flip, false, seed)
                return 14
            }
            else -> {
                // standing, walking, fishing, chopping: as ever; a lantern held out in the near hand when standing or walking
                val walking = a == Activity.WALK
                if (lantern && (walking || a == Activity.STAND)) {
                    val ly = held - (if (walking) step else 0)
                    figure(bx, by, L, walking, flip, false, seed, reach = intArrayOf(bx + 4 * d, ly), held = false)
                    lamp2(bx + 4 * d, ly + 1, seed)
                } else figure(bx, by, L, walking, flip, false, seed)
            }
        }
        if (lantern && a != Activity.WALK && a != Activity.STAND) lamp2(bx - 5 * d, by0 - 4, seed, ground = true)
        return 14
    }

    /**
     * A lantern at the middle detail, 3 px wide, its cap's row at [y] (hanging from a hand just above; [ground]: set down,
     * its ring on top): the dark cap and foot, the glass lit warm between, the flame in the middle flickering; its light.
     */
    private fun lamp2(x: Int, y: Int, seed: Int, ground: Boolean = false) {
        val f = flicker(seed)
        if (ground) c.set(x, y - 1, LAMP)
        c.hline(x - 1, x + 1, y, LAMP); c.hline(x - 1, x + 1, y + 3, LAMP)
        glow {
            for (r in 1..2) { c.set(x - 1, y + r, Pal.WINDOW_LIT); c.set(x + 1, y + r, if (r == 1) Pal.WINDOW_LIT_HI else Pal.WINDOW_LIT) }
            c.set(x, y + 1, if (f > 0.5f) Pal.FLAME[0] else Pal.FLAME[1]); c.set(x, y + 2, if (f > 0.25f) Pal.FLAME[1] else Pal.FLAME[2])
        }
        s.light(x + 0.5f, y + 2f, LAMP_R, LAMP_I * (0.85f + 0.15f * f))
    }

    /** The anvil on its stump at the middle detail, the stump's foot at ([ax], [by]): the iron glowing on its face, the horn toward [d]. */
    private fun anvil2(ax: Int, by: Int, d: Int) {
        c.fillRect(ax - 2, by - 3, 4, 3, Pal.LOG_M); c.hline(ax - 2, ax + 1, by - 3, Pal.LOG_L); c.vline(ax + 1, by - 2, by - 1, Pal.LOG_D)
        c.hline(ax - 1, ax + 1, by - 4, Pal.SLATE_D)
        c.hline(ax - 2, ax + 2, by - 5, Pal.SLATE_M); c.hline(ax - 2, ax + 2, by - 6, Pal.SLATE_L)
        c.set(ax + 3 * d, by - 6, Pal.SLATE_M)
        glow { c.set(ax - d, by - 7, Pal.FLAME[2]); c.set(ax, by - 7, Pal.FLAME[1]) }
    }

    /**
     * A wooden pail at the middle detail hanging by its bail from the hand beside it ([x] its middle, a column out from
     * the hand; [y] its rim's row, just under the hand): the water in it glinting, an iron hoop.
     */
    private fun pail2(x: Int, y: Int, d: Int) {
        c.set(x - d, y - 1, Pal.SLATE_D); c.set(x, y - 1, Pal.SLATE_D)
        c.set(x - d, y, Pal.WOOD_D); c.set(x, y, if (((t * 3).toInt() and 1) == 0) Pal.WATER_L else Pal.WATER_M); c.set(x + d, y, Pal.WOOD_D)
        c.hline(x - 1, x + 1, y + 1, Pal.SLATE_D)
        c.set(x - d, y + 2, Pal.WOOD_L); c.set(x, y + 2, Pal.WOOD_M); c.set(x + d, y + 2, Pal.WOOD_D)
    }

    /** A basket at the middle detail hanging on the forearm ([x] its middle, [y] its top row): woven, an apple and a loaf in it. */
    private fun basket2(x: Int, y: Int, d: Int) {
        c.set(x - d, y, Pal.GERANIUM); c.set(x, y, GOURD); c.set(x + d, y, Pal.LEAF)
        c.hline(x - 2 * d, x + d, y + 1, Pal.WOOD_D)
        c.hline(x - 2 * d, x + d, y + 2, Pal.WOOD_M); c.set(x - d, y + 2, Pal.WOOD_L); c.set(x + d, y + 2, Pal.WOOD_L)
        c.hline(x - d, x, y + 3, Pal.WOOD_M)
    }

    // ---- the scene's people (k = 3)

    /** Someone at the finest detail: the scene's sprite at the work (its arms by [PeoplePainter.draw]'s reach), the tool, the load, the lantern. */
    private fun fine(bx: Int, by0: Int, art: String?, seed: Int, a: Activity, flip: Boolean, bob: Int, lantern: Boolean, load: Load): Int {
        val ar = art?.takeIf { it != "baby" } ?: ANON_ARTS[(seed ushr 5).mod(ANON_ARTS.size)]
        val P = s.people
        val d = if (flip) -1 else 1
        val by = by0 - bob * 3
        val H = P.height(ar)
        val B = P.body(ar, seed)
        val step = ((t * 7).toInt() + seed) and 1 // the sprite's step
        val held = by + B.hand - 3 // a lantern held out: the hand's row, the lantern hanging from it
        when (a) {
            Activity.SIT, Activity.WARM -> {
                P.draw(c, ar, bx, by, t, seed, false, flip, false, true)
                if (lantern) lamp3(bx - d * (B.tw + 6), by0 - 6, seed)
                return H - 6
            }
            Activity.MEND -> {
                // sitting, the work in the lap (a basket half woven), the near hand drawing the twig through it and back
                P.draw(c, ar, bx, by, t, seed, false, flip, false, true, reach = intArrayOf(bx + d * (B.tw + 1), by - (if (stitchUp(seed)) 12 else 8)), holding = true)
                for (yy in by - 8..by - 6) for (xx in bx - 4..bx + 4) c.set(xx, yy, if ((xx + yy) % 2 == 0) Pal.WOOD_L else Pal.WOOD_M)
                c.hline(bx - 4, bx + 4, by - 9, Pal.WOOD_D)
                if (stitchUp(seed)) c.line(bx + d * (B.tw + 1), by - 12, bx + d * 2, by - 8, Pal.WOOD_L)
                if (lantern) lamp3(bx - d * (B.tw + 6), by0 - 6, seed)
                return H - 6
            }
            Activity.HIDE -> {
                // crouched in the hiding place, still
                P.draw(c, ar, bx, by0, t, seed, false, flip, false, false, crouch = true)
                return P.crouchHeight(ar, seed)
            }
            Activity.SWEEP -> {
                // both hands on the broom slanting down in front, its head going to and fro on the ground, a puff of dust
                val out = sweepOut(seed)
                val ex = bx + d * (B.tw + (if (out) 18 else 12)); val ey = by - 4
                val sx = bx + d * (B.tw + 1); val sy = by + B.armTop
                c.line(sx, sy, ex, ey, Pal.WOOD_M); c.line(sx + d, sy, ex + d, ey, Pal.WOOD_D)
                P.draw(c, ar, bx, by, t, seed, false, flip, false, false,
                    reach = intArrayOf(bx + d * (B.tw + 6), sy + (ey - sy) * 5 / (abs(ex - sx).coerceAtLeast(1)), sx + d, sy + 1), holding = true)
                // the broom's head: a bundle of birch twigs bound to the handle
                c.fillRect(if (d > 0) ex - 2 else ex - 4, by - 4, 7, 3, Pal.HAY_M); c.hline(if (d > 0) ex - 2 else ex - 4, if (d > 0) ex + 4 else ex + 2, by - 1, Pal.HAY_D)
                c.set(ex, by - 5, Pal.WOOD_D); c.set(ex + d, by - 5, Pal.WOOD_D)
                for (j in 0..2) c.set(ex + d * (j * 2 - 2), by - 2, Pal.HAY_L)
                if (out) for (j in 0..2) c.ditherCircle((ex + d * (7 + j * 3)).toFloat(), by - 3f - j, 1.5f + j * 0.5f, DUST, 0.6f - j * 0.15f)
            }
            Activity.HANG -> {
                // at the washing line beside them: up to peg a cloth, down to the basket at their feet for the next
                val lineY = by + B.hTop - 3
                line3(bx, by, d, lineY, rug = false)
                val up = reachUp(seed)
                if (up) {
                    P.draw(c, ar, bx, by, t, seed, false, flip, false, false, reach = intArrayOf(bx + d * (B.tw + 4), lineY + 1, bx + d * (B.tw + 9), lineY + 2), holding = true)
                    c.fillRect(if (d > 0) bx + B.tw + 4 else bx - B.tw - 9, lineY + 1, 6, 7, CLOTH); c.vline(bx + d * (B.tw + 6), lineY + 3, lineY + 7, CLOTH_D)
                } else P.draw(c, ar, bx, by, t, seed, false, flip, false, false, reach = intArrayOf(bx - d * (B.tw + 3), by + B.hand + 1), holding = true)
                basket3(bx - d * (B.tw + 6), by - 5, d, CLOTH)
            }
            Activity.BEAT -> {
                // the rug over the line in front, the beater up over the shoulder and down on it, the dust flying off
                val lineY = by + B.hTop - 3
                line3(bx, by, d, lineY, rug = true)
                val u = beat(seed)
                if (u < 0.5f) {
                    val hx = bx + d * (B.hw + 1); val hy = by + B.hTop + 4
                    P.draw(c, ar, bx, by, t, seed, false, flip, false, false, reach = intArrayOf(hx, hy), holding = true)
                    c.line(hx, hy, hx - d * 4, hy - 9, Pal.WOOD_M)
                    beater3(hx - d * 4, hy - 12)
                } else {
                    val hx = bx + d * (B.tw + 5); val hy = by + B.armTop + 2
                    P.draw(c, ar, bx, by, t, seed, false, flip, false, false, reach = intArrayOf(hx, hy), holding = true)
                    c.line(hx, hy, hx + d * 8, hy - 3, Pal.WOOD_M)
                    beater3(hx + d * 10, hy - 4)
                    if (u < 0.9f) {
                        val p = (u - 0.5f) / 0.4f
                        for (j in 0..3) c.ditherCircle((hx + d * (12 + j * 4 + p * 6)).toFloat(), hy - 6f - p * 8f + j * 2, 1.5f + p * 2f, DUST, 0.7f - p * 0.5f)
                    }
                }
            }
            Activity.FEED -> {
                // a handful of grain thrown out to the hens pecking about, the bowl in the far hand
                val out = throwing(seed)
                P.draw(c, ar, bx, by, t, seed, false, flip, false, false,
                    reach = intArrayOf(bx + d * (B.tw + (if (out) 9 else 4)), by + B.armTop + (if (out) 1 else 5), bx - d * (B.tw + 2), by + B.hand - 2), holding = true)
                val bwx = bx - d * (B.tw + 3); val bwy = by + B.hand - 1
                c.fillRect(bwx - 3, bwy, 7, 2, Pal.WOOD_L); c.hline(bwx - 2, bwx + 2, bwy + 2, Pal.WOOD_D); c.hline(bwx - 2, bwx + 2, bwy - 1, Pal.GOLD)
                if (out) for (j in 0..5) c.set(bx + d * (B.tw + 12 + j * 3), by + B.armTop + 2 + j * 3 + (j % 2), Pal.GOLD)
                hen3(bx + d * (B.tw + 24), by + 5, -d, pecking(seed, 0), white = true)
                hen3(bx + d * (B.tw + 12), by + 11, d, pecking(seed, 1), white = (seed ushr 9) % 2 == 0)
                hen3(bx + d * (B.tw + 33), by + 14, -d, pecking(seed, 2), white = (seed ushr 10) % 2 == 0)
            }
            Activity.COUNT -> {
                // counting, both hands over the eyes
                P.draw(c, ar, bx, by, t, seed, false, flip, false, false, reach = intArrayOf(bx + d * 2, by + B.eyeRow, bx - d * 2, by + B.eyeRow), holding = true)
            }
            Activity.TEND, Activity.WASH, Activity.GATHER -> {
                val C = P.body(ar, seed, crouch = true)
                if (a == Activity.GATHER) {
                    // picking: the hand going to the ground and back, the basket behind
                    basket3(bx - d * (C.tw + 7), by0 - 5, d, picked())
                    val out = tendOut(seed)
                    P.draw(c, ar, bx, by0, t, seed, false, flip, false, false, crouch = true,
                        reach = intArrayOf(bx + d * (C.tw + (if (out) 5 else 3)), by0 - (if (out) 2 else 5)), holding = true)
                    if (out) { c.set(bx + d * (C.tw + 6), by0 - 2, picked()); c.set(bx + d * (C.tw + 7), by0 - 2, picked()) }
                } else if (a == Activity.TEND) {
                    // squatting at the work, the hand going to and fro (with what it holds: the smoker puffing)
                    val out = tendOut(seed)
                    P.draw(c, ar, bx, by0, t, seed, false, flip, false, false, crouch = true,
                        reach = intArrayOf(bx + d * (C.tw + (if (out) 5 else 3)), by0 - (if (out) 3 else 5)))
                } else {
                    // kneeling at the bank, both hands on the cloth scrubbing to and fro, a splash now and then
                    val out = scrubOut(seed)
                    val nx = bx + d * (C.tw + (if (out) 8 else 4)); val ny = by0 - 4
                    cloth3(nx - d, by0, d)
                    P.draw(c, ar, bx, by0, t, seed, false, flip, false, false, crouch = true, reach = intArrayOf(nx, ny, nx - 3 * d, ny + 1), holding = true)
                    val sp = splash(seed)
                    if (sp >= 0f) for (j in 0..2) {
                        val up = (sin(sp * PI) * (5 + j * 2)).toInt()
                        c.set(nx + d * (7 + j * 2 + (sp * 3).toInt()), by0 - 2 - up, if (j == 1) CLOTH else Pal.WATER_L)
                    }
                }
                if (lantern) lamp3(bx - d * (C.tw + 6), by0 - 6, seed)
                return P.crouchHeight(ar, seed)
            }
            Activity.HOE -> {
                if (hoeUp(seed)) {
                    // raised over the shoulder: the handle up past the head (behind it), the blade up behind
                    val hx = bx + d * (B.hw + 1); val hy = by + B.hTop + 4
                    val ex = bx - d * 2; val ey = by + B.hTop - 7
                    c.line(hx + d, hy + 4, ex, ey, Pal.WOOD_M)
                    c.fillRect(if (d > 0) ex - 4 else ex + 1, ey, 4, 3, Pal.SLATE_M); c.hline(ex - d, ex - 4 * d, ey, Pal.SLATE_L); c.hline(ex - d, ex - 4 * d, ey + 2, Pal.SLATE_D)
                    P.draw(c, ar, bx, by, t, seed, false, flip, false, false, reach = intArrayOf(hx, hy), holding = true)
                } else {
                    // brought down: both hands on the handle, the blade in the soil in front, a clod flying
                    val fx = bx + d * (B.tw + 3); val fy = by + B.armTop + 3
                    val ex = bx + d * (B.tw + 16); val ey = by - 4
                    val nx = bx + d * (B.tw + 7); val ny = fy + (ey - fy) * (B.tw + 7 - (B.tw + 3)) / (B.tw + 16 - (B.tw + 3))
                    c.line(fx - 2 * d, fy - 1, ex, ey, Pal.WOOD_M)
                    P.draw(c, ar, bx, by, t, seed, false, flip, false, false, reach = intArrayOf(nx, ny - 1, fx, fy - 1), holding = true)
                    c.fillRect(if (d > 0) ex - 3 else ex, by - 3, 4, 3, Pal.SLATE_M); c.hline(ex, ex - 3 * d, by - 3, Pal.SLATE_L)
                    c.vline(ex, by - 2, by - 1, Pal.SLATE_D)
                    c.set(ex + d, by - 1, Pal.SOIL_D); c.set(ex + 2 * d, by - 1, Pal.SOIL_M)
                    val p = clod(seed)
                    if (p >= 0f) {
                        val cx = ex + d * (3 + (p * 8).toInt()); val cy = by - 4 - (sin(p * PI) * 8).toInt()
                        c.fillRect(cx, cy, 2, 2, Pal.SOIL_M); c.set(cx + 1, cy + 1, Pal.SOIL_D); c.set(cx - 3 * d, cy + 3, Pal.SOIL_L)
                    }
                }
            }
            Activity.HAMMER -> {
                val ax = bx + d * (B.tw + 10)
                if (hammerUp(seed)) {
                    val hx = bx + d * (B.hw + 1); val hy = by + B.hTop + 4
                    P.draw(c, ar, bx, by, t, seed, false, flip, false, false, reach = intArrayOf(hx, hy), holding = true)
                    anvil3(ax, by, d)
                    // the hammer up: the handle over the fist, the head across its top
                    c.vline(hx, hy - 6, hy - 1, Pal.WOOD_M)
                    c.fillRect(hx - 2, hy - 9, 5, 3, Pal.SLATE_M); c.hline(hx - 2, hx + 2, hy - 9, Pal.SLATE_L); c.hline(hx - 2, hx + 2, hy - 7, Pal.SLATE_D)
                } else {
                    // down on the iron, sparks flying
                    val hx = ax - d * 7
                    P.draw(c, ar, bx, by, t, seed, false, flip, false, false, reach = intArrayOf(hx, by - 15), holding = true)
                    anvil3(ax, by, d)
                    c.hline(hx + 2 * d, ax - 2 * d, by - 14, Pal.WOOD_M)
                    c.fillRect(ax - 1, by - 15, 3, 4, Pal.SLATE_M); c.hline(ax - 1, ax + 1, by - 15, Pal.SLATE_L); c.vline(ax + d, by - 14, by - 12, Pal.SLATE_D)
                    val p = strike(seed)
                    if (p >= 0f) {
                        sparksAt(ax, by - 12, p, 6, 12f, seed, round(seed, 0.9))
                        s.light(ax.toFloat(), by - 12f, 10f, 0.3f * (1f - p))
                    }
                }
            }
            Activity.CARRY -> {
                val b = step // the load bobs with the step
                val swing = if (step == 0) 1 else -1
                when (load) {
                    Load.HAY -> {
                        sheaf(bx + d * (B.tw + 8), by + B.armTop - 1 - b, bx - d * (B.hw + 8), by + B.hTop + 3 - b, 5)
                        P.draw(c, ar, bx, by, t, seed, false, flip, true, false, reach = intArrayOf(bx + d * (B.tw + 3), by + B.armTop - 3 - b), holding = true)
                    }
                    Load.WOOD -> {
                        P.draw(c, ar, bx, by, t, seed, false, flip, true, false,
                            reach = intArrayOf(bx + d * (B.tw + 2), by + B.hand - 1 - b, bx + d * (B.tw - 1), by + B.hand - b), holding = true)
                        val y = by + B.legTop - b
                        log(bx - d * 4, y - 3, 13, 3, d); log(bx - d * 3, y - 6, 13, 3, d); log(bx - d, y - 9, 10, 3, d)
                        c.fillRect(if (d > 0) bx + B.tw + 2 else bx - B.tw - 3, y - 1, 2, 2, Looks.of(ar, seed).skin)
                    }
                    Load.WATER -> {
                        P.draw(c, ar, bx, by, t, seed, false, flip, true, false, holding = true)
                        pail3(P.heldX + d, P.heldY + 2, d)
                    }
                    Load.BASKET -> {
                        val hy = by + B.hand - 3 - b
                        P.draw(c, ar, bx, by, t, seed, false, flip, true, false, reach = intArrayOf(bx + d * (B.tw + 3), hy), holding = true)
                        basket3(P.heldX + d * 2, hy + 2, d)
                    }
                    Load.NONE -> P.draw(c, ar, bx, by, t, seed, false, flip, true, false)
                }
                if (lantern) lamp3(bx - d * (B.tw + 2), by + B.hand + swing + 2, seed) // in the far hand
                return H
            }
            Activity.PLAY, Activity.CHEER -> {
                // hopping and skipping in place, a smile; now and then both arms up in a jump (cheering: most of the time)
                val up = if (a == Activity.CHEER) cheerUp(seed) else cheering(seed)
                P.draw(c, ar, bx, by, t, seed, false, flip, !up, false, pose = if (up) Pose.CHEER else Pose.HAPPY)
            }
            Activity.GUARD -> {
                // facing them, the near hand up with the tool (shaken now and then): a torch at night, the hammer, a stick
                val hx = bx + d * (B.hw + 1); val hy = by + B.hTop + 6 - shaking(seed) * 2
                P.draw(c, ar, bx, by, t, seed, false, flip, false, false, reach = intArrayOf(hx, hy), holding = true)
                when {
                    lantern -> { c.vline(hx, hy - 9, hy - 1, Pal.WOOD_M); c.vline(hx + d, hy - 9, hy - 1, Pal.WOOD_D); torch3(hx, hy - 10, seed) }
                    ar == "smith" -> {
                        c.vline(hx, hy - 6, hy - 1, Pal.WOOD_M)
                        c.fillRect(hx - 2, hy - 9, 5, 3, Pal.SLATE_M); c.hline(hx - 2, hx + 2, hy - 9, Pal.SLATE_L); c.hline(hx - 2, hx + 2, hy - 7, Pal.SLATE_D)
                    }
                    else -> { c.vline(hx, hy - 12, by - 1, Pal.WOOD_M); c.vline(hx + d, hy - 12, by - 1, Pal.WOOD_D) }
                }
            }
            Activity.HAT -> {
                // hurrying, the near hand on the head keeping the hat on
                P.draw(c, ar, bx, by, t, seed, false, flip, true, false, reach = intArrayOf(bx + d * 2, by + B.hTop + 1), holding = true)
            }
            Activity.REPAIR -> {
                // at the wall in front, the hammer up and down on a plank held against it, more planks leaning behind
                for (j in 0..2) {
                    val px = bx - d * (B.tw + 6 + j * 3)
                    c.fillRect(if (d > 0) px - 1 else px, by - 16 + j * 2, 2, 16 - j * 2, if (j % 2 == 0) Pal.WOOD_L else Pal.WOOD_M)
                }
                val wx = bx + d * (B.tw + 12)
                c.fillRect(if (d > 0) wx else wx - 2, by - 26, 3, 20, Pal.WOOD_L); c.vline(if (d > 0) wx + 2 else wx - 2, by - 26, by - 7, Pal.WOOD_D)
                if (hammerUp(seed)) {
                    val hx = bx + d * (B.hw + 1); val hy = by + B.hTop + 4
                    P.draw(c, ar, bx, by, t, seed, false, flip, false, false, reach = intArrayOf(hx, hy), holding = true)
                    c.vline(hx, hy - 6, hy - 1, Pal.WOOD_M)
                    c.fillRect(hx - 2, hy - 9, 5, 3, Pal.SLATE_M); c.hline(hx - 2, hx + 2, hy - 9, Pal.SLATE_L); c.hline(hx - 2, hx + 2, hy - 7, Pal.SLATE_D)
                } else {
                    val hx = wx - d * 7
                    P.draw(c, ar, bx, by, t, seed, false, flip, false, false, reach = intArrayOf(hx, by - 18), holding = true)
                    c.hline(hx + 2 * d, wx - 2 * d, by - 17, Pal.WOOD_M)
                    c.fillRect(wx - 3 * d - 1, by - 19, 3, 4, Pal.SLATE_M); c.hline(wx - 3 * d - 1, wx - 3 * d + 1, by - 19, Pal.SLATE_L)
                }
            }
            Activity.MUSIC -> {
                // the harmonika held in both hands in front, its bellows going in and out, the notes going up
                val out = bellows(seed)
                val y0 = by + B.armTop + 2
                val near = bx + d * (B.tw + (if (out) 9 else 7)); val far = bx + d * (B.tw - 1)
                P.draw(c, ar, bx, by, t, seed, false, flip, false, false, reach = intArrayOf(near, y0 + 2, far, y0 + 2), holding = true)
                val x0 = minOf(near, far); val x1 = maxOf(near, far)
                c.fillRect(x0, y0, x1 - x0 + 1, 6, Pal.FLAG_RED)
                for (x in x0 + 2 until x1 - 1 step 2) c.vline(x, y0 + 1, y0 + 4, Pal.FLAG_WHITE)
                c.fillRect(x0, y0, 2, 6, Pal.WOOD_D); c.fillRect(x1 - 1, y0, 2, 6, Pal.WOOD_D)
                notes(bx + d * (B.tw + 6), by + B.hTop - 4, seed, 3)
            }
            Activity.CHAT -> {
                // talking in turns: the mouth going and a hand now and then, then listening
                val talk = talking(seed)
                if (lantern) {
                    P.draw(c, ar, bx, by, t, seed, talk, flip, false, false, reach = intArrayOf(bx + d * (B.tw + 2), held), holding = true)
                    lamp3(P.heldX + d, P.heldY + 2, seed)
                } else P.draw(c, ar, bx, by, t, seed, talk, flip, false, false)
                return H
            }
            Activity.WAVE -> {
                val w = waving(seed)
                val y = by - (if (w in 0f..0.3f) 2 else 0) // a little hop as the wave starts
                if (w >= 0f) {
                    // an arm up waving, a smile, and a wink
                    P.draw(c, ar, bx, y, t, seed, false, flip, false, false, pose = Pose.WAVE, wink = w in 0.4f..0.95f)
                    if (lantern) lamp3(P.heldX + d, P.heldY - 6, seed) // held up high
                } else if (lantern) {
                    P.draw(c, ar, bx, y, t, seed, false, flip, false, false, reach = intArrayOf(bx + d * (B.tw + 2), held), holding = true)
                    lamp3(P.heldX + d, P.heldY + 2, seed)
                } else P.draw(c, ar, bx, y, t, seed, false, flip, false, false)
                return H
            }
            else -> {
                // standing, walking, fishing, chopping: as ever; a lantern held out in the near hand when standing or walking
                val walking = a == Activity.WALK
                if (lantern && (walking || a == Activity.STAND)) {
                    P.draw(c, ar, bx, by, t, seed, false, flip, walking, false, reach = intArrayOf(bx + d * (B.tw + 2), held - (if (walking) step else 0)), holding = true)
                    lamp3(P.heldX + d, P.heldY + 2, seed)
                } else P.draw(c, ar, bx, by, t, seed, false, flip, walking, false)
            }
        }
        if (lantern && a != Activity.WALK && a != Activity.STAND) lamp3(bx - d * (B.tw + 6), by0 - 6, seed)
        return H
    }

    /**
     * A lantern at the finest detail, 5 px wide, [x] its middle and [y] its ring's row (in a hand just above, or its top
     * set down): the dark cap and foot, the frame's posts, the warm glass between with the flame in the middle, flickering
     * (a pixel's dance); its light.
     */
    private fun lamp3(x: Int, y: Int, seed: Int) {
        val f = flicker(seed)
        c.set(x, y, LAMP)
        c.hline(x - 1, x + 1, y + 1, LAMP); c.hline(x - 2, x + 2, y + 2, LAMP_L)
        c.hline(x - 2, x + 2, y + 6, LAMP)
        c.vline(x - 2, y + 3, y + 5, LAMP); c.vline(x + 2, y + 3, y + 5, LAMP)
        glow {
            for (r in 3..5) { c.set(x - 1, y + r, if (r == 3) Pal.WINDOW_LIT_HI else Pal.WINDOW_LIT); c.set(x + 1, y + r, Pal.WINDOW_LIT) }
            val fx = x + (if (f < 0.2f) -1 else if (f > 0.85f) 1 else 0)
            c.set(x, y + 3, Pal.WINDOW_LIT); c.set(x, y + 4, Pal.FLAME[1]); c.set(x, y + 5, Pal.FLAME[2])
            c.set(fx, y + 4, Pal.FLAME[0]); if (f > 0.45f) c.set(x, y + 3, Pal.FLAME[0])
        }
        s.light(x + 0.5f, y + 4.5f, LAMP_R, LAMP_I * (0.85f + 0.15f * f))
    }

    /** The anvil on its stump at the finest detail, the stump's foot at ([ax], [by]): the iron glowing on its face, the horn toward [d]. */
    private fun anvil3(ax: Int, by: Int, d: Int) {
        c.fillRect(ax - 4, by - 6, 8, 6, Pal.LOG_M)
        c.hline(ax - 4, ax + 3, by - 6, Pal.LOG_L); c.set(ax - 1, by - 6, Pal.WOOD_L)
        c.vline(ax + 3, by - 5, by - 1, Pal.LOG_D); c.vline(ax - 2, by - 4, by - 2, Pal.LOG_D); c.vline(ax + 1, by - 3, by - 1, Pal.LOG_D)
        c.fillRect(ax - 2, by - 8, 5, 2, Pal.SLATE_D)
        c.fillRect(ax - 4, by - 10, 9, 2, Pal.SLATE_M); c.hline(ax - 4, ax + 4, by - 10, Pal.SLATE_L)
        c.set(ax + 5 * d, by - 10, Pal.SLATE_M); c.set(ax + 5 * d, by - 9, Pal.SLATE_D); c.set(ax + 6 * d, by - 10, Pal.SLATE_D)
        glow { c.hline(ax - 2, ax + 1, by - 11, Pal.FLAME[2]); c.set(ax - 1, by - 11, Pal.FLAME[1]); c.set(ax, by - 11, Pal.FLAME[1]) }
    }

    /**
     * A wooden pail at the finest detail hanging from a hand ([x] its middle, [y] its bail's top): the iron bail, the rim,
     * the water glinting, the staves under an iron hoop.
     */
    private fun pail3(x: Int, y: Int, d: Int) {
        c.line(x - 3, y + 2, x - 1, y, Pal.SLATE_D); c.line(x + 3, y + 2, x + 1, y, Pal.SLATE_D)
        c.hline(x - 3, x + 3, y + 2, Pal.WOOD_L)
        c.hline(x - 2, x + 2, y + 3, Pal.WATER_M); c.set(x - 3, y + 3, Pal.WOOD_L); c.set(x + 3, y + 3, Pal.WOOD_M)
        c.set(x - 1 + (if (((t * 3).toInt() and 1) == 0) 0 else 2), y + 3, Pal.WATER_L)
        for (yy in y + 4..y + 6) for (xx in x - 3..x + 3) c.set(xx, yy, if ((xx - x) % 2 == 0) Pal.WOOD_M else Pal.WOOD_L)
        c.hline(x - 3, x + 3, y + 4, Pal.SLATE_D); c.vline(x + 3 * d, y + 4, y + 6, Pal.WOOD_D)
        c.hline(x - 2, x + 2, y + 7, Pal.SLATE_D)
    }

    /**
     * A basket at the finest detail on the forearm or the ground ([x] its middle, [y] its rim's row): woven, the handle over
     * it; apples, a loaf and greens in it, or [fill] heaped in it (the washing, berries, mushrooms).
     */
    private fun basket3(x: Int, y: Int, d: Int, fill: Int? = null) {
        c.line(x - 3, y, x - d, y - 4, Pal.WOOD_D); c.line(x + 3, y, x + d, y - 4, Pal.WOOD_D)
        if (fill != null) {
            c.hline(x - 2, x + 2, y - 1, fill); c.set(x - 1, y - 2, fill); c.set(x + 1, y - 2, fill)
            if (fill == MUSHROOM) { c.set(x - 1, y - 1, MUSHROOM_STEM); c.set(x + 2, y - 1, MUSHROOM_STEM) }
        } else {
            c.fillRect(x - 2, y - 2, 2, 2, Pal.GERANIUM); c.set(x - 2, y - 2, Col.hex(0xF07060))
            c.fillRect(x, y - 1, 3, 1, GOURD); c.set(x + 1, y - 2, GOURD); c.set(x + 3 * d, y - 2, Pal.LEAF)
        }
        for (yy in y + 1..y + 4) for (xx in x - 3 + (if (yy == y + 4) 1 else 0)..x + 3 - (if (yy == y + 4) 1 else 0)) c.set(xx, yy, if ((xx + yy) % 2 == 0) Pal.WOOD_L else Pal.WOOD_M)
        c.hline(x - 3, x + 3, y, Pal.WOOD_D)
    }

    /**
     * The washing line beside someone at the nominal detail (their feet at [bx], [by], the line going on toward [d]): a post
     * behind them and one at its far end, the line above their head, the washing hanging from it (a sheet, a shirt, a towel);
     * with [rug], a rug over it in front of them instead, its fringe hanging.
     */
    private fun line1(bx: Int, by: Int, d: Int, rug: Boolean) {
        val x0 = bx - d; val x1 = bx + 9 * d; val ly = by - 8
        c.vline(x0, ly, by - 1, Pal.WOOD_D); c.vline(x1, ly, by - 1, Pal.WOOD_D)
        c.line(x0, ly, x1, ly, LINE)
        if (rug) {
            for (x in 3..7) for (y in 1..5) c.set(bx + x * d, ly + y, if (y == 3) RUG_L else if (x == 3 || x == 7) RUG_D else RUG)
            c.set(bx + 4 * d, ly + 6, RUG_L); c.set(bx + 6 * d, ly + 6, RUG_L)
        } else {
            for (x in 3..4) for (y in 1..3) c.set(bx + x * d, ly + y, CLOTH)
            for (x in 6..7) for (y in 1..2) c.set(bx + x * d, ly + y, Pal.FLAG_BLUE)
            c.set(bx + 8 * d, ly + 1, CLOTH); c.set(bx + 8 * d, ly + 2, CLOTH_D)
        }
    }

    /** [line1] at the middle detail: taller posts, the line just over their head, the washing or the rug bigger. */
    private fun line2(bx: Int, by: Int, d: Int, rug: Boolean) {
        val x0 = bx - 2 * d; val x1 = bx + 19 * d; val ly = by - 15
        c.vline(x0, ly - 1, by - 1, Pal.WOOD_D); c.vline(x1, ly - 1, by - 1, Pal.WOOD_D)
        c.line(x0, ly, x1, ly, LINE)
        if (rug) {
            for (x in 6..17) for (y in 1..10) c.set(bx + x * d, ly + y, when {
                y == 1 -> RUG_D
                y == 5 || y == 6 -> if ((x + y) % 2 == 0) RUG_L else RUG
                x == 6 || x == 17 -> RUG_D
                else -> RUG
            })
            for (x in 6..17 step 2) c.set(bx + x * d, ly + 11, RUG_L)
        } else {
            for (x in 6..10) for (y in 1..6) c.set(bx + x * d, ly + y, if (x == 10 || y == 6) CLOTH_D else CLOTH)
            for (x in 12..14) for (y in 1..4) c.set(bx + x * d, ly + y, if (y == 4) Col.scale(Pal.FLAG_BLUE, 0.8f) else Pal.FLAG_BLUE)
            for (x in 15..18) for (y in 1..7) c.set(bx + x * d, ly + y, if (y == 7 || x == 18) CLOTH_D else if (y == 2) Pal.GERANIUM else CLOTH)
        }
    }

    /** [line1] at the finest detail, the line at [ly]: posts with a crossbar, pegs on the line, the washing or the rug. */
    private fun line3(bx: Int, by: Int, d: Int, ly: Int, rug: Boolean) {
        val x0 = bx - 6 * d; val x1 = bx + 32 * d
        for (x in intArrayOf(x0, x1)) { c.vline(x, ly - 2, by - 1, Pal.WOOD_D); c.vline(x + d, ly - 2, by - 1, Pal.WOOD_M); c.hline(x - 2, x + 2, ly - 2, Pal.WOOD_D) }
        c.line(x0, ly, x1, ly, LINE)
        if (rug) {
            for (x in 10..30) for (y in 1..22) c.set(bx + x * d, ly + y, when {
                y <= 2 -> RUG_D
                x == 10 || x == 30 || y == 22 -> RUG_D
                (y in 9..14) && (x + y) % 4 < 2 -> RUG_L
                (y == 6 || y == 17) -> RUG_L
                else -> RUG
            })
            for (x in 10..30 step 2) { c.set(bx + x * d, ly + 23, RUG_L); c.set(bx + x * d, ly + 24, RUG_L) }
        } else {
            fun cloth(from: Int, to: Int, deep: Int, col: Int, shade: Int) {
                for (x in from..to) for (y in 1..deep) c.set(bx + x * d, ly + y, if (x == to || y == deep) shade else col)
                c.set(bx + from * d, ly, Pal.WOOD_L); c.set(bx + to * d, ly, Pal.WOOD_L) // the pegs
            }
            cloth(10, 17, 16, CLOTH, CLOTH_D)
            cloth(20, 24, 10, Pal.FLAG_BLUE, Col.scale(Pal.FLAG_BLUE, 0.75f))
            cloth(26, 30, 14, CLOTH, CLOTH_D)
            for (x in 26..30) c.set(bx + x * d, ly + 4, Pal.GERANIUM)
        }
    }

    /** A carpet beater's head at the finest detail ([x], [y] its middle): a trefoil of bent cane. */
    private fun beater3(x: Int, y: Int) {
        c.hline(x - 2, x + 2, y - 2, Pal.WOOD_L); c.hline(x - 2, x + 2, y + 2, Pal.WOOD_L)
        c.vline(x - 2, y - 2, y + 2, Pal.WOOD_L); c.vline(x + 2, y - 2, y + 2, Pal.WOOD_L); c.set(x, y, Pal.WOOD_M)
    }

    /** A basket set down at the middle detail, its foot on [by] at [x]: woven, [fill] heaped in it (picked berries, the washing). */
    private fun basketOnGround2(x: Int, by: Int, d: Int, fill: Int = picked()) {
        c.hline(x - 2, x + 2, by - 1, Pal.WOOD_D); c.hline(x - 2, x + 2, by - 2, Pal.WOOD_M); c.set(x - 1, by - 2, Pal.WOOD_L); c.set(x + 1, by - 2, Pal.WOOD_L)
        c.hline(x - 2, x + 2, by - 3, Pal.WOOD_D)
        c.hline(x - 1, x + 1, by - 4, fill); c.set(x + d, by - 5, fill)
    }

    /** A hen at the nominal detail, its feet at ([x], [y]) facing [d]: a white or brown body, the comb; its head down pecking. */
    private fun hen1(x: Int, y: Int, d: Int, peck: Boolean, white: Boolean) {
        val body = if (white) CLOTH else HEN_BROWN
        c.set(x, y - 1, body); c.set(x - d, y - 1, body); c.set(x - d, y - 2, Col.scale(body, 0.8f))
        if (peck) { c.set(x + d, y - 1, body); c.set(x + 2 * d, y, Pal.GOLD) } else { c.set(x + d, y - 2, body); c.set(x + d, y - 3, Pal.FLAG_RED) }
    }

    /** A hen at the middle detail: [hen1] bigger, its tail up, its legs, the comb and the beak. */
    private fun hen2(x: Int, y: Int, d: Int, peck: Boolean, white: Boolean) {
        val body = if (white) CLOTH else HEN_BROWN
        val shade = Col.scale(body, 0.8f)
        c.hline(x - 2 * d, x + d, y - 2, body); c.hline(x - d, x + d, y - 3, body); c.set(x - 2 * d, y - 3, shade); c.set(x - 3 * d, y - 4, shade)
        c.set(x, y - 1, Pal.GOLD); c.set(x - d, y - 1, Pal.GOLD)
        if (peck) { c.set(x + 2 * d, y - 2, body); c.set(x + 3 * d, y - 1, Pal.GOLD); c.set(x + 2 * d, y - 3, Pal.FLAG_RED) }
        else { c.set(x + 2 * d, y - 4, body); c.set(x + 2 * d, y - 5, Pal.FLAG_RED); c.set(x + 3 * d, y - 4, Pal.GOLD); c.set(x + d, y - 4, body) }
    }

    /** A hen at the finest detail: a round body with a wing, its tail feathers up, yellow legs, the red comb and wattle. */
    private fun hen3(x: Int, y: Int, d: Int, peck: Boolean, white: Boolean) {
        val body = if (white) CLOTH else HEN_BROWN
        val shade = Col.scale(body, 0.78f)
        for (r in 0..3) c.hline(x - 3 * d + (if (r == 0) d else 0), x + 2 * d, y - 3 - r, body)
        c.hline(x - 2 * d, x + d, y - 2, shade) // the belly
        c.hline(x - d, x + d, y - 5, shade); c.set(x, y - 4, shade) // the wing
        c.set(x - 4 * d, y - 6, shade); c.set(x - 4 * d, y - 7, body); c.set(x - 3 * d, y - 7, shade) // the tail
        c.vline(x - d, y - 1, y - 1, Pal.GOLD); c.vline(x + d, y - 1, y - 1, Pal.GOLD)
        if (peck) {
            c.fillRect(if (d > 0) x + 3 else x - 4, y - 3, 2, 2, body); c.set(x + 5 * d, y - 1, Pal.GOLD); c.set(x + 3 * d, y - 4, Pal.FLAG_RED)
        } else {
            c.fillRect(if (d > 0) x + 2 else x - 3, y - 8, 2, 3, body); c.set(x + 2 * d, y - 9, Pal.FLAG_RED); c.set(x + 3 * d, y - 9, Pal.FLAG_RED)
            c.set(x + 4 * d, y - 7, Pal.GOLD); c.set(x + 3 * d, y - 6, Pal.FLAG_RED); c.set(x + 3 * d, y - 7, Col.hex(0x1E1618))
        }
    }

    /**
     * Hens bunched up by a house while wolves or a bear are about, their feet round ([bx], [by]): [n] of them close
     * together, heads up, now one, now another turning, at the lens's detail.
     */
    fun huddle(bx: Int, by: Int, n: Int, seed: Int) {
        val step = when (k) { 1 -> 2; 2 -> 3; else -> 5 }
        for (j in 0 until n) {
            val x = bx + ((j - (n - 1) / 2f) * step).toInt()
            val y = by + (j % 2) * (step / 2)
            val d = if (cyc(seed + j * 211, 2.7, j * 0.3) < 0.5f) 1 else -1
            val white = (seed ushr (9 + j)) % 2 == 0
            when (k) {
                1 -> hen1(x, y, d, false, white)
                2 -> hen2(x, y, d, false, white)
                else -> hen3(x, y, d, false, white)
            }
        }
    }

    /**
     * The children's ball ([Placed.ball]) at ([bx], [by]) on the ground, [z] canvas px up in the air: its shadow on the
     * ground, the ball red with a white band, bigger at a closer look.
     */
    fun ball(bx: Int, by: Int, z: Int) {
        val r = when (k) { 1 -> 1; 2 -> 1; else -> 2 }
        c.hline(bx - r, bx + r, by, Col.hex(0x2E2A24))
        val y = by - z - r - 1
        if (k == 1) { c.set(bx, y, Pal.FLAG_RED); c.set(bx + 1, y, Pal.FLAG_WHITE); c.set(bx, y + 1, Col.scale(Pal.FLAG_RED, 0.75f)); c.set(bx + 1, y + 1, Pal.FLAG_RED); return }
        for (dy in -r..r) for (dx in -r..r) if (dx * dx + dy * dy <= r * r + 1) c.set(bx + dx, y + dy, if (dy == 0) Pal.FLAG_WHITE else if (dy > 0) Col.scale(Pal.FLAG_RED, 0.75f) else Pal.FLAG_RED)
    }

    /** The washing on the stones at the finest detail ([x] its near edge toward [d], its bottom on [by]): white, wet and grey underneath. */
    private fun cloth3(x: Int, by: Int, d: Int) {
        for (i in 0..6) {
            val xx = x + i * d
            c.set(xx, by - 3, if (i in 1..5) CLOTH else CLOTH_D); c.set(xx, by - 2, CLOTH); c.set(xx, by - 1, CLOTH_D)
        }
        c.set(x + 3 * d, by - 2, CLOTH_D) // a fold
    }

    /** Sparks off the anvil: [p] 0 until 1 through the strike, [n] of them out and up from ([x], [y]) as far as [r] px, a new spray each [round]. */
    private fun sparksAt(x: Int, y: Int, p: Float, n: Int, r: Float, seed: Int, round: Int) {
        glow {
            for (j in 0 until n) {
                val a = PI * (0.12 + 0.76 * Noise.rnd(j, seed, round))
                val dist = r * (0.25f + 0.75f * p) * (0.55f + 0.45f * Noise.rnd(j, seed, round + 7))
                val sx = x + (cos(a) * dist).toInt(); val sy = y - (sin(a) * dist).toInt() + (p * p * r * 0.4f).toInt()
                c.set(sx, sy, if (p < 0.4f) Pal.FLAME[0] else if (p < 0.75f) Pal.FLAME[1] else Pal.FLAME[2])
            }
        }
    }

    /**
     * A sheaf of hay lying from ([x0], [y0]) (the cut stalks, in front) to ([x1], [y1]) (the ears, behind), [th] px thick:
     * the stalks' ragged cut ends, a twist of straw binding it near the front, lit along its top and shaded under, the ears
     * fanning out behind, heavy with grain.
     */
    private fun sheaf(x0: Int, y0: Int, x1: Int, y1: Int, th: Int) {
        val n = max(abs(x1 - x0), abs(y1 - y0)).coerceAtLeast(1)
        for (i in 0..n) {
            val u = i.toFloat() / n
            val x = x0 + ((x1 - x0) * u).roundToInt(); val yc = y0 + ((y1 - y0) * u).roundToInt()
            val half = th / 2 + (if (u > 0.68f) 1 else 0) + (if (u > 0.86f && th > 3) 1 else 0)
            for (j in -half..half) {
                if (i == 0 && (j and 1) != 0) continue
                val col = when {
                    i <= 1 -> Pal.HAY_D
                    u in 0.07f..0.12f -> Pal.WOOD_D
                    u > 0.68f -> if ((i + j) % 2 == 0) Pal.GOLD else Pal.HAY_L
                    j == -half -> Pal.HAY_L
                    j == half -> Pal.HAY_D
                    (i + j * 3) % 5 == 0 -> Pal.HAY_L
                    else -> Pal.HAY_M
                }
                c.set(x, yc + j, col)
            }
        }
    }

    /** A log held lengthwise, [len] px from [x0] toward [d], [th] rows from [y]: bark lit on top and dark under, its cut end pale at the front. */
    private fun log(x0: Int, y: Int, len: Int, th: Int, d: Int) {
        for (i in 0 until len) {
            val x = x0 + i * d
            for (j in 0 until th) c.set(x, y + j, if (j == 0) Pal.LOG_L else if (j == th - 1) Pal.LOG_D else Pal.LOG_M)
        }
        val ex = x0 + (len - 1) * d
        for (j in 0 until th) c.set(ex, y + j, Pal.WOOD_L)
        if (th >= 3) c.set(ex, y + th / 2, Pal.WOOD_M)
    }

    /**
     * The nominal 7 px figure, small and round like the scene's people: a 3 × 3 head (hair over it, a kerchief
     * or a hat), a short body, two legs and dark shoes; a step when walking, what they carry. Without [hand] the near
     * hand is left to the caller (at work, waving); without [held] what it carries is left out (a lantern in it).
     */
    private fun tiny(bx: Int, by: Int, L: Look, walking: Boolean, flip: Boolean, seed: Int, hand: Boolean = true, held: Boolean = hand) {
        val frame = if (walking) (((t * 6).toInt() + seed) and 1) else 0
        val d = if (flip) -1 else 1
        val shoes = Col.hex(0x2E2018)
        val top = by - (if (L.small) 6 else 7)
        if (L.pack) {
            // the krošnja behind: two posts beside the body, a rail over the hat, the load on it (a sieve, a spoon)
            c.vline(bx - 2, top - 1, by - 3, Pal.WOOD_M); c.vline(bx + 2, top - 1, by - 3, Pal.WOOD_D)
            c.hline(bx - 2, bx + 2, top - 2, Pal.WOOD_L)
            c.set(bx - 1, top - 3, Pal.CANVAS_M); c.set(bx - 2, top - 3, Pal.WOOD_M); c.set(bx, top - 3, Pal.WOOD_M)
            c.set(bx - 1, top - 4, Pal.WOOD_L); c.set(bx + 1, top - 3, Pal.WOOD_L); c.set(bx + 2, top - 4, Pal.WOOD_L)
        }
        tinyHead(bx, top, L, flip)
        var y = top + 3
        if (!L.small) { c.hline(bx - 1, bx + 1, y, L.shirt); y++ }
        c.hline(bx - 1, bx + 1, y, if (L.apron != 0) L.apron else L.shirt)
        if (L.apron != 0) { c.set(bx - 1, y, L.shirt); c.set(bx + 1, y, L.shirt) }
        if (hand) c.set(bx + 2 * d, y - frame, L.skin) // the near hand, swinging
        val legs = if (L.skirt) L.shirt else L.pants
        if (L.skirt) c.hline(bx - 1, bx + 1, by - 2, legs)
        else if (frame == 0) { c.set(bx - 1, by - 2, legs); c.set(bx + 1, by - 2, legs) } else c.set(bx, by - 2, legs)
        if (frame == 0) { c.set(bx - 1, by - 1, shoes); c.set(bx + 1, by - 1, shoes) } else { c.set(bx, by - 1, shoes); c.set(bx + d, by - 1, shoes) }
        when (L.prop) {
            // (the krošnja's post stands where a stick would)
            PROP_STICK, PROP_CROOK -> if (!L.pack && held) { c.vline(bx + 2 * d, y - 2, by - 1, Pal.WOOD_M); if (L.prop == PROP_CROOK) c.set(bx + d, y - 3, Pal.WOOD_L) }
            PROP_HAMMER -> if (held) { c.set(bx + 2 * d, y, Pal.WOOD_M); c.set(bx + 3 * d, y - 1, Pal.SLATE_M) }
            PROP_BASKET -> { c.set(bx - 2 * d, by - 2, Pal.WOOD_M); c.set(bx - 2 * d, by - 3, Pal.GERANIUM) }
            // a staff taller than him, a gourd at its top
            PROP_STAFF -> if (held) { c.vline(bx + 2 * d, top - 2, by - 1, Pal.WOOD_M); c.set(bx + 3 * d, top, GOURD) }
            else -> Unit
        }
        if (L.bag != 0) c.set(bx - 2 * d, y, L.bag) // the bag at the far hip
    }

    /** The 3 × 3 head of the small figures: hair (or a kerchief) over it and down the sides, the face below, a hat on top. */
    private fun tinyHead(bx: Int, top: Int, L: Look, flip: Boolean) {
        val hair = if (L.kerchief) Pal.FLAG_RED else L.hair
        c.hline(bx - 1, bx + 1, top, hair)
        c.set(bx - 1, top + 1, hair); c.set(bx, top + 1, L.skin); c.set(bx + 1, top + 1, hair)
        c.hline(bx - 1, bx + 1, top + 2, L.skin)
        if (L.kerchief) c.set(if (flip) bx + 1 else bx - 1, top, Pal.FLAG_WHITE)
        if (L.hat != 0) {
            val hat = if (L.straw) Pal.HAY_L else L.hat
            c.hline(bx - 2, bx + 2, top, hat); c.set(bx, top - 1, if (L.straw) Pal.HAY_M else hat)
        }
        if (L.beard != 0) c.set(bx, top + 2, L.beard)
    }

    private fun seatedTiny(bx: Int, by: Int, L: Look, flip: Boolean) {
        val d = if (flip) -1 else 1
        tinyHead(bx, by - 5, L, flip)
        c.hline(bx - 1, bx + 1, by - 2, L.shirt)
        val legs = if (L.skirt) L.shirt else L.pants
        c.hline(bx - 1, bx + 1, by - 1, legs)
        c.set(bx + 2 * d, by - 1, Col.hex(0x2E2018))
    }

    /**
     * The 14 px figure of the middle detail, small and round like the scene's people: a big head with hair over
     * it (or a kerchief, a hat), dot eyes and a mouth or a beard; a short body with its arms, an apron, a skirt or
     * trousers, dark shoes; what they carry. Walking swings the legs. Drawn in canvas pixels (no zoom), with its
     * feet at ([bx], [by]). At work: [reach] takes the near hand to canvas point (reach[0], reach[1]) (and with four
     * values the far one to (reach[2], reach[3])), the arm going there from the shoulder; without [held] the near hand
     * carries nothing of theirs (a tool, a lantern instead); [lean] bends the head a pixel forward and down over the work
     * (crouching, [sitting] low); [open] opens the mouth (talking).
     */
    private fun figure(
        bx: Int, by: Int, L: Look, walking: Boolean, flip: Boolean, sitting: Boolean, seed: Int,
        reach: IntArray? = null, held: Boolean = true, lean: Int = 0, open: Boolean = false,
    ) {
        val d = if (flip) -1 else 1
        val step = if (walking) (((t * 6).toInt() + seed) and 1) else -1
        val shoes = Col.hex(0x2E2018)
        val eye = Col.hex(0x1E1618)
        val small = L.small
        val legH = if (small) 2 else 3
        val torsoH = if (small) 3 else 4
        // legs (or a skirt, or the lap), the body's base
        val base: Int
        if (sitting) {
            base = by - 2
            c.fillRect(bx - 2, by - 2, 5, 1, if (L.skirt) L.shirt else L.pants)
            c.hline(bx - 2, bx - 1, by - 1, shoes); c.hline(bx + 1, bx + 2, by - 1, shoes)
        } else if (L.skirt) {
            for (r in 0 until legH) c.hline(bx - 2 - r / 2, bx + 2 + r / 2, by - legH - 1 + r, if (r == legH - 1) Col.scale(L.shirt, 0.75f) else L.shirt)
            val lift = if (step >= 0) 1 else 0
            c.hline(bx - 2, bx - 1, by - 1 + (if (step == 0) 0 else 0), shoes); c.hline(bx + 1, bx + 2, by - 1, shoes)
            if (lift == 1 && step == 0) c.set(bx - 2, by - 1, L.skin)
            base = by - legH - 1
        } else {
            for (leg in 0..1) {
                val lift = if (step >= 0 && step == leg) 1 else 0
                val shift = if (step >= 0) (if (step == leg) d else -d) else 0
                val x0 = if (leg == 0) bx - 2 else bx + 1
                c.fillRect(x0 + shift, by - legH - lift, 2, legH - 1, L.pants)
                c.fillRect(x0 + shift, by - 1 - lift, 2, 1, shoes)
            }
            c.set(bx, by - legH, Col.scale(L.pants, 0.75f))
            base = by - legH
        }
        // the body, its arms, the apron (the krošnja first, behind him)
        val tTop = base - torsoH
        if (L.pack) figurePack(bx, tTop - 5, base, d)
        c.fillRect(bx - 2, tTop, 5, torsoH, L.shirt)
        c.vline(bx + 2, tTop + 1, base - 1, Col.scale(L.shirt, 0.8f))
        if (L.apron != 0) { c.fillRect(bx - 1, tTop + 1, 3, torsoH - 1 + (if (sitting || L.skirt) 0 else 1), L.apron); c.vline(bx + 1, tTop + 1, base - 1, Col.scale(L.apron, 0.85f)) }
        val swing = if (step >= 0) (if (step == 0) 1 else -1) else 0
        // the arms hanging at the sides (one reaching comes after the head, over it)
        val far = reach?.takeIf { it.size >= 4 }
        if (!(if (d < 0) reach != null else far != null)) { c.vline(bx - 3, tTop + 1 + swing, base - 2 + swing, L.shirt); c.set(bx - 3, base - 1 + swing, L.skin) }
        if (!(if (d > 0) reach != null else far != null)) { c.vline(bx + 3, tTop + 1 - swing, base - 2 - swing, L.shirt); c.set(bx + 3, base - 1 - swing, L.skin) }
        // a shoulder bag's strap across the chest, the bag at the far hip
        if (L.bag != 0) {
            c.line(bx + 2 * d, tTop, bx - 2 * d, base - 1, Col.scale(L.bag, 0.7f))
            c.fillRect(if (d > 0) bx - 5 else bx + 4, base - 2, 2, 2, L.bag)
        }
        // the head: 7 × 6, sitting on the shoulders (bent over the work: a pixel forward and down)
        val hb = bx + lean * d
        val hTop = tTop - 5 + lean
        for (r in 0..5) { val inset = if (r == 0 || r == 5) 1 else 0; c.hline(hb - 3 + inset, hb + 3 - inset, hTop + r, L.skin) }
        c.vline(hb + 3, hTop + 2, hTop + 4, Col.scale(L.skin, 0.88f))
        when {
            L.kerchief -> {
                c.hline(hb - 2, hb + 2, hTop - 1, Pal.FLAG_RED); c.hline(hb - 3, hb + 3, hTop, Pal.FLAG_RED); c.hline(hb - 3, hb + 3, hTop + 1, Pal.FLAG_RED)
                c.vline(hb - 3, hTop + 2, hTop + 4, Pal.FLAG_RED); c.vline(hb + 3, hTop + 2, hTop + 4, Pal.FLAG_RED)
                c.set(hb + d, hTop + 6, Pal.FLAG_RED)
                if ((seed ushr 2) % 2 == 0) { c.set(hb - 1, hTop, Pal.FLAG_WHITE); c.set(hb + 1, hTop + 1, Pal.FLAG_WHITE) }
            }
            else -> {
                // hair over the head, a fringe, down the sides (longer for the women)
                c.hline(hb - 2, hb + 2, hTop - 1, L.hair); c.hline(hb - 3, hb + 3, hTop, L.hair); c.hline(hb - 3, hb + 3, hTop + 1, L.hair)
                c.set(hb - 1 + d, hTop + 2, L.hair)
                c.vline(hb - 3, hTop + 2, hTop + (if (L.skirt) 5 else 3), L.hair); c.vline(hb + 3, hTop + 2, hTop + (if (L.skirt) 5 else 3), Col.scale(L.hair, 0.8f))
                c.set(hb - 2, hTop, Col.mix(L.hair, Col.hex(0xFFFFFF), 0.3f))
            }
        }
        if (L.hat != 0) {
            val hat = if (L.straw) Pal.HAY_L else L.hat
            val crown = if (L.straw) Pal.HAY_M else hat
            c.hline(hb - 4, hb + 4, hTop + 1, hat)
            c.fillRect(hb - 2, hTop - 2, 5, 3, crown)
            c.hline(hb - 2, hb + 2, hTop, if (L.straw) Pal.FLAG_RED else Col.scale(hat, 0.7f))
            if (L.shell) { c.set(hb, hTop - 1, SHELL); c.set(hb - 1, hTop - 2, SHELL); c.set(hb + 1, hTop - 2, SHELL) } // the pilgrim's scallop
        }
        c.set(hb - 1, hTop + 3, eye); c.set(hb + 1, hTop + 3, eye)
        if (L.beard != 0) { c.hline(hb - 2, hb + 2, hTop + 4, L.beard); c.hline(hb - 1, hb + 1, hTop + 5, L.beard); c.set(hb, hTop + 6, L.beard) }
        else c.set(hb, hTop + 4, if (open) MOUTH_OPEN else Col.hex(0xB8484A))
        // an arm reaching (a tool's handle, a wave, a lantern held out), the hand at its end
        if (far != null) { c.line(bx - 3 * d, tTop + 1, far[2], far[3], Col.scale(L.shirt, 0.8f)); c.set(far[2], far[3], L.skin) }
        if (reach != null) { c.line(bx + 3 * d, tTop + 1, reach[0], reach[1], L.shirt); c.set(reach[0], reach[1], L.skin) }
        // what they carry, in the near hand (the basket on the far arm stays)
        val hx = reach?.get(0) ?: (bx + 3 * d); val hy = reach?.get(1) ?: (base - 1)
        if (held || L.prop == PROP_BASKET) when (L.prop) {
            PROP_STICK -> c.vline(hx + d + (if (L.pack) d else 0), hy - 2, by - 1, Pal.WOOD_D) // out past the krošnja's post
            PROP_STAFF -> {
                c.vline(hx + d, hTop - 3, by - 1, Pal.WOOD_M); c.set(hx + d, hTop - 4, Pal.WOOD_D)
                c.set(hx + 2 * d, hTop - 3, Col.hex(0x3A2A20)); c.fillRect(if (d > 0) hx + 2 else hx - 3, hTop - 2, 2, 2, GOURD)
            }
            PROP_CROOK -> { c.vline(hx + d, hTop - 2, by - 1, Pal.WOOD_M); c.set(hx, hTop - 3, Pal.WOOD_L); c.set(hx - d, hTop - 3, Pal.WOOD_L) }
            PROP_HAMMER -> { c.hline(hx, hx + 3 * d, hy, Pal.WOOD_M); c.fillRect(hx + 3 * d - 1, hy - 2, 2, 4, Pal.SLATE_M); c.set(hx + 3 * d - 1, hy - 2, Pal.SLATE_L) }
            PROP_BASKET -> { val by1 = base - 1; c.fillRect(bx - 3 * d - 2, by1 - 1, 3, 2, Pal.WOOD_M); c.set(bx - 3 * d - 1, by1 - 2, Pal.GERANIUM); c.set(bx - 3 * d, by1 - 2, Pal.GOLD) }
            PROP_BOOK -> { c.fillRect(hx, hy - 3, 3, 2, Pal.FLAG_BLUE); c.set(hx + 1, hy - 3, Pal.FLAG_WHITE) }
            PROP_SPOON -> { c.line(hx, hy, hx + 2 * d, hy - 3, Pal.WOOD_M); c.set(hx + 2 * d, hy - 4, Pal.WOOD_L) }
            PROP_MUG -> { c.fillRect(hx, hy - 3, 2, 3, Pal.GOLD); c.set(hx, hy - 3, Pal.FLAG_WHITE) }
            PROP_BOTTLE -> { c.vline(hx + d, hy - 4, hy - 3, Col.hex(0x2E5A3A)); c.fillRect(hx, hy - 2, 2, 3, Col.hex(0x2E5A3A)) }
            PROP_SMOKER -> { c.fillRect(hx, hy - 3, 2, 3, Pal.STONE_M); c.set(hx + d, hy - 4 - ((t * 3).toInt() and 1), Pal.SMOKE) }
            PROP_FLOWER -> { c.vline(hx + d, hy - 3, hy - 1, Pal.LEAF); c.set(hx + d, hy - 4, Pal.GERANIUM) }
            else -> Unit
        }
    }

    /**
     * The 14 px figure's krošnja, behind him: posts beside the body up past his hat, a rail over it, and the load on the
     * rail: an upright sieve on the far side, two wooden spoons sticking out on the near side.
     */
    private fun figurePack(bx: Int, hTop: Int, base: Int, d: Int) {
        c.vline(bx - 4, hTop - 3, base - 1, Pal.WOOD_M); c.vline(bx + 4, hTop - 3, base - 1, Pal.WOOD_D)
        c.hline(bx - 4, bx + 4, hTop - 3, Pal.WOOD_L)
        val sx = bx - d
        c.hline(sx - 1, sx + 1, hTop - 8, Pal.WOOD_L); c.hline(sx - 1, sx + 1, hTop - 4, Pal.WOOD_M)
        for (y in hTop - 7..hTop - 5) {
            c.set(sx - 2, y, Pal.WOOD_M); c.set(sx + 2, y, Pal.WOOD_D)
            for (x in sx - 1..sx + 1) c.set(x, y, if ((x + y) and 1 == 0) Pal.CANVAS_M else Pal.CANVAS_D)
        }
        c.line(bx + 2 * d, hTop - 3, bx + 4 * d, hTop - 7, Pal.WOOD_L); c.set(bx + 4 * d, hTop - 8, Pal.WOOD_L); c.set(bx + 5 * d, hTop - 7, Pal.WOOD_L)
        c.line(bx + 2 * d, hTop - 3, bx + 5 * d, hTop - 5, Pal.WOOD_M); c.set(bx + 6 * d, hTop - 5, Pal.WOOD_L); c.set(bx + 6 * d, hTop - 6, Pal.WOOD_L)
    }

    /** A fishing rod held out over the water (up and to the left), a line down to a bobbing float. */
    private fun rod(bx: Int, by: Int, h: Int) {
        val hx = bx + k; val hy = by - h * 55 / 100
        val tx = bx - 9 * k; val ty = hy - 9 * k
        c.line(hx, hy, tx, ty, Pal.WOOD_M)
        val bob = if (sin(t * 2.2) > 0.5) k else 0
        c.line(tx, ty, tx - k, ty + 6 * k + bob, Col.hex(0x3A3A44))
        c.block(tx - 2 * k + 1, ty + 6 * k + bob, k, Pal.FLAG_RED)
    }

    /** An axe swinging at the chopping block: up, then down with chips flying. */
    private fun axe(bx: Int, by: Int, flip: Boolean, h: Int) {
        val d = if (flip) -1 else 1
        val up = ((t * 1.6) % 1.0) < 0.5
        val hx = bx + 3 * d * k; val hy = by - h * 6 / 10
        if (up) {
            val ex = hx + 2 * d * k; val ey = by - h - 3 * k
            c.line(hx, hy, ex, ey, Pal.WOOD_M)
            c.block(ex - k, ey - k, k, Pal.STONE_M); c.block(ex - k + d * k, ey - k, k, Pal.STONE_L)
        } else {
            val ex = hx + 5 * d * k; val ey = by - k
            c.line(hx, hy, ex, ey, Pal.WOOD_M)
            c.block(ex, ey - k, k, Pal.STONE_M); c.block(ex + d * k, ey - k, k, Pal.STONE_L)
            c.block(ex + 2 * d * k, ey - 3 * k, k, Pal.WOOD_L); c.block(ex + 3 * d * k, ey - k, k, Pal.WOOD_L)
        }
    }

    // ------------------------------------------------------------------ animals

    /** Draw a character-art sprite whose bottom row sits on [by], centred on [bx]; '.' is transparent. */
    private fun sprite(bx: Int, by: Int, rows: Array<String>, flip: Boolean, pal: (Char) -> Int) {
        val h = rows.size; val w = rows[0].length
        for ((r, row) in rows.withIndex()) for (j in row.indices) {
            val ch = row[j]
            if (ch == '.') continue
            val x = if (flip) bx + w / 2 - j else bx - w / 2 + j
            val col = pal(ch)
            if (ch == 'e' && col != Col.hex(0x2A2A30)) { c.penEmissive = true; c.set(x, by - h + r, col); c.penEmissive = false }
            else c.set(x, by - h + r, col)
        }
    }

    fun wolf(bx: Int, by: Int, faceRight: Boolean, eyes: Boolean, step: Int) {
        val l = Col.hex(0xB4B8C2); val m = Col.hex(0x80848E); val d = Col.hex(0x50535C); val w = Col.hex(0xDADCE2)
        val eye = if (eyes) Col.hex(0xF4F46A) else Col.hex(0x2A2A30)
        val legs = if (step == 0) "..d.d...d.d." else "...d.d..d.d."
        sprite(bx, by, arrayOf(
            "........m.m.",
            "........mmmm",
            "..lllllllmee",
            "dllmmmmmmmwmd",
            "d.mmmmmmmmw..",
            legs,
            legs,
        ).map { it.padEnd(13, '.') }.toTypedArray(), !faceRight) {
            when (it) { 'l' -> l; 'm' -> m; 'w' -> w; 'e' -> eye; else -> d }
        }
    }

    /** A brown bear standing up on its hind legs, arms out: instantly a bear, even at 12 px. */
    fun bear(bx: Int, by: Int, faceRight: Boolean, step: Int) {
        val l = Col.hex(0xA87444); val m = Col.hex(0x7A4C2A); val d = Col.hex(0x3E2414); val sn = Col.hex(0xD2A878)
        val arms = if (step == 0) "lmmmsmmml" else "mmmmsmmmm"
        val paws = if (step == 0) "d.......d" else "ld.....dl"
        sprite(bx, by, arrayOf(
            ".ll...ll.",
            ".lmmmmml.",
            ".mmmmmmm.",
            ".mdmmmdm.",
            ".mmsssmm.",
            "..mmdmm..",
            paws,
            arms,
            ".mmssmmm.",
            ".mmmmmmm.",
            ".mm...mm.",
            ".dd...dd.",
        ), !faceRight) {
            when (it) { 'l' -> l; 'm' -> m; 's' -> sn; else -> d }
        }
    }

    /** Merchant cart heading toward -x (up-left), horse in front. World anchor = cart centre. */
    fun cart(x: Float, y: Float, moving: Boolean) {
        val i = s.iso
        val step = if (moving) ((t * 6).toInt() and 1) else 0
        // horse (in front = smaller x): legs, body, neck/head
        val hx = x - 2.6f
        val horse = Col.hex(0x8A5A34); val horseL = Col.hex(0xA8764A); val horseD = Col.hex(0x5E3A20)
        for ((j, lx) in floatArrayOf(hx + 0.1f, hx + 1.1f).withIndex()) {
            i.post(lx, y - 0.1f, 0f, 3f + (if (j == step) 1 else 0), horseD)
            i.post(lx + 0.1f, y + 0.2f, 0f, 3f + (if (j != step) 1 else 0), horseD)
        }
        i.box(hx, y - 0.25f, 3f, 1.3f, 0.5f, 3f, horseL, horse, horseD)
        i.box(hx - 0.35f, y - 0.2f, 5f, 0.45f, 0.4f, 4f, horseL, horse, horseD)
        i.box(hx - 0.65f, y - 0.15f, 7f, 0.4f, 0.3f, 1.8f, horseL, horse, horseD)
        i.line(hx - 0.1f, y - 0.2f, 9f, hx + 0.15f, y - 0.2f, 6f, Col.hex(0x2E1E14))
        i.px(hx + 1.35f, y, 5f, Col.hex(0x2E1E14)); i.px(hx + 1.45f, y, 4f, Col.hex(0x2E1E14))
        // shafts
        i.line(hx + 0.6f, y + 0.3f, 4f, x - 0.8f, y + 0.3f, 3.5f, Pal.WOOD_D)
        // cart body
        i.box(x - 0.8f, y - 0.5f, 2f, 1.8f, 1.1f, 3f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        // canvas cover
        i.gableX(x - 0.8f, y - 0.55f, 5f, 1.8f, 1.2f, 4f, Pal.CANVAS_L, Pal.CANVAS_M, Pal.CANVAS_D, Pal.CANVAS_D)
        i.line(x - 0.4f, y + 0.65f, 5f, x - 0.4f, y + 0.05f, 9f, Pal.FLAG_RED)
        // wheels on the visible side
        for (wx in floatArrayOf(x - 0.5f, x + 0.6f)) {
            val sx = i.ix(wx, y + 0.65f); val sy = i.iy(wx, y + 0.65f, 1.5f)
            s.sprite(sx, sy) {
                c.fillRect(sx - 1, sy - 1, 3, 3, Col.hex(0x3A2618)); c.set(sx, sy, Pal.WOOD_L)
                c.set(sx + (if (step == 0) -1 else 1), sy - 1, Pal.WOOD_M)
            }
        }
        // goods poking out the back
        i.px(x + 1.0f, y + 0.2f, 5f, Col.hex(0xE07A1E)); i.px(x + 1.0f, y - 0.1f, 5f, Col.hex(0xC8392F)); i.px(x + 1.0f, y + 0.4f, 6f, Pal.GOLD)
    }

    // ------------------------------------------------------------------ weather + particles

    /** Rain over the village, [density] 1 in a storm, less when a scene's dialog brought a shower. */
    /**
     * Rain over the village ([density] 0..1: the storm's 1, a dialog's shower less; companion/GAME.md "People react"):
     * streaks slanting in the wind, fading toward their tails, and where they land below the [horizon] the ground is wet,
     * the drops splashing and spreading in rings. Sized in nominal px and drawn at the lens's detail (as the scenes' rain
     * is, render/scene/Weather.kt), so closer up the streaks are longer and fewer, as near rain is.
     */
    fun rain(horizon: Int, density: Float = 1f) {
        if (density <= 0.01f) return
        // over the screen (a canvas may hold more of the picture or less): its corner, and its size
        val sc = s.screenRect()
        val sx = sc.x0; val sy = sc.y0
        val w = sc.w; val h = sc.h
        // what of the nominal canvas this canvas shows, in nominal px
        val vw = w / k.toFloat(); val vh = h / k.toFloat()
        val col = Col.hex(0xC0CEE4)
        val n = (vw * vh / 55f * density).toInt()
        val slant = 0.38f
        val len = 4f + 3f * density // nominal px
        val a = 0.38f + 0.22f * density
        val thick = k.coerceAtMost(2)
        val span = vh + len + 12f
        val reach = slant * span
        val pxLen = (len * k).toInt().coerceAtLeast(2)
        for (j in 0 until n) {
            val sp = 150f + Noise.rnd(j, 32) * 70f // nominal px a second
            val yb = ((Noise.rnd(j, 33) * span + t * sp) % span).toFloat() - len - 6f
            val xb = Noise.rnd(j, 31) * (vw + reach + 16f) - reach - 8f + yb * slant
            // the canvas pixel of the streak's head, and its length in canvas px
            val x = (xb * k).toInt(); val y = (yb * k).toInt()
            if (y < 0 || y - pxLen >= h || x < -pxLen || x - pxLen >= w) continue
            for (q in 0 until pxLen) {
                val px = x - (q * slant).toInt(); val py = y - q
                if (py < 0 || py >= h) continue
                val f = a * (1f - 0.65f * q / pxLen)
                for (o in 0 until thick) if (px + o in 0 until w) c.blend(sx + px + o, sy + py, col, f)
            }
        }
        // on the ground (what isn't a building, a person or a tree: the canvas's background), the drops splash and ring out
        val ground = (horizon - sy + 4 * k).coerceAtLeast(0)
        if (ground >= h) return
        val gh = (h - ground) / k.toFloat()
        val m = (vw * gh / 260f * density).toInt()
        val ring = Col.mix(col, Col.hex(0xFFFFFF), 0.25f)
        for (j in 0 until m) {
            val cycle = t * 1.7 + Noise.rnd(j, 34)
            val ph = (cycle % 1.0).toFloat()
            if (ph > 0.55f) continue
            val round = cycle.toInt()
            val bx = Noise.rnd(j, 35, round) * vw; val by = Noise.rnd(j, 36, round) * gh
            val cx = sx + (bx * k).toInt(); val cy = sy + ground + (by * k).toInt()
            if (cx !in sx until sx + w || cy !in sy until sy + h || !c.inside(cx, cy) || c.ids[c.index(cx, cy)] != 0) continue
            if (ph < 0.12f) {
                // the drop's crown
                s.sprite(cx, cy) {
                    c.blend(cx - k, cy - k, ring, 0.7f); c.blend(cx + k, cy - k, ring, 0.7f); c.blend(cx, cy - 2 * k, ring, 0.5f)
                }
            } else {
                // its ring spreading on the wet ground, fading
                val u = (ph - 0.12f) / 0.43f
                val rx = (1f + 2.6f * u) * k; val ry = rx * 0.45f
                val fa = 0.5f * (1f - u)
                val steps = (rx * 3f).toInt().coerceIn(8, 40)
                for (i in 0 until steps) {
                    val ang = i * (2.0 * PI / steps)
                    val px = (cx + cos(ang) * rx).toInt(); val py = (cy + sin(ang) * ry).toInt()
                    if (px in sx until sx + w && py in sy until sy + h && c.inside(px, py) && c.ids[c.index(px, py)] == 0) c.blend(px, py, ring, fa)
                }
            }
        }
    }

    /** A bolt of lightning from the top of the sky down to [horizon], crooked, seeded by [seed] (a new one each flash). */
    fun lightning(horizon: Int, seed: Int) {
        c.penEmissive = true
        val sc = s.screenRect()
        var x = sc.x0 + sc.w * (0.25f + Noise.rnd(seed, 41) * 0.5f)
        var y = sc.y0.toFloat()
        val bolt = Col.hex(0xF4F6FF)
        while (y < horizon - 4) {
            val nx = x + (Noise.rnd(seed, y.toInt(), 42) - 0.5f) * 8f * k
            val ny = y + (3 + Noise.rnd(seed, y.toInt(), 43) * 4) * k
            c.line(x.toInt(), y.toInt(), nx.toInt(), ny.toInt(), bolt)
            c.line(x.toInt() + 1, y.toInt(), nx.toInt() + 1, ny.toInt(), Col.hex(0xB8C8FF))
            x = nx; y = ny
        }
        c.penEmissive = false
    }

    fun leaves(season: Season, month: Int) {
        // over the screen (a canvas may hold more of the picture or less, a close-up's window of a bigger one, see
        // PixelCanvas.originX)
        val sc = s.screenRect()
        val w = sc.w; val h = sc.h
        val (n, cols) = when (season) {
            Season.AUTUMN -> (if (month == 11) 10 else 22) to intArrayOf(Col.hex(0xF08C2C), Col.hex(0xD6452C), Col.hex(0xECC23E), Col.hex(0xC9651F))
            Season.WINTER -> 80 to intArrayOf(Pal.SNOW_L, Col.hex(0xFFFFFF), Pal.SNOW_M)
            Season.SPRING -> 12 to intArrayOf(Col.hex(0xF4B8CC), Col.hex(0xFFFFFF))
            Season.SUMMER -> 0 to intArrayOf()
        }
        for (j in 0 until n) {
            val sp = (if (season == Season.WINTER) 7f + Noise.rnd(j, 51) * 8f else 9f + Noise.rnd(j, 51) * 7f) * k
            val y = ((Noise.rnd(j, 52) * (h + 10) + t * sp) % (h + 10)).toFloat() - 5
            val x = Noise.rnd(j, 53) * w + sin(t * (1.1 + Noise.rnd(j, 54)) + j).toFloat() * 4f * k + (t * 3 % w).toFloat() * (if (season == Season.WINTER) 0f else 0.3f)
            val xi = floor(x % w).toInt() + sc.x0; val yi = y.toInt() + sc.y0
            c.block(xi, yi, k, cols[j % cols.size])
            if (season == Season.AUTUMN && ((t * 4).toInt() + j) % 2 == 0) c.block(xi + k, yi, k, cols[(j + 1) % cols.size])
        }
    }

    fun fireworks(p: Float, horizon: Int) {
        if (p < 0f || p > 1f) return
        // over the screen (a canvas may hold more of the picture or less)
        val sc = s.screenRect()
        val w = sc.w; val left = sc.x0; val bottom = sc.y0 + sc.h
        c.penEmissive = true
        val palettes = arrayOf(
            intArrayOf(Pal.GOLD_L, Pal.GOLD, Col.hex(0xFF8A1A)),
            intArrayOf(Col.hex(0xFFB0C0), Pal.FLAG_RED, Col.hex(0x9A1030)),
            intArrayOf(Col.hex(0xB8D8FF), Col.hex(0x4A90E8), Pal.FLAG_BLUE),
            intArrayOf(Col.hex(0xC8FFD0), Col.hex(0x3EC878), Col.hex(0x16A06E)),
        )
        for (j in 0..3) {
            val u = (p - j * 0.14f) / 0.55f
            if (u < 0f || u > 1f) continue
            val cx = left + w * (0.22f + 0.56f * Noise.rnd(j, 61)); val cy = 8f * k + horizon * 0.55f * Noise.rnd(j, 62)
            val pal = palettes[j % palettes.size]
            if (u < 0.25f) {
                val ry = bottom - (bottom - cy) * (u / 0.25f)
                c.block(cx.toInt(), ry.toInt(), k, pal[0]); c.block(cx.toInt(), ry.toInt() + k, k, pal[1]); c.block(cx.toInt(), ry.toInt() + 2 * k, k, pal[2])
            } else {
                val e = (u - 0.25f) / 0.75f
                val r = (4f + 16f * (1 - (1 - e) * (1 - e))) * k
                if (e < 0.2f) s.light(cx, cy, 60f, 0.8f * (1 - e / 0.2f))
                for (q in 0 until 26) {
                    val a = 2 * PI * q / 26 + Noise.rnd(j, q) * 0.2
                    val sp = 0.75f + Noise.rnd(j, q, 2) * 0.35f
                    val px = cx + cos(a).toFloat() * r * sp
                    val py = cy + sin(a).toFloat() * r * sp * 0.9f + e * e * 10f * k
                    val col = pal[if (e < 0.35f) 0 else if (e < 0.7f) 1 else 2]
                    if (e > 0.85f && (q + (t * 12).toInt()) % 2 == 0) continue
                    c.block(px.toInt(), py.toInt(), k, col)
                    if (e < 0.5f) c.block((px - cos(a) * 1.5f * k).toInt(), (py - sin(a) * 1.5f * k).toInt(), k, pal[2])
                }
            }
        }
        c.penEmissive = false
        // confetti drifting down over the village
        for (j in 0 until 44) {
            val start = Noise.rnd(j, 71) * 0.35f
            val u = (p - start) / 0.65f
            if (u < 0f || u > 1f) continue
            val x = left + Noise.rnd(j, 72) * w + sin(u * 9 + j).toFloat() * 3f * k
            val y = sc.y0 - 4 + u * (sc.h + 8)
            val col = Pal.PAINTED[j % Pal.PAINTED.size]
            c.block(x.toInt(), y.toInt(), k, col)
            if (((t * 10).toInt() + j) % 2 == 0) c.block(x.toInt() + k, y.toInt(), k, col) else c.block(x.toInt(), y.toInt() + k, k, col)
        }
    }

    /**
     * Bunting strung between two screen points with a little sag, its flags in [cols] in turn (the village's culture's,
     * [Festive.bunting]), plus lanterns at night.
     */
    fun bunting(x0: Float, y0: Float, x1: Float, y1: Float, seed: Int, lanterns: Boolean, cols: IntArray = s.look.festive.bunting) {
        val len = max(abs(x1 - x0), abs(y1 - y0)).toInt()
        if (len <= 0) return
        val string = Col.hex(0x3A2A20)
        for (j in 0..len) {
            val u = j.toFloat() / len
            val x = x0 + (x1 - x0) * u; val y = y0 + (y1 - y0) * u + sin(u * PI).toFloat() * 4f * k
            c.set(x.toInt(), y.toInt(), string)
            if ((j / k) % 3 == 1 && j % k == 0) {
                val col = cols[(j / k / 3 + seed) % cols.size]
                c.block(x.toInt(), y.toInt() + k, k, col); c.block(x.toInt() + k, y.toInt() + k, k, col); c.block(x.toInt(), y.toInt() + 2 * k, k, col)
            }
            if (lanterns && (j / k) % 8 == 4 && j % k == 0) {
                c.penEmissive = true
                c.block(x.toInt(), y.toInt() + k, k, Pal.WINDOW_LIT); c.block(x.toInt(), y.toInt() + 2 * k, k, Pal.FLAME[2])
                c.penEmissive = false
                s.light(x, y + 2 * k, 12f, 0.45f)
            }
        }
    }

    companion object {
        /** A hearth's smoke: a light grey. */
        val PLUME = Col.hex(0xBEBCC4)
        const val PROP_NONE = 0; const val PROP_STICK = 1; const val PROP_CROOK = 2; const val PROP_HAMMER = 3; const val PROP_BASKET = 4
        const val PROP_BOOK = 5; const val PROP_SPOON = 6; const val PROP_MUG = 7; const val PROP_BOTTLE = 8; const val PROP_SMOKER = 9; const val PROP_FLOWER = 10
        /** The pilgrim's tall staff with a gourd. */
        const val PROP_STAFF = 11
        private val GOURD = Col.hex(0xD9A040)
        private val SHELL = Col.hex(0xF4EEDD)
        /** A lantern's iron (its cap, foot and posts, a lighter brim), its light's reach (nominal px) and strength. */
        private val LAMP = Col.hex(0x2E2A30)
        private val LAMP_L = Col.hex(0x4A4550)
        private const val LAMP_R = 14f
        private const val LAMP_I = 0.6f
        /** A defender's torch: its light reaches farther than a lantern's, and flickers more (see [torch1]). */
        private const val TORCH_R = 20f
        private const val TORCH_I = 0.75f
        /** The washing: white linen, grey where it's wet. */
        private val CLOTH = Col.hex(0xF4EEDD)
        private val CLOTH_D = Col.hex(0xB4BECA)
        /** The washing line's cord. */
        private val LINE = Col.hex(0xD8D2C4)
        /** A rug to beat: red, its border dark, its pattern gold. */
        private val RUG = Col.hex(0xA8323A)
        private val RUG_D = Col.hex(0x6E1E26)
        private val RUG_L = Col.hex(0xD8A040)
        /** Dust swept up, beaten out of a rug. */
        private val DUST = Col.hex(0xC8B89A)
        /** What the pickers find at the forest's edge: bilberries, mushrooms' brown caps and pale stems. */
        private val BILBERRY = Col.hex(0x34407A)
        private val MUSHROOM = Col.hex(0xA86A3C)
        private val MUSHROOM_STEM = Col.hex(0xEADFC8)
        /** A brown hen (the others are white). */
        private val HEN_BROWN = Col.hex(0xA0602E)
        /** A mouth open, talking. */
        private val MOUTH_OPEN = Col.hex(0x6A2226)

        /** Sprites the unnamed population borrows at the finest detail. */
        private val ANON_ARTS = listOf("farmer", "aunt", "child1", "child3", "man", "woman", "child2", "innkeeper")

        /**
         * Waiting for the learner at [time] (s): 1.5 s of every 8 an arm up, waving, each at their own moment by the
         * drawing's [seed]; the seconds into the wave, or -1 between. [TownPeople] asks too: whoever waits at their work
         * works between the waves.
         */
        fun waving(time: Double, seed: Int): Float {
            val v = (time / 8.0 + ((seed ushr 4) and 0xFF) / 256.0 + 0.13) % 1.0
            val w = (if (v < 0.0) v + 1.0 else v).toFloat() * 8f
            return if (w < 1.5f) w else -1f
        }

        /**
         * How someone with sprite [art] looks in the small figures; an unknown or missing art gets colours by
         * [seed] (the unnamed population: the same seed always gives the same person).
         */
        fun lookOf(art: String?, seed: Int): Look {
            val shirt = Pal.SHIRTS[(seed ushr 3).mod(Pal.SHIRTS.size)]
            val hair = Pal.HAIR[(seed ushr 7).mod(Pal.HAIR.size)]
            val dark = Col.hex(0x3A3246)
            return when (art) {
                "grandma" -> Look(Col.hex(0x2F4A8A), Col.hex(0x2F4A8A), Pal.HAIR[4], kerchief = true, apron = Col.hex(0xF4EEDD), prop = PROP_SPOON, skirt = true)
                "grandpa" -> Look(Col.hex(0xE9E2D0), Col.hex(0x4A4652), Pal.HAIR[4], hat = Col.hex(0x5A4232), prop = PROP_STICK, beard = Col.hex(0xE6E2DA))
                "child1" -> Look(Pal.SHIRTS[0], Col.hex(0x2E63B0), Pal.HAIR[3], small = true)
                "child2" -> Look(Col.hex(0xF0C23A), Col.hex(0xF0C23A), Pal.HAIR[2], skirt = true, small = true, prop = PROP_FLOWER)
                "child3" -> Look(Pal.SHIRTS[2], Col.hex(0x6F4527), Pal.HAIR[1], hat = Col.hex(0x2C7F86), small = true)
                "shepherd" -> Look(Col.hex(0x8A6A48), Col.hex(0x4A3A2F), Pal.HAIR[0], hat = Col.hex(0x3E5A3A), prop = PROP_CROOK)
                "smith" -> Look(Col.hex(0x4A5A6A), Col.hex(0x2E2A34), Pal.HAIR[1], kerchief = true, apron = Col.hex(0x8A5A32), prop = PROP_HAMMER, beard = Col.hex(0x3A2A24))
                "teacher" -> Look(Col.hex(0x7A4E9A), dark, Col.hex(0x8A4A22), skirt = true, prop = PROP_BOOK)
                "farmer" -> Look(Col.hex(0x2E63B0), Col.hex(0x6F4527), Pal.HAIR[3], hat = Pal.HAY_L, straw = true, skin = Col.hex(0xE0A878))
                "beekeeper" -> Look(Pal.CANVAS_L, Pal.CANVAS_M, Pal.HAIR[0], hat = Pal.CANVAS_L, prop = PROP_SMOKER)
                "innkeeper" -> Look(Col.hex(0xE9E2D0), Col.hex(0x2E2A34), Pal.HAIR[0], apron = Col.hex(0xF4EEDD), prop = PROP_MUG)
                "winemaker" -> Look(Col.hex(0x7A2E3A), dark, Pal.HAIR[1], hat = Col.hex(0x2E2A34), prop = PROP_BOTTLE)
                "aunt" -> Look(Col.hex(0xE07A8A), Col.hex(0xE07A8A), Pal.HAIR[0], skirt = true, apron = Col.hex(0x3E8A3A), prop = PROP_BASKET)
                // the strangers at the road: the pedlar under his krošnja, a pilgrim with his staff and bag
                "pedlar" -> Look(Col.hex(0xE9E2D0), Col.hex(0x4A3A2F), Pal.HAIR[1], hat = Col.hex(0x2A2426), prop = PROP_STICK, pack = true)
                "pilgrim" -> Look(Col.hex(0x8A6E4E), Col.hex(0x4A4652), Pal.HAIR[3], hat = Col.hex(0x7A5E3E), prop = PROP_STAFF, beard = Pal.HAIR[3], bag = Col.hex(0xB08A56), shell = true)
                // the hunter, in loden green, his hat green too
                "hunter" -> Look(Col.hex(0x4E6A3E), Col.hex(0x6A5238), Pal.HAIR[4], hat = Col.hex(0x3E5436), prop = PROP_STICK)
                // the charcoal burner by his pile: a jacket gone grey with soot, a black hat, a dark beard, his poker
                "burner" -> Look(Col.hex(0x5A524A), Col.hex(0x2E2A2A), Pal.HAIR[1], hat = Col.hex(0x2A2426), prop = PROP_STICK, beard = Col.hex(0x3A2E28))
                "woman" -> Look(shirt, shirt, hair, kerchief = (seed ushr 11) % 3 == 0, skirt = true, apron = if ((seed ushr 13) % 2 == 0) Col.hex(0xF4EEDD) else 0)
                "man" -> Look(shirt, if ((seed ushr 13) % 2 == 0) dark else Col.hex(0x6F4527), hair, hat = if ((seed ushr 11) % 3 == 0) Col.hex(0x5A4232) else 0)
                else -> Look(shirt, if ((seed ushr 13) % 2 == 0) dark else Col.scale(shirt, 0.7f), hair, kerchief = (seed ushr 11) % 4 == 0)
            }
        }
    }
}
