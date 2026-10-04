package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.Wildlife
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The outdoor scenes' wild animals ([Wildlife]): which are out ([out], the day's mix or the frame's), where today
 * ([spot]), and how each looks: the hare, the fox, the hedgehog, the roe deer, the stag and his hinds, the wild boar with
 * her striped piglets, the badger, the dormouse, the bats, the butterflies, the chamois, the ibex, the bear's eyes.
 *
 * Each is drawn from shapes in its own units ([anchor]: 1 unit is a pixel of the scene's canvas at size 1), its feet on
 * the ground at the anchor, facing right unless turned; closer up (detail 2 and 3) the same shapes come out finer, so
 * every detail and every window of the picture agree. They are drawn in daylight colours for the light pass to dim, but
 * the eyes that shine at night and the bats against the sky.
 */
internal abstract class WildPainter : VistaPainter() {
    private var wildFrame: SceneFrame? = null
    private var wildNow: Set<String> = emptySet()

    /** Whether wild animal [id] is out in this frame (the day's, or the frame's [SceneFrame.wild]), or drawn alone for its sticker. */
    protected fun out(id: String): Boolean {
        if (solo == id) return true
        if (wildFrame !== frame) { wildNow = frame.wildOf(art); wildFrame = frame }
        return id in wildNow
    }

    /** Where [id] is today, 0..1 ([Wildlife.spot]): the painter's pick among its places. */
    protected fun spot(id: String): Float = Wildlife.spot(art, id, frame.village, frame.day, frame.hour, frame.month)

    /**
     * Wild animal [slot] drawn by [draw]: as its word (a thing to tap) when it is out, else as scenery (a dialog's cue
     * brought it, the day didn't).
     */
    protected inline fun creature(slot: String, slop: Int = 2, draw: () -> Unit) {
        if (out(slot)) thing(slot, slop = slop) { draw() } else prop { draw() }
    }

    // ------------------------------------------------------------------ drawing in a creature's units

    private var wx = 0f
    private var wy = 0f
    private var wk = 1f
    private var wf = 1f

    /** Draws the next creature with its feet at ([x], [y]) (picture px), [size] scene px per unit, facing right or [left]. */
    protected fun anchor(x: Float, y: Float, size: Float = 1f, left: Boolean = false) {
        wx = x; wy = y; wk = size * detail; wf = if (left) -1f else 1f
    }

    private fun qx(u: Float) = wx + wf * u * wk
    private fun qy(v: Float) = wy + v * wk

    /** An ellipse round ([cx], [cy]). */
    protected fun ell(cx: Float, cy: Float, rx: Float, ry: Float, col: Int) = c.fillEllipse(qx(cx), qy(cy), max(0.5f, rx * wk), max(0.5f, ry * wk), col)

    /** A convex polygon through the points (x, y, x, y …). */
    protected fun poly(col: Int, vararg pts: Float) {
        c.polyBegin()
        var i = 0
        while (i < pts.size) { c.polyAdd(qx(pts[i]), qy(pts[i + 1])); i += 2 }
        c.polyFill(col)
    }

    /** A line from ([x0], [y0]) to ([x1], [y1]), [th] units thick (a picture pixel at the least). */
    protected fun seg(x0: Float, y0: Float, x1: Float, y1: Float, th: Float, col: Int) {
        val ax0 = qx(x0); val ay0 = qy(y0); val ax1 = qx(x1); val ay1 = qy(y1)
        val tt = th * wk
        if (tt <= 1.01f) { c.line(floor(ax0).toInt(), floor(ay0).toInt(), floor(ax1).toInt(), floor(ay1).toInt(), col); return }
        val dx = ax1 - ax0; val dy = ay1 - ay0
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val nx = -dy / len * tt / 2; val ny = dx / len * tt / 2
        c.polyBegin(); c.polyAdd(ax0 + nx, ay0 + ny); c.polyAdd(ax1 + nx, ay1 + ny); c.polyAdd(ax1 - nx, ay1 - ny); c.polyAdd(ax0 - nx, ay0 - ny)
        c.polyFill(col)
    }

    /** A square [s] units across round ([x], [y]), one picture pixel at the least: an eye, a nose, a glint. */
    protected fun dot(x: Float, y: Float, col: Int, s: Float = 1f) {
        val n = max(1, (s * wk).roundToInt())
        c.fillRect(floor(qx(x) - n / 2f + 0.5f).toInt(), floor(qy(y) - n / 2f + 0.5f).toInt(), n, n, col)
    }

    // ------------------------------------------------------------------ the animals

    /**
     * The brown hare sitting up in the grass: long ears with black tips, a white scut; its ears twitch now and then.
     * [hop] 0..1 is a hop's arc (the hind legs out, lifted), 0 sitting.
     */
    protected fun hare(x: Float, y: Float, size: Float = 1f, left: Boolean = false, hop: Float = 0f) {
        anchor(x, y - sin(hop * Math.PI.toFloat()) * 3f * size * detail, size, left)
        val coat = Col.hex(0x9C7A52); val light = Col.hex(0xC4A276); val dark = Col.hex(0x6A5236); val belly = Col.hex(0xE2D4B6)
        val twitch = if (sin(t * 2.3 + x * 0.1) > 0.85) 0.6f else 0f
        if (hop > 0.05f) {
            // stretched out in the leap
            seg(-3.6f, -1.8f, -6f, -0.6f, 1.2f, dark)
            ell(-0.4f, -2.6f, 3.8f, 1.7f, coat); ell(-0.6f, -3.3f, 3.2f, 0.8f, light)
            seg(2.2f, -2f, 3.8f, -0.6f, 0.9f, dark)
        } else {
            ell(-1.8f, -1.2f, 2.2f, 1.2f, dark) // the haunch folded under
            ell(-0.6f, -2.4f, 3f, 2.1f, coat); ell(-1f, -3.4f, 2.4f, 0.9f, light)
            ell(0.4f, -1.2f, 1.8f, 0.9f, belly)
            seg(1.8f, -1.6f, 2.1f, 0f, 1f, coat) // the forelegs
            ell(-2.2f, -0.3f, 1.8f, 0.5f, dark) // the long hind foot
        }
        ell(-3.4f, -3f, 0.9f, 0.9f, Col.hex(0xF4F0E6)) // the scut
        ell(2.6f, -4.4f, 1.6f, 1.3f, coat)
        ell(3.6f, -4.1f, 0.8f, 0.7f, light)
        seg(1.9f, -5.4f, 0.6f - twitch, -9f, 1f, coat); seg(2.6f, -5.4f, 2.4f + twitch, -9.2f, 1f, light)
        dot(0.6f - twitch, -8.8f, Col.hex(0x241A14)); dot(2.4f + twitch, -9f, Col.hex(0x241A14))
        dot(3f, -4.7f, Col.hex(0x241A14))
        dot(4.3f, -4.1f, Col.hex(0x5A3A30))
    }

    /** The red fox trotting (its legs going with [trot]), or sitting up and looking at us ([sit]); the white tip of its brush. */
    protected fun fox(x: Float, y: Float, size: Float = 1f, left: Boolean = false, sit: Boolean = false, trot: Double = t) {
        anchor(x, y, size, left)
        val red = Col.hex(0xD2702C); val redL = Col.hex(0xE8944A); val redD = Col.hex(0x9A4A1E); val white = Col.hex(0xF4ECE0); val black = Col.hex(0x2A1C18)
        if (sit) {
            ell(-2.6f, -1.4f, 2.6f, 1.2f, red); ell(-4.8f, -1f, 1f, 0.8f, white) // the brush round its feet
            ell(0f, -3.6f, 2f, 3f, red); ell(0.8f, -3.2f, 1f, 2.2f, white)
            seg(0.6f, -1.4f, 0.6f, 0f, 0.9f, black); seg(1.6f, -1.4f, 1.6f, 0f, 0.9f, black)
            ell(0.8f, -7.4f, 1.8f, 1.5f, red); ell(0.8f, -6.8f, 1.1f, 0.8f, white)
            poly(red, -0.6f, -8f, 0.2f, -8.4f, -0.6f, -10.2f); poly(red, 1.4f, -8.4f, 2.2f, -8f, 2.2f, -10.2f)
            dot(-0.5f, -9.6f, black); dot(2.1f, -9.6f, black)
            dot(0.1f, -7.6f, black); dot(1.5f, -7.6f, black); dot(0.8f, -6.6f, black)
            return
        }
        val ph = ((trot * 4.0) % 1.0).toFloat()
        val a = sin(ph * 2f * Math.PI.toFloat()) * 0.9f
        // the brush out behind, the white tip
        poly(red, -3f, -4.2f, -3f, -3f, -7.4f, -2.6f, -8.2f, -3.4f)
        ell(-6.6f, -3.2f, 1.9f, 1.1f, red); ell(-8.2f, -3.1f, 1f, 0.8f, white)
        // the legs, black stockings
        seg(-2.2f, -3f, -2.2f - a, 0f, 0.9f, black); seg(-1.2f, -3f, -1.2f + a, 0f, 0.9f, black)
        seg(2f, -3f, 2f + a, 0f, 0.9f, black); seg(3f, -3f, 3f - a, 0f, 0.9f, black)
        ell(0.2f, -3.8f, 3.6f, 1.5f, red); ell(0f, -4.6f, 3f, 0.6f, redL); ell(0.4f, -3f, 2.8f, 0.6f, redD)
        ell(3.4f, -3.8f, 1.1f, 1.2f, white) // the white chest
        ell(4.4f, -5.2f, 1.5f, 1.2f, red)
        poly(red, 5f, -5.6f, 7.4f, -4.8f, 5.2f, -4.2f); dot(7.2f, -4.8f, black) // the snout, its nose
        poly(white, 5f, -4.6f, 7f, -4.6f, 5.2f, -4.1f)
        poly(red, 3.6f, -5.8f, 4.6f, -6f, 3.8f, -7.8f); poly(redD, 4.6f, -6f, 5.4f, -5.8f, 5.2f, -7.6f)
        dot(3.8f, -7.4f, black); dot(5.2f, -7.3f, black)
        dot(5.3f, -5.4f, black)
    }

    /** The hedgehog nosing about ([curl] 0), or rolled up into a ball of spines ([curl] 1). */
    protected fun hedgehog(x: Float, y: Float, size: Float = 1f, left: Boolean = false, curl: Float = 0f) {
        anchor(x, y, size, left)
        val spines = Col.hex(0x6E5A44); val tips = Col.hex(0xC8B494); val dark = Col.hex(0x3E3024); val face = Col.hex(0xB89A78)
        if (curl > 0.5f) {
            ell(0f, -1.9f, 2.2f, 1.9f, spines)
            for (k in 0 until 9) { val a = k * 0.7f; dot(cos(a) * 1.5f, -1.9f + sin(a) * 1.3f, tips) }
            dot(1.6f, -0.6f, dark)
            return
        }
        val sniff = if (sin(t * 5 + x) > 0.3) 0.3f else 0f
        seg(-1.8f, -0.8f, -1.8f, 0f, 0.8f, dark); seg(1.4f, -0.8f, 1.4f, 0f, 0.8f, dark)
        ell(2.2f, -1f, 1.3f, 0.9f, face)
        poly(face, 2.8f, -1.6f, 4.4f + sniff, -0.9f, 2.8f, -0.4f)
        dot(4.3f + sniff, -0.9f, dark); dot(2.7f, -1.4f, dark)
        ell(-0.3f, -1.7f, 2.8f, 1.7f, spines)
        for (k in 0 until 8) dot(-2.4f + k * 0.62f, -2.4f + abs(k - 3.5f) * 0.22f - (k and 1) * 0.5f, tips)
        for (k in 0 until 5) dot(-2f + k * 0.9f, -1.3f + (k and 1) * 0.4f, Col.scale(spines, 0.75f))
    }

    /**
     * A roe deer: a doe or a buck with small spiked antlers ([buck]); grazing, its head down ([graze]), else watching,
     * its big ears up; the white rump. [walk] 0..1 moves its legs.
     */
    protected fun roe(x: Float, y: Float, size: Float = 1f, left: Boolean = false, graze: Boolean = false, buck: Boolean = false, walk: Float = 0f) {
        anchor(x, y, size, left)
        val coat = Col.hex(0xA8743E); val light = Col.hex(0xC8945C); val dark = Col.hex(0x6E4A2A); val hoof = Col.hex(0x2E2018); val cream = Col.hex(0xF2E8D6)
        val a = sin(walk * 6.28f) * 0.8f
        for ((lx, s) in listOf(-4f to a, -3f to -a, 3f to -a, 4f to a)) seg(lx, -5f, lx + s, 0f, 0.8f, dark)
        ell(0f, -6f, 5f, 2.2f, coat); ell(0f, -7f, 4.2f, 0.9f, light)
        ell(-4.6f, -6.4f, 1f, 1.4f, cream) // the rump patch
        if (graze) {
            poly(coat, 3f, -7.4f, 4.4f, -6.4f, 7f, -1.6f, 5.8f, -1f)
            ell(6.8f, -1.2f, 1.3f, 1f, coat); dot(7.6f, -1f, hoof)
            seg(6.2f, -2f, 5.8f, -3.8f, 0.7f, coat); seg(6.8f, -2.2f, 7f, -4f, 0.7f, coat)
            if (buck) { seg(6.2f, -2.2f, 5.6f, -4.6f, 0.5f, dark); seg(6.6f, -2.2f, 6.6f, -4.8f, 0.5f, dark) }
        } else {
            poly(coat, 3f, -7.6f, 4.8f, -6.6f, 6.2f, -10.6f, 4.8f, -11.2f)
            ell(6f, -11.2f, 1.5f, 1.1f, coat); poly(coat, 6.6f, -11.8f, 8.6f, -10.8f, 6.8f, -10.2f)
            dot(8.4f, -10.9f, hoof); dot(6.3f, -11.6f, hoof)
            seg(5.2f, -12f, 4.4f, -14.2f, 0.8f, coat); seg(6f, -12.2f, 6.2f, -14.4f, 0.8f, coat)
            if (buck) { seg(5.4f, -12.2f, 5.2f, -15.4f, 0.5f, dark); seg(6f, -12.2f, 6.6f, -15.2f, 0.5f, dark) }
        }
    }

    /**
     * The red deer stag, bigger and darker than the roe, a mane on his neck and wide antlers with their tines; or a hind
     * ([stag] false), no antlers. [walk] 0..1 moves the legs.
     */
    protected fun redDeer(x: Float, y: Float, size: Float = 1f, left: Boolean = false, stag: Boolean = true, walk: Float = 0f) {
        anchor(x, y, size, left)
        val coat = if (stag) Col.hex(0x8A5634) else Col.hex(0x9C6A42); val dark = Col.hex(0x5A3620); val mane = Col.hex(0x5E3E2A)
        val rump = Col.hex(0xE0C89A); val hoof = Col.hex(0x241810); val antler = Col.hex(0xD8C8A4)
        val a = sin(walk * 6.28f) * 1f
        for ((lx, s) in listOf(-5f to a, -4f to -a, 3.6f to -a, 4.8f to a)) seg(lx, -6.4f, lx + s, 0f, 1f, dark)
        ell(0f, -7.6f, 6f, 2.8f, coat); ell(-5.4f, -8f, 1.3f, 1.8f, rump)
        poly(if (stag) mane else coat, 3.4f, -9.6f, 5.8f, -8f, 7.6f, -13f, 5.6f, -13.8f)
        ell(7.4f, -13.8f, 1.8f, 1.3f, coat); poly(coat, 8.2f, -14.4f, 10.6f, -13.2f, 8.4f, -12.6f)
        dot(10.4f, -13.2f, hoof); dot(7.8f, -14.2f, hoof)
        seg(6.4f, -14.6f, 5.4f, -16f, 0.9f, coat)
        if (stag) {
            // two beams sweeping back and up, three tines on each
            for ((bx, lean) in listOf(6.6f to -1f, 7.8f to 0.6f)) {
                seg(bx, -14.8f, bx + lean * 1.4f - 1.4f, -19.6f, 0.7f, antler)
                seg(bx + lean * 1.4f - 1.4f, -19.6f, bx + lean * 2f - 3.4f, -22f, 0.6f, antler)
                seg(bx - 0.2f, -15.6f, bx + 1.6f, -16.6f, 0.5f, antler)
                seg(bx + lean * 0.7f - 0.8f, -17.8f, bx + lean * 0.7f + 0.8f, -19.2f, 0.5f, antler)
                seg(bx + lean * 1.4f - 1.4f, -19.6f, bx + lean * 1.4f, -21.6f, 0.5f, antler)
            }
        } else seg(7.8f, -14.6f, 8.2f, -16.2f, 0.9f, coat)
    }

    /**
     * The wild boar rooting with her striped piglets: a dark, bristly sow, her snout down to the ground, bobbing as she
     * digs; [piglets] little ones trotting round her (0 to 3); [scatter] 0..1 runs them off from her and back.
     */
    protected fun boars(x: Float, y: Float, size: Float = 1f, left: Boolean = false, piglets: Int = 3, scatter: Float = 0f) {
        anchor(x, y, size, left)
        val hide = Col.hex(0x4A3C32); val bristle = Col.hex(0x2E241E); val light = Col.hex(0x6E5C4C); val snout = Col.hex(0x8A6A5E)
        val dig = if (sin(t * 3 + x * 0.3) > 0f) 0.5f else 0f
        seg(-3f, -2.4f, -3f, 0f, 1f, bristle); seg(-1.8f, -2.4f, -1.6f, 0f, 1f, bristle)
        seg(2.2f, -2.4f, 2.2f, 0f, 1f, bristle); seg(3.2f, -2.4f, 3.4f, 0f, 1f, bristle)
        ell(0f, -3.2f, 4.4f, 2.4f, hide); ell(-0.4f, -4.8f, 3.6f, 1f, bristle); ell(0.4f, -2.4f, 3.4f, 0.8f, light)
        seg(-4.2f, -3.6f, -5f, -2.6f, 0.5f, bristle) // the little tail
        poly(hide, 3.2f, -4.8f, 5.4f, -3.4f, 6.8f, -1.2f + dig, 5.2f, -1f + dig, 3f, -2.2f)
        ell(6.6f, -1f + dig, 0.8f, 0.7f, snout); dot(6.9f, -1f + dig, bristle)
        poly(bristle, 3.6f, -5f, 4.6f, -5.2f, 4.4f, -6.2f); dot(5f, -3.6f, Col.hex(0x14100C))
        val stripe = Col.hex(0xD8C49A)
        for (k in 0 until piglets) {
            val home = floatArrayOf(-6.8f, 8.6f, -3.2f)[k]
            val run = scatter * floatArrayOf(-7f, 8f, -12f)[k]
            val px = home + run + sin(t * 2.6 + k * 2.1).toFloat() * 0.8f
            val py = floatArrayOf(0.6f, 0.2f, 1.4f)[k]
            val ph = sin(t * 9 + k).toFloat() * 0.4f
            seg(px - 1f, py - 1.2f, px - 1f + ph, py, 0.7f, bristle); seg(px + 1f, py - 1.2f, px + 1f - ph, py, 0.7f, bristle)
            ell(px, py - 1.8f, 2f, 1.2f, Col.hex(0x8A6A48))
            seg(px - 1.4f, py - 2.1f, px + 1.4f, py - 2.1f, 0.4f, stripe); seg(px - 1.4f, py - 1.4f, px + 1.4f, py - 1.4f, 0.4f, stripe)
            ell(px + 2f, py - 1.6f, 0.9f, 0.7f, Col.hex(0x8A6A48)); dot(px + 2.8f, py - 1.4f, snout)
        }
    }

    /** The badger shuffling along the forest's edge, low and grey, its white face with the black stripes. */
    protected fun badger(x: Float, y: Float, size: Float = 1f, left: Boolean = false) {
        anchor(x, y, size, left)
        val grey = Col.hex(0x8C8A86); val greyL = Col.hex(0xB0ACA4); val black = Col.hex(0x1E1C1C); val white = Col.hex(0xF0EEE8)
        val step = if (sin(t * 5 + x) > 0) 0.4f else -0.4f
        seg(-2.4f, -1f, -2.4f + step, 0f, 1f, black); seg(2f, -1f, 2f - step, 0f, 1f, black)
        ell(-0.2f, -1.8f, 3.4f, 1.6f, grey); ell(-0.4f, -2.8f, 2.8f, 0.6f, greyL); ell(0f, -1f, 2.8f, 0.6f, black)
        poly(white, 2.2f, -3f, 3.4f, -3.2f, 5.6f, -1.6f, 5.2f, -0.8f, 2.6f, -1f)
        seg(2.8f, -2.9f, 5.2f, -1.5f, 0.6f, black)
        dot(5.5f, -1.3f, black); dot(1.9f, -3.2f, white)
    }

    /** The dormouse (polh) on a branch or at a hollow at night: a grey ball of fur, huge dark eyes, a bushy tail hanging. */
    protected fun dormouse(x: Float, y: Float, size: Float = 1f, left: Boolean = false, hide: Float = 0f) {
        anchor(x, y + hide * 3f * size * detail, size, left)
        val fur = Col.hex(0xA8A29A); val light = Col.hex(0xD8D2C8); val dark = Col.hex(0x5E5A56)
        seg(-1.2f, -0.8f, -2.2f, 2.6f, 1.1f, fur); seg(-2.2f, 2.6f, -1.6f, 4f, 0.9f, fur)
        ell(0f, -1.4f, 1.8f, 1.4f, fur); ell(0.4f, -1f, 1.1f, 0.8f, light)
        ell(1.4f, -2.2f, 1.2f, 1.1f, fur)
        ell(0.8f, -3.2f, 0.6f, 0.6f, dark); ell(1.9f, -3.2f, 0.6f, 0.6f, dark)
        glow { dot(1.7f, -2.4f, Col.hex(0x0E0A0A), 1.2f); dot(1.6f, -2.6f, Col.hex(0xC8D0E0), 0.5f) }
        dot(2.6f, -2f, Col.hex(0xC08A8A))
    }

    /** A bat on the wing at ([x], [y]) (its middle), its wings beating by [flap] (0..1 round the beat). */
    protected fun bat(x: Float, y: Float, size: Float, flap: Float) {
        anchor(x, y, size)
        val col = Col.hex(0x2E2834); val rim = Col.hex(0x4E4660)
        val up = sin(flap * 6.28f)
        poly(col, -0.6f, -0.4f, -3.6f, -0.6f - up * 1.6f, -2.4f, 0.4f, -1.2f, 0.2f)
        poly(col, 0.6f, -0.4f, 3.6f, -0.6f - up * 1.6f, 2.4f, 0.4f, 1.2f, 0.2f)
        ell(0f, 0f, 0.9f, 0.8f, col)
        dot(-0.5f, -0.9f, col); dot(0.5f, -0.9f, col)
        if (up < -0.3f) { dot(-2.4f, -0.2f, rim); dot(2.4f, -0.2f, rim) }
    }

    /** A butterfly fluttering round ([x], [y]), its wings opening and closing; [kind] picks the brimstone, the peacock or a blue. */
    protected fun butterfly(x: Float, y: Float, size: Float, kind: Int) {
        anchor(x, y, size)
        val (wing, spot) = when (kind % 3) {
            0 -> Col.hex(0xF2E26A) to Col.hex(0xD8A838) // the brimstone
            1 -> Col.hex(0xB83A2E) to Col.hex(0x3A5AA8) // the peacock
            else -> Col.hex(0x7AA8E8) to Col.hex(0xF4F4FA) // a blue
        }
        val open = abs(sin(t * 11 + kind * 1.3)).toFloat()
        val wspan = 0.4f + 1.5f * open
        poly(wing, 0f, -0.2f, -wspan, -1.6f, -wspan * 0.8f, 0f)
        poly(wing, 0f, -0.2f, wspan, -1.6f, wspan * 0.8f, 0f)
        if (open > 0.5f) { dot(-wspan * 0.6f, -0.9f, spot); dot(wspan * 0.6f, -0.9f, spot) }
        seg(0f, -1f, 0f, 0.4f, 0.4f, Col.hex(0x2A2020))
    }

    /** The chamois on the scree: dark brown with a pale face and its black stripes, little hooked horns. */
    protected fun chamois(x: Float, y: Float, size: Float = 1f, left: Boolean = false, look: Boolean = false) {
        anchor(x, y, size, left)
        val coat = Col.hex(0x5E4632); val light = Col.hex(0x8A6A4A); val face = Col.hex(0xE8DCC4); val black = Col.hex(0x1C1612)
        for ((lx, s) in listOf(-3f to 0.3f, -2f to -0.3f, 2f to -0.3f, 3f to 0.3f)) seg(lx, -3.6f, lx + s, 0f, 0.8f, black)
        ell(0f, -4.6f, 3.6f, 1.8f, coat); ell(0f, -5.6f, 3f, 0.6f, light)
        val hy = if (look) -9.4f else -8.6f
        poly(coat, 2.2f, -5.8f, 3.6f, -5f, 4.6f, hy + 1f, 3.4f, hy + 0.6f)
        ell(4.4f, hy, 1.2f, 1.1f, face); seg(4f, hy - 0.8f, 5.2f, hy + 0.6f, 0.4f, black)
        dot(5.4f, hy + 0.4f, black)
        seg(3.8f, hy - 0.8f, 3.6f, hy - 3f, 0.5f, black); seg(3.6f, hy - 3f, 3f, hy - 2.6f, 0.4f, black)
        seg(4.6f, hy - 0.9f, 4.6f, hy - 3.1f, 0.5f, black); seg(4.6f, hy - 3.1f, 4f, hy - 2.8f, 0.4f, black)
    }

    /** The ibex on a crag: grey-brown, stocky, a short beard and great horns curving back in an arc, ridged. */
    protected fun ibex(x: Float, y: Float, size: Float = 1f, left: Boolean = false) {
        anchor(x, y, size, left)
        val coat = Col.hex(0x8A7A62); val light = Col.hex(0xB0A084); val dark = Col.hex(0x4E4234); val horn = Col.hex(0xB8A482); val hornD = Col.hex(0x7A6A52)
        for ((lx, s) in listOf(-3.4f to 0.2f, -2.2f to -0.2f, 2.4f to -0.2f, 3.4f to 0.2f)) seg(lx, -3.8f, lx + s, 0f, 1f, dark)
        ell(0f, -5f, 4f, 2.2f, coat); ell(0f, -6.2f, 3.4f, 0.7f, light); ell(0.2f, -3.8f, 3f, 0.6f, dark)
        poly(coat, 2.6f, -6.4f, 4.2f, -5.6f, 5.4f, -8.4f, 3.8f, -9f)
        ell(5.4f, -9f, 1.4f, 1.1f, coat); seg(5.8f, -8.2f, 5.6f, -6.8f, 0.6f, dark) // the beard
        dot(6.6f, -8.8f, dark); dot(5.2f, -9.6f, Col.hex(0x14100C))
        // the horns: an arc back over the neck, ridged
        var px = 4.8f; var py = -9.8f
        for (k in 1..7) {
            val a = 1.6f + k * 0.36f
            val nx = 3.2f + cos(a) * 3.6f; val ny = -10.2f - sin(a) * 3f
            seg(px, py, nx, ny, 1f - k * 0.08f, horn)
            dot(nx, ny, hornD, 0.5f)
            px = nx; py = ny
        }
    }

    /**
     * Two eyes shining in the dark under the trees (a bear standing at the forest's edge, just its shape): they blink
     * now and then; [gone] 0..1 closes them for good (a tap).
     */
    protected fun bearEyes(x: Float, y: Float, size: Float = 1f, gone: Float = 0f) {
        anchor(x, y, size)
        ell(0f, -4f, 5f, 4f, Col.hex(0x1A1612)); ell(-3f, -7.6f, 1.2f, 1.1f, Col.hex(0x1A1612)); ell(3f, -7.6f, 1.2f, 1.1f, Col.hex(0x1A1612))
        val blink = ((t + x * 0.01) % 4.3) < 0.18
        if (gone >= 1f || blink || env.dark < 0.3f) return
        val eye = Col.mix(Col.hex(0xFFE08A), Col.hex(0x1A1612), gone)
        glow { dot(-1.6f, -5.4f, eye, 1.2f); dot(1.6f, -5.4f, eye, 1.2f) }
    }

    /**
     * The bats of the frame over an area: [n] of them flitting on looping paths between ([x0], [y0]) and ([x1], [y1])
     * (picture px), jinking now and then. Drawn over the sky, they keep their dusky colour at night.
     */
    protected fun batsOver(x0: Float, y0: Float, x1: Float, y1: Float, n: Int, seed: Int, size: Float = 1f) {
        for (j in 0 until n) {
            val sp = 0.35 + Noise.rnd(j, seed) * 0.3
            val u = 0.5f + 0.5f * sin(t * sp + j * 2.3 + sin(t * 1.7 + j) * 0.6).toFloat()
            val v = 0.5f + 0.5f * sin(t * sp * 1.9 + j * 1.1 + cos(t * 2.3 + j * 3) * 0.8).toFloat()
            glow { bat(x0 + (x1 - x0) * u, y0 + (y1 - y0) * v, size, ((t * 5 + j * 0.37) % 1.0).toFloat()) }
        }
    }

    /** How many bats a frame shows: a cue's level ([Wildlife.bats]), else two or three while they are [out]. */
    protected fun batCount(cue: Float?, isOut: Boolean): Int = when {
        cue != null -> Wildlife.bats(cue)
        isOut -> 2 + (Noise.rnd(frame.day.toInt(), 431) * 2).toInt()
        else -> 0
    }

    /**
     * The village's church bell heard from afar: its rings coming over the land from ([x], [y]) (the bell tower out of sight),
     * again and again while [level] is up; 0 silent.
     */
    protected fun farBell(x: Float, y: Float, level: Float, seed: Int) {
        if (level <= 0.02f) return
        val d = detail
        s.fx {
            for (k in 0 until 2) {
                val u = (((t * 0.55) + k * 0.5) % 1.0).toFloat()
                PokeArt.rings(c, x, y, u, (26f + 10f * level) * d, Col.hex(0xFFF1C8), d, 0.45f * level)
            }
            // a note or two drifting up
            val ph = ((t * 0.4 + seed) % 1.0).toFloat()
            c.blend(floor(x + sin(ph * 6f) * 3f * d).toInt(), floor(y - ph * 14f * d).toInt(), Col.hex(0xFFF1C8), 0.6f * level * (1f - ph))
        }
    }

    /** Whether the evening bell (the ave) rings by itself now: a few minutes round sunset. */
    protected fun aveNow(): Boolean = s.hour in (env.sunset - 0.1f)..(env.sunset + 0.12f)

    /** Dew on the grass at dawn in the warm months: glints twinkling over the rows [y0]..[y1] (picture px). */
    protected fun dew(y0: Int, y1: Int, seed: Int) {
        if (Wildlife.part(s.hour, env.month) != Wildlife.Part.DAWN || env.snow || env.month !in 4..10 || frame.sky.rain > 0.1f) return
        val d = detail
        s.fx {
            val n = 26 * d
            for (k in 0 until n) {
                val x = (Noise.rnd(k, seed) * w).toInt(); val y = y0 + (Noise.rnd(k, seed + 1) * (y1 - y0)).toInt()
                val tw = sin(t * (1.3 + Noise.rnd(k, seed + 2) * 2) + k).toFloat()
                if (tw < 0.55f) continue
                val a = (tw - 0.55f) / 0.45f
                c.blend(x, y, Col.hex(0xF4FAFF), 0.85f * a)
                if (d > 1 && a > 0.6f) { c.blend(x - 1, y, Col.hex(0xC8E0F4), 0.5f * a); c.blend(x + 1, y, Col.hex(0xC8E0F4), 0.5f * a) }
            }
        }
    }

    protected companion object {
        /** A creature's step along a path: 0..1 eased in and out. */
        fun ease(u: Float): Float = PokeArt.ease(u)

        fun clamp(v: Float): Float = min(1f, max(0f, v))
    }
}
