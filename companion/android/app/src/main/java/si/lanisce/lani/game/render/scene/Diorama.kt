package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Env
import si.lanisce.lani.game.render.Iso
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.Season
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.Stage
import si.lanisce.lani.game.scene.Stance
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A close-up as a diorama: one place built on a floating plot of ground in the village's isometric
 * projection, over a dark vignette, as if lifted out of the map. The plot is [plotW] × [plotD] cells (+x to
 * the lower right, +y to the lower left); what stands on it reaches [rise] nominal px above the plot's back
 * corner, and the earth under its front edges is [depth] px deep.
 *
 * [K] is the picture's pixels per nominal px: 2 on the scene's canvas, the scale the people sprites are drawn
 * at, so a person is as tall against a wall in every view, and 2 × [detail] closer up (the sprites then drawn
 * [detail] times bigger, see [ArtPainter.sprite]); [fit] centres the diorama, and a canvas too short for it
 * loses the plot's front tip first. Everything is drawn through [iso] in world coordinates: sizes in pixels are
 * multiples of [K] (or of [detail]), so every detail shows the same diorama, finer.
 */
internal abstract class DioramaPainter : ArtPainter() {
    protected abstract val plotW: Float
    protected abstract val plotD: Float
    protected abstract val rise: Float
    protected open val depth: Float = 7f

    protected val iso: Iso get() = s.iso

    /** The plot's object id: shadows fall on it, everything on it is outlined against it. */
    protected var groundId = 0

    override fun stageTop(height: Int): Int = 0

    // ------------------------------------------------------------------ fitting and coordinates

    /** Canvas px per nominal px: [BASE_K] on the scene's canvas, times the [detail] closer up. */
    protected val K: Int get() = BASE_K * detail

    /** Centres the plot and what stands on it in the canvas; call first. */
    protected fun fit() {
        iso.k = K
        s.lens = Lens(K, 0, 0, w, h)
        // placed on the scene's canvas, then as much bigger as the picture is: every detail frames the same
        val bw = w / detail; val bh = h / detail
        val nomW = (plotW + plotD) * 4f
        val nomH = (plotW + plotD) * 2f + rise + depth
        val left = (bw - nomW * BASE_K) / 2f
        val spare = bh - nomH * BASE_K
        val top = if (spare >= 0f) spare * 0.45f else spare * 0.25f
        iso.ox = floor(left + plotD * 4f * BASE_K) * detail
        iso.oy = floor(top + rise * BASE_K) * detail
    }

    protected fun sx(x: Float, y: Float): Int = iso.ix(x, y)
    protected fun sy(x: Float, y: Float, z: Float = 0f): Int = iso.iy(x, y, z)

    /** Screen pixel → (x − y) and the screen height in nominal px, the two numbers every plane solve starts from. */
    @PublishedApi internal fun ua(px: Int): Float = (px + 0.5f - iso.ox) / (4f * K)
    @PublishedApi internal fun vb(py: Int): Float = (py + 0.5f - iso.oy) / K

    /** The point of the horizontal plane z = [z] under a screen pixel: x, then y. */
    protected fun flatX(px: Int, py: Int, z: Float): Float = (ua(px) + (vb(py) + z) / 2f) / 2f
    protected fun flatY(px: Int, py: Int, z: Float): Float = ((vb(py) + z) / 2f - ua(px)) / 2f

    /** The wall plane x = [x0] under a screen pixel: its y is [wallXy], its height [wallXz]. */
    @PublishedApi internal fun wallXy(px: Int, x0: Float): Float = x0 - ua(px)
    @PublishedApi internal fun wallXz(px: Int, py: Int, x0: Float): Float = (x0 + wallXy(px, x0)) * 2f - vb(py)

    /** The wall plane y = [y0] under a screen pixel: its x is [wallYx], its height [wallYz]. */
    @PublishedApi internal fun wallYx(px: Int, y0: Float): Float = ua(px) + y0
    @PublishedApi internal fun wallYz(px: Int, py: Int, y0: Float): Float = (wallYx(px, y0) + y0) * 2f - vb(py)

    /** Fills the quad of four world points, each pixel coloured by [shader] (screen x, y). */
    protected inline fun quadFill(
        x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float, x4: Float, y4: Float, z4: Float,
        crossinline shader: (Int, Int) -> Int,
    ) {
        c.polyBegin()
        c.polyAdd(iso.sx(x1, y1), iso.sy(x1, y1, z1)); c.polyAdd(iso.sx(x2, y2), iso.sy(x2, y2, z2))
        c.polyAdd(iso.sx(x3, y3), iso.sy(x3, y3, z3)); c.polyAdd(iso.sx(x4, y4), iso.sy(x4, y4, z4))
        c.polyFill(shader)
    }

    /** A person of the frame standing at world ([x], [y]) on a floor [z] px high, facing left when [flip]. */
    protected fun personAt(p: PersonInScene, x: Float, y: Float, z: Float = 0f, flip: Boolean = false, seated: Boolean = false) {
        person(p, sx(x, y), sy(x, y, z) + detail, flip, seated = seated)
    }

    /**
     * A person of the frame asleep in a bed ([ArtPainter.sleeper]): the head's middle and chin at world ([hx], [hy], [hz]) on
     * the pillow, the feet at ([fx], [fy], [fz]) along the bed, under [cover] (null: a wool blanket).
     */
    protected fun sleeperAt(p: PersonInScene, hx: Float, hy: Float, hz: Float, fx: Float, fy: Float, fz: Float, cover: IntArray? = null, pillow: Boolean = false) {
        sleeper(p, sx(hx, hy), sy(hx, hy, hz), sx(fx, fy), sy(fx, fy, fz), cover, pillow)
    }

    // ------------------------------------------------------------------ beds for the night (companion/SCENES.md, "Asleep at night")

    /** Whether anyone lies asleep in one of the art's [beds] now: the place goes quiet for them (the lamp out, the fire low). */
    protected val asleepHere: Boolean get() = beds.any { peopleAt(it).isNotEmpty() }

    /**
     * [p] asleep on a bed or mattress over x [xa]..[xb], y [ya]..[yb], its top at [top]: the head on the pillow at the head
     * end (the [xa] end, the [xb] end [far]; [ya] and [yb] [alongY]), under [cover] to the feet [foot] cells short of the
     * other end.
     */
    protected fun sleeperOn(p: PersonInScene, xa: Float, ya: Float, xb: Float, yb: Float, top: Float, alongY: Boolean, cover: IntArray?, foot: Float = 0.2f, far: Boolean = false) {
        val hz = top + HEAD_Z; val fz = top + 0.5f
        if (alongY) {
            val mx = (xa + xb) / 2f
            if (far) sleeperAt(p, mx, yb - 0.4f, hz, mx, ya + foot, fz, cover) else sleeperAt(p, mx, ya + 0.4f, hz, mx, yb - foot, fz, cover)
        } else {
            val my = (ya + yb) / 2f
            if (far) sleeperAt(p, xb - 0.4f, my, hz, xa + foot, my, fz, cover) else sleeperAt(p, xa + 0.4f, my, hz, xb - foot, my, fz, cover)
        }
    }

    /**
     * A straw mattress (slamnjača) over x [xa]..[xb], y [ya]..[yb], on [z0] (the floor, a cot's frame), [h] nominal px thick:
     * striped ticking with its seam round the sides, straws poking out of it on the side we see, a linen pillow at its head
     * (the [xa] end, the [xb] end [far]; [ya] and [yb] when it runs along y, [alongY]). A place draws it only while someone
     * lies on it.
     */
    protected fun strawMattress(xa: Float, ya: Float, xb: Float, yb: Float, alongY: Boolean, z0: Float, h: Float = MAT_H, far: Boolean = false) {
        val z1 = z0 + h
        // the long side shows bands along it, the foot's end the stripes' ends
        bedPlaneY(yb, xa, xb, z0, z1) { x, z, px -> ticking(x - xa, z - z0, 0.92f, px, end = alongY) }
        bedPlaneX(xb, ya, yb, z0, z1) { y, z, px -> ticking(y - ya, z - z0, 0.8f, px, end = !alongY) }
        bedPlaneFlat(z1, xa, ya, xb, yb) { x, y, px ->
            val edge = min(min(x - xa, xb - x), min(y - ya, yb - y))
            ticking(if (alongY) x - xa else y - ya, 2f, if (edge < 0.1f) 0.9f else 1f, px, end = true)
        }
        // straws poking out along the seam of the long side
        val d = detail
        for (k in 0 until 7) {
            val f = 0.12f + Noise.rnd(k, 97) * 0.76f
            val wx = if (alongY) xb else xa + f * (xb - xa); val wy = if (alongY) ya + f * (yb - ya) else yb
            val sx = iso.ix(wx, wy); val sy = iso.iy(wx, wy, z0 + 0.7f)
            val len = d + (Noise.rnd(k, 98) * 2 * d).toInt()
            c.line(sx, sy, if (alongY) sx + len else sx - len, sy - (Noise.rnd(k, 99) * 2 * d).toInt(), if (k % 2 == 0) Col.hex(0xE0C878) else Col.hex(0xC8A858))
        }
        when {
            alongY && far -> pillowBox(xa + 0.12f, yb - 0.7f, xb - 0.12f, yb - 0.1f, z1)
            alongY -> pillowBox(xa + 0.12f, ya + 0.1f, xb - 0.12f, ya + 0.7f, z1)
            far -> pillowBox(xb - 0.7f, ya + 0.12f, xb - 0.1f, yb - 0.12f, z1)
            else -> pillowBox(xa + 0.1f, ya + 0.12f, xa + 0.7f, yb - 0.12f, z1)
        }
    }

    /** A linen pillow over x [xa]..[xb], y [ya]..[yb], lying on [z]. */
    protected fun pillowBox(xa: Float, ya: Float, xb: Float, yb: Float, z: Float) =
        iso.box(xa, ya, z, xb - xa, yb - ya, 1.1f, BED_LINEN, Col.hex(0xE6DCC6), Col.hex(0xC4B89E))

    /** A mattress's ticking at [u] cells across its stripes (along it: [end] the foot's face, else the side's), [h] nominal px up; [dim] shades it. */
    protected fun ticking(u: Float, h: Float, dim: Float, px: Int, end: Boolean): Int {
        val stripe = if (end) Math.floorMod(floor(u / 0.1f).toInt(), 4) == 0 else Math.floorMod(floor(h / 0.45f).toInt(), 3) == 0
        val seam = abs(h - 0.7f) < 0.12f
        val col = when {
            seam -> Col.hex(0xB4A888)
            stripe -> Col.hex(0x7A8CA8)
            detail > 1 && px % 2 == 0 -> Col.hex(0xE2D8C0)
            else -> Col.hex(0xE8DFC8)
        }
        return Col.scale(col, dim)
    }

    /** The bedding's plane y = [yw] over x [xa]..[xb], z [za]..[zb]; [shader] (x, z, px) gives a colour, or 0 to leave the pixel. */
    private inline fun bedPlaneY(yw: Float, xa: Float, xb: Float, za: Float, zb: Float, crossinline shader: (Float, Float, Int) -> Int) {
        c.polyBegin()
        c.polyAdd(iso.sx(xa, yw), iso.sy(xa, yw, za)); c.polyAdd(iso.sx(xb, yw), iso.sy(xb, yw, za))
        c.polyAdd(iso.sx(xb, yw), iso.sy(xb, yw, zb)); c.polyAdd(iso.sx(xa, yw), iso.sy(xa, yw, zb))
        c.polyScan { px, py -> val col = shader(wallYx(px, yw), wallYz(px, py, yw), px); if (col != 0) c.set(px, py, col) }
    }

    /** The bedding's plane x = [xw] over y [ya]..[yb], z [za]..[zb]; [shader] (y, z, px) gives a colour, or 0. */
    private inline fun bedPlaneX(xw: Float, ya: Float, yb: Float, za: Float, zb: Float, crossinline shader: (Float, Float, Int) -> Int) {
        c.polyBegin()
        c.polyAdd(iso.sx(xw, ya), iso.sy(xw, ya, za)); c.polyAdd(iso.sx(xw, yb), iso.sy(xw, yb, za))
        c.polyAdd(iso.sx(xw, yb), iso.sy(xw, yb, zb)); c.polyAdd(iso.sx(xw, ya), iso.sy(xw, ya, zb))
        c.polyScan { px, py -> val col = shader(wallXy(px, xw), wallXz(px, py, xw), px); if (col != 0) c.set(px, py, col) }
    }

    /** The bedding's level z = [z] over x [xa]..[xb], y [ya]..[yb]; [shader] (x, y, px) gives a colour, or 0. */
    private inline fun bedPlaneFlat(z: Float, xa: Float, ya: Float, xb: Float, yb: Float, crossinline shader: (Float, Float, Int) -> Int) {
        c.polyBegin()
        c.polyAdd(iso.sx(xa, ya), iso.sy(xa, ya, z)); c.polyAdd(iso.sx(xb, ya), iso.sy(xb, ya, z))
        c.polyAdd(iso.sx(xb, yb), iso.sy(xb, yb, z)); c.polyAdd(iso.sx(xa, yb), iso.sy(xa, yb, z))
        c.polyScan { px, py -> val col = shader(flatX(px, py, z), flatY(px, py, z), px); if (col != 0) c.set(px, py, col) }
    }

    // ------------------------------------------------------------------ the stage (companion/SCENES.md, "Stage directions")

    /**
     * Where someone is at a spot of this art's stage ([si.lanisce.lani.game.scene.SceneArt.stage]): standing at world
     * ([x], [y]) on a floor [z] nominal px high (sitting: the seat), facing left when [flip]. At a hiding place it is where
     * they stand when they come out (where they hide is the painter's, [hiding]). [layer] is when in the painting order
     * they are drawn ([cast]), the spot's name unless it shares another's; [hidden] when they are drawn hiding or peeking
     * there (before what hides them), the same unless it says.
     */
    internal class Mark(val x: Float, val y: Float, val z: Float = 0f, val flip: Boolean = false, val layer: String? = null, val hidden: String? = null)

    /** The spots of the stage, by name: where people are at each. None: the art has no stage, people stand at their person spots. */
    protected open val marks: Map<String, Mark> = emptyMap()

    /** When in the painting order someone at [spot] in [stance] is drawn. */
    private fun layerOf(spot: String, stance: Stance): String {
        val m = marks[spot] ?: return spot
        val own = m.layer ?: spot
        return if (stance == Stance.HIDE || stance == Stance.PEEK) m.hidden ?: own else own
    }

    /** Whether the people at [spot] in any of [stances] are drawn somewhere in the painting order now: the art shows it. */
    protected fun at(spot: String, vararg stances: Stance): Boolean = frame.people.any { p ->
        p.pose != Pose.SLEEP && (p.slot == spot && p.stance in stances || p.from == spot && p.fromStance in stances && p.moveAge < Stage.MOVE_S)
    }

    /** A shadow at the feet of someone standing at world ([x], [y]) (a room's floor has one); none by default. */
    protected open fun footprint(x: Float, y: Float) {}

    /**
     * Draws the people of the frame whose place in the painting order is [layer] (a spot's, [Mark.layer], or where they hide
     * there, [Mark.hidden]), each as the stage has them: at their spot in their [Stance], or on their way there
     * ([PersonInScene.from]): out of hiding first with a hop and a puff where they hid ([POP_S]), then a walk from spot to
     * spot ([walkS], the walking look, turned the way they go; drawn at the first spot's layer for the first half, the
     * second's after), or a dash into hiding (a puff where they stood, [SLIP_S]: nobody sees where they went), the hiding
     * place showing its clue once they are gone. Someone asleep is the painter's (in a bed); someone at a spot the stage
     * lacks is not drawn here.
     */
    protected fun cast(layer: String) {
        for (p in frame.people) {
            if (p.pose == Pose.SLEEP) continue
            val to = marks[p.slot] ?: continue
            val a = p.from
            val from = a?.let { marks[it] }
            if (a == null || from == null) { if (layerOf(p.slot, p.stance) == layer) settle(p, p.slot, to, p.stance); continue }
            move(p, a, from, to, layer)
        }
    }

    private fun move(p: PersonInScene, a: String, ma: Mark, mb: Mark, layer: String) {
        val b = p.slot
        var age = p.moveAge
        var was = p.fromStance
        val stays = a == b && (p.stance == Stance.HIDE || p.stance == Stance.PEEK)
        // out of hiding first: they spring up out of it, where they hid
        if (was == Stance.HIDE && !stays) {
            if (age < POP_S) { if (layerOf(a, Stance.STAND) == layer) popOut(p, a, ma, age / POP_S.toFloat()); return }
            age -= POP_S.toFloat(); was = Stance.STAND
        }
        // into hiding elsewhere: a puff where they stood, and they are gone (a peek drawn back in is just hidden)
        if (p.stance == Stance.HIDE && !(a == b && was == Stance.PEEK)) {
            val u = age / SLIP_S.toFloat()
            if (u < 1f && layerOf(a, was) == layer) slip(p, a, ma, was, u)
            if (u >= SLIP_GONE && layerOf(b, Stance.HIDE) == layer) settle(p, b, mb, Stance.HIDE)
            return
        }
        if (a == b) { if (layerOf(b, p.stance) == layer) settle(p, b, mb, p.stance); return }
        // a walk from spot to spot, standing up first, into the pose there at the end
        val u = age / walkS(hypot(mb.x - ma.x, mb.y - ma.y))
        if (u >= 1f) { if (layerOf(b, p.stance) == layer) settle(p, b, mb, p.stance); return }
        if (layerOf(if (u < 0.5f) a else b, Stance.STAND) != layer) return
        val x = ma.x + (mb.x - ma.x) * u; val y = ma.y + (mb.y - ma.y) * u; val z = ma.z + (mb.z - ma.z) * u
        footprint(x, y)
        val left = iso.sx(mb.x, mb.y) < iso.sx(ma.x, ma.y)
        person(p.copy(stance = Stance.STAND), sx(x, y), sy(x, y, z) + detail, left, walking = true)
    }

    /** Springing out of hiding at [spot], [u] of the way: standing there with a hop, a puff of dust round the feet. */
    private fun popOut(p: PersonInScene, spot: String, m: Mark, u: Float) {
        settle(p, spot, m, Stance.STAND, lift = (sin(u * PI.toFloat()) * 4f).toInt())
        puffAt(m, u)
    }

    /** Dashing off into hiding from [spot], [u] of the way: still there a moment, then only a puff of dust where they stood. */
    private fun slip(p: PersonInScene, spot: String, m: Mark, was: Stance, u: Float) {
        if (u < SLIP_GONE * 0.6f) settle(p, spot, m, if (was == Stance.HIDE) Stance.STAND else was)
        puffAt(m, u)
    }

    private fun puffAt(m: Mark, u: Float) {
        val cx = iso.sx(m.x, m.y); val cy = iso.sy(m.x, m.y, m.z)
        s.fx { PokeArt.puff(c, cx, cy - 4f * K, u, 4.5f * K, 6, DUST, 17, 0.7f) }
    }

    /**
     * [p] at [spot] ([m]) in [stance], [lift] sprite px up (a hop): standing, sitting on the seat there, crouching,
     * lying on the floor, or hiding ([hiding]: the painter's own for its hiding places).
     */
    protected open fun settle(p: PersonInScene, spot: String, m: Mark, stance: Stance, lift: Int = 0) {
        val q = if (p.stance == stance) p else p.copy(stance = stance)
        val bx = sx(m.x, m.y); val by = sy(m.x, m.y, m.z) + detail - lift * detail
        when (stance) {
            Stance.STAND -> { footprint(m.x, m.y); person(q, bx, by, m.flip) }
            Stance.SIT -> person(q, bx, by, m.flip, seated = true)
            Stance.CROUCH -> { footprint(m.x, m.y); person(q, bx, by, m.flip, crouch = true) }
            Stance.LIE -> person(q.copy(pose = Pose.SLEEP), bx, by, m.flip)
            Stance.HIDE, Stance.PEEK -> hiding(q, spot, m, stance == Stance.PEEK)
        }
    }

    /**
     * [p] hidden at [spot] (only a clue of them shows, and they can't be tapped: draw it with [figure]) or peeking out of it
     * ([peek]: a part of them, tappable). By default the hidden show nothing and the peeking stand there.
     */
    protected open fun hiding(p: PersonInScene, spot: String, m: Mark, peek: Boolean) {
        if (peek) person(p, sx(m.x, m.y), sy(m.x, m.y, m.z) + detail, m.flip)
    }

    /**
     * [p]'s sprite with the feet at picture ([bx], [by]), outlined like a person but not registered: what shows of someone
     * hidden (a braid past the door, the shoes under the table) can't be tapped for their card.
     */
    protected fun figure(p: PersonInScene, bx: Int, by: Int, flip: Boolean, crouch: Boolean = false) {
        prop(Pal.OUTLINE) { sprite(bx, by) { people.draw(c, p.art, bx, by, t, Noise.hash(p.id.hashCode(), 7) ushr 1, false, flip, false, false, p.pose, crouch = crouch) } }
    }

    override fun spotAt(x: Float, y: Float, slop: Float): String? {
        if (marks.isEmpty() || !rendered) return null
        val d = detail.toFloat()
        var best: String? = null; var bestD = Float.MAX_VALUE
        for ((name, m) in marks) {
            // where someone stands there, as tall as a grown-up: its lower middle at the mark; the nearest middle wins
            val cx = iso.sx(m.x, m.y) / d; val by = iso.sy(m.x, m.y, m.z) / d
            val dx = abs(x - cx); val top = by - 30f
            if (dx > 8f + slop || y < top - slop || y > by + 3f + slop) continue
            val dd = hypot(dx, y - (by - 11f))
            if (dd < bestD) { bestD = dd; best = name }
        }
        return best
    }

    // ------------------------------------------------------------------ the room it floats in

    /** The vignette's middle and edge: warm by day, deep blue at night, a cold grey under a grey sky ([gloom]). */
    private fun roomColors(e: Env, gloom: Float): Pair<Int, Int> {
        val day = 1f - e.dark
        var center = Col.mix(Col.hex(0x1C2135), Col.hex(0x3A2F26), day)
        var edge = Col.mix(Col.hex(0x07080F), Col.hex(0x140F0B), day)
        if (gloom > 0f) {
            center = Col.mix(center, Col.hex(0x242A33), gloom * 0.7f)
            edge = Col.mix(edge, Col.hex(0x090B10), gloom * 0.7f)
        }
        return center to edge
    }

    override fun backdrop(frame: SceneFrame): Int =
        roomColors(Env(frame.hour, frame.month, false, frame.sky.gloom * gloomShare), frame.sky.gloom).second

    /** A room keeps some of its light under a grey sky. */
    override val gloomShare: Float = 0.5f

    /** A radial vignette in dithered bands behind everything, warm by day and deep blue at night; the light pass leaves it. */
    protected fun vignette() {
        val (center, edge) = roomColors(env, frame.sky.gloom)
        val cx = w / 2f; val cy = iso.oy + (plotW + plotD) * K * 0.8f
        val maxR = hypot(max(cx, w - cx), max(cy, h - cy))
        val bands = 8f
        c.penEmissive = true
        for (y in max(0, c.top) until min(h, c.bottom)) {
            val dy = (y + 0.5f - cy) * 1.35f
            for (x in max(0, c.left) until min(w, c.right)) {
                val d = (hypot(x + 0.5f - cx, dy) / maxR).coerceIn(0f, 1f)
                val q = floor(d * bands + Dither.at(x, y) - 0.5f) / bands
                c.set(x, y, Col.mix(center, edge, q.coerceIn(0f, 1f)))
            }
        }
        c.penEmissive = false
    }

    // ------------------------------------------------------------------ the plot of ground

    protected val grass: IntArray get() = env.grass
    protected val winter: Boolean get() = env.season == Season.WINTER

    /**
     * The plot: its top coloured by [ground] (world x, y and the screen pixel → a colour), the earth under its two
     * front edges with a grass lip, stones and roots, and a lighter rim. Registers [groundId].
     */
    protected inline fun plot(crossinline ground: (Float, Float, Int, Int) -> Int) {
        groundId = s.newObject(Col.hex(0x120C08))
        val pw = plotW; val pd = plotD; val dp = depth
        // the earth: the front-left face (y = D) and the front-right one (x = W), darker
        quadFill(0f, pd, 0f, pw, pd, 0f, pw, pd, -dp, 0f, pd, -dp) { px, py ->
            earth(px, py, wallYx(px, pd), -wallYz(px, py, pd) / dp, 1f)
        }
        quadFill(pw, 0f, 0f, pw, pd, 0f, pw, pd, -dp, pw, 0f, -dp) { px, py ->
            earth(px, py, wallXy(px, pw) + 31f, -wallXz(px, py, pw) / dp, 0.78f)
        }
        // the top
        quadFill(0f, 0f, 0f, pw, 0f, 0f, pw, pd, 0f, 0f, pd, 0f) { px, py ->
            val x = flatX(px, py, 0f); val y = flatY(px, py, 0f)
            rim(ground(x, y, px, py), x, y, px, py)
        }
        c.penId = 0
    }

    /** The plot's edges fall away a little darker; the two front ones catch a thin light rim. */
    @PublishedApi internal fun rim(col: Int, x: Float, y: Float, px: Int, py: Int): Int {
        val edge = min(min(x, y), min(plotW - x, plotD - y))
        return when {
            plotW - x < 0.09f || plotD - y < 0.09f -> Col.mix(col, Col.hex(0xFFF4D0), 0.18f)
            edge < 0.35f && Dither.at(px, py) < (0.35f - edge) * 2.2f -> Col.scale(col, 0.84f)
            else -> col
        }
    }

    /** The earth side at [zf] (0 at the grass, 1 at the bottom) along the edge at [along]; [light] dims the right face. */
    @PublishedApi internal fun earth(px: Int, py: Int, along: Float, zf: Float, light: Float): Int {
        val lipDepth = 0.2f + Noise.v1(along * 3.1f, 11) * 0.22f
        if (zf < lipDepth) {
            val lip = if (winter) Pal.SNOW_M else grass[2]
            return Col.scale(if (zf < 0.08f) grass[1] else lip, light)
        }
        val n = Noise.rnd(px, py, 91)
        val base = when {
            n < 0.03f -> Pal.STONE_M
            n < 0.05f -> Pal.STONE_D
            else -> {
                val band = zf + (Noise.v2(along * 2.2f, zf * 3f, 5) - 0.5f) * 0.35f
                when {
                    band < 0.5f -> if (Dither.at(px, py) < 0.5f) Col.hex(0x6E4C30) else Col.hex(0x5E4029)
                    band < 0.8f -> Col.hex(0x533823)
                    else -> if (Dither.at(px, py) < 0.6f) Col.hex(0x3E2A1A) else Col.hex(0x533823)
                }
            }
        }
        // a root now and then
        val root = Noise.v1(along * 7f, 29)
        val col = if (root > 0.86f && zf < 0.75f && abs(Noise.v1(zf * 9f + along * 5f, 31) - 0.5f) < 0.05f) Col.hex(0x8A6A48) else base
        return Col.scale(col, light)
    }

    /** Grass at world ([wx], [wy]): soft patches, dark blades, a light glint; snow in winter. */
    protected fun grassAt(wx: Float, wy: Float, px: Int, py: Int): Int {
        val g = grass
        val patch = Noise.v2(wx * 0.8f, wy * 0.8f, 17)
        val fine = Noise.rnd(px, py, 23)
        if (fine < 0.06f) return g[2]
        if (fine > 0.975f) return g[3]
        val v = patch + (Dither.at(px, py) - 0.5f) * 0.2f
        return when {
            v > 0.68f -> g[3]
            v > 0.45f -> g[0]
            v > 0.26f -> g[1]
            else -> g[2]
        }
    }

    /** Trampled earth over [grassCol], [f] 0 (grass) to 1 (bare), with a ragged dithered border. */
    protected fun dirtAt(px: Int, py: Int, f: Float, grassCol: Int): Int {
        if (f <= 0f) return grassCol
        val n = Noise.rnd(px, py, 37)
        val earth = if (winter) (if (n < 0.5f) Pal.SNOW_M else Pal.SNOW_D) else if (n < 0.25f) Pal.DIRT_D else if (n < 0.85f) Pal.DIRT_M else Pal.DIRT_L
        return if (f + (Dither.at(px, py) - 0.5f) * 0.6f > 0.5f) earth else grassCol
    }

    /** Darkens the pixels of objects [id0]..[id1] (the ground, a floor) in an ellipse around ([cx], [cy]) by [f]. */
    protected fun shade(cx: Float, cy: Float, rx: Float, ry: Float, f: Float, id0: Int, id1: Int = id0) {
        val ids = c.ids; val px = c.pixels; val em = c.emissive
        for (y in max(c.top, (cy - ry).toInt())..min(c.bottom - 1, (cy + ry).toInt())) for (x in max(c.left, (cx - rx).toInt())..min(c.right - 1, (cx + rx).toInt())) {
            val dx = (x + 0.5f - cx) / rx; val dy = (y + 0.5f - cy) / ry
            if (dx * dx + dy * dy > 1f) continue
            val i = c.index(x, y)
            if (ids[i] < id0 || ids[i] > id1 || em[i]) continue
            if (dx * dx + dy * dy > 0.6f && Dither.at(x, y) < 0.5f) continue
            px[i] = Col.scale(px[i], f)
        }
    }

    /** Tufts of grass and small flowers over the plot, not where [keepOut] (world x, y) says. */
    protected fun tufts(n: Int, seed: Int, keepOut: (Float, Float) -> Boolean) {
        val g = grass
        c.penId = groundId
        for (i in 0 until n) {
            val x = 0.3f + Noise.rnd(i, seed) * (plotW - 0.6f)
            val y = 0.3f + Noise.rnd(i, seed + 1) * (plotD - 0.6f)
            if (keepOut(x, y)) continue
            val bx = sx(x, y); val by = sy(x, y)
            val kind = Noise.rnd(i, seed + 2)
            sprite(bx, by) { when {
                winter -> { c.set(bx, by, Pal.SNOW_D); c.set(bx + 1, by - 1, Pal.STONE_D) }
                kind < 0.15f && env.season != Season.AUTUMN -> {
                    val petal = if (kind < 0.05f) Col.hex(0xF4EEDD) else if (kind < 0.1f) Col.hex(0xF0C23A) else Col.hex(0xB08AD8)
                    c.set(bx, by, g[2]); c.set(bx, by - 1, g[2])
                    c.set(bx - 1, by - 2, petal); c.set(bx + 1, by - 2, petal); c.set(bx, by - 3, petal)
                    c.set(bx, by - 2, Col.hex(0xFFE36A))
                }
                kind < 0.3f && env.season == Season.AUTUMN -> {
                    val leaf = if (kind < 0.2f) Col.hex(0xC9702A) else Col.hex(0xE0A23A)
                    c.set(bx, by, leaf); c.set(bx + 1, by, leaf); c.set(bx, by - 1, Col.scale(leaf, 0.8f))
                }
                else -> {
                    c.set(bx, by, g[2]); c.set(bx - 1, by - 1, g[2]); c.set(bx - 2, by - 2, g[1])
                    c.set(bx + 1, by - 1, g[2]); c.set(bx + 1, by - 2, g[3]); c.set(bx, by - 2, g[1]); c.set(bx, by - 3, g[3])
                }
            } }
        }
        c.penId = 0
    }

    // ------------------------------------------------------------------ building blocks

    /** A plank floor over x0..x1, y0..y1 at height [z], boards [width] cells wide running along y. */
    protected fun planks(x0: Float, y0: Float, x1: Float, y1: Float, z: Float, width: Float = 0.55f) {
        val tones = intArrayOf(Col.hex(0xA46C3E), Col.hex(0x93603A), Col.hex(0xAF7747), Col.hex(0x8B5932), Col.hex(0x9C673C))
        val seam = Col.hex(0x4E3019); val joint = Col.hex(0x5E3A1F)
        val seamW = 1f / (width * 8f * K)
        quadFill(x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z) { px, py ->
            val wx = flatX(px, py, z); val wy = flatY(px, py, z)
            val u = (wx - x0) / width
            val board = floor(u).toInt()
            val f = u - board
            val base = tones[Math.floorMod(Noise.hash(board, 5), tones.size)]
            val v = (wy - y0) / 2.6f + Noise.rnd(board, 9) * 3f
            val fy = v - floor(v)
            when {
                f < seamW -> seam
                fy < 0.035f -> joint
                Noise.v2(wy * 5f, board * 3.7f, 3) > 0.74f -> Col.scale(base, 0.9f)
                Noise.rnd(px, py, 41) > 0.975f -> Col.mix(base, Col.hex(0xE0B07A), 0.35f)
                else -> base
            }
        }
    }

    /** A stone at world ([x], [y]), [r] cells across, lit from the upper left. */
    protected fun stone(x: Float, y: Float, r: Float, seed: Int) {
        val cx = iso.sx(x, y); val cy = iso.sy(x, y, 0f)
        val rx = r * 4f * K; val ry = r * 2.8f * K
        val warm = Noise.rnd(seed, 1) < 0.5f
        val l = if (warm) Pal.STONE_L else Col.hex(0xA8A296)
        val m = if (warm) Pal.STONE_M else Col.hex(0x88826F)
        val d = if (warm) Pal.STONE_D else Col.hex(0x66615A)
        c.fillEllipse(cx, cy - ry * 0.45f, rx, ry, d)
        c.fillEllipse(cx - rx * 0.12f, cy - ry * 0.6f, rx * 0.85f, ry * 0.8f, m)
        c.fillEllipse(cx - rx * 0.35f, cy - ry * 0.85f, rx * 0.4f, ry * 0.35f, l)
        if (winter) c.fillEllipse(cx - rx * 0.1f, cy - ry * 1.05f, rx * 0.6f, ry * 0.35f, Pal.SNOW_L)
    }

    /** The stones of a fire ring at world ([x], [y]), [r] cells across: the back half, or the front half. */
    protected fun ring(x: Float, y: Float, r: Float, seed: Int, front: Boolean) {
        val n = 11
        for (i in 0 until n) {
            val a = (i + 0.5f) / n * 2f * PI.toFloat()
            if ((sin(a) + cos(a) > 0f) != front) continue
            stone(x + cos(a) * r, y + sin(a) * r, 0.42f + Noise.rnd(i, seed) * 0.14f, seed + i)
        }
    }

    /** Ashes and three crossed logs in the middle of a fire ring. */
    protected fun logs(x: Float, y: Float, r: Float) {
        val cx = iso.sx(x, y); val cy = iso.sy(x, y, 0f)
        c.fillEllipse(cx, cy - K, r * 3.2f * K, r * 1.5f * K, Col.hex(0x3A2E28))
        c.fillEllipse(cx, cy - K, r * 2.2f * K, r * 1f * K, Col.hex(0x5A4438))
        for (j in 0 until 3) {
            val a = j * 2.1f + 0.4f
            val ex = cos(a) * r * 3f * K; val ey = sin(a) * r * 1.4f * K
            for (tk in 0..K) c.line((cx - ex * 0.9f).toInt(), (cy - ey * 0.9f).toInt() - tk, (cx + ex * 0.2f).toInt(), (cy + ey * 0.2f).toInt() - K * 3 - tk, if (tk == 0) Pal.LOG_D else Pal.LOG_M)
            c.set((cx - ex * 0.9f).toInt(), (cy - ey * 0.9f).toInt() - 1, Pal.LOG_L)
        }
    }

    /** Flickering flames (emissive) at world ([x], [y]) over a ring [r] cells across, with sparks, smoke and the fire's light. */
    protected fun flames(x: Float, y: Float, r: Float, seed: Int) {
        val cx = iso.sx(x, y); val cy = iso.sy(x, y, 0f)
        glow {
            val base = cy - 2f * K
            val flick = sin(t * 11 + seed).toFloat() * 0.5f + sin(t * 7.3 + seed * 2).toFloat() * 0.5f
            val hgt = (10f + flick * 2f) * K
            for (tongue in 0..4) {
                val off = (tongue - 2) * 1.5f * K
                val th = hgt * (1f - abs(tongue - 2) * 0.24f) * (0.78f + 0.22f * sin(t * (9 + tongue) + tongue * 1.7).toFloat())
                val sway = sin(t * 6 + tongue).toFloat() * 0.8f * K
                c.fillEllipse(cx + off + sway * 0.5f, base - th * 0.42f, 1.8f * K, th * 0.5f, Pal.FLAME[3])
                c.fillEllipse(cx + off * 0.8f + sway * 0.6f, base - th * 0.34f, 1.2f * K, th * 0.36f, Pal.FLAME[2])
            }
            c.fillEllipse(cx, base - hgt * 0.22f, 1.7f * K, hgt * 0.26f, Pal.FLAME[1])
            c.fillEllipse(cx, base - hgt * 0.1f, 1.1f * K, hgt * 0.12f, Pal.FLAME[0])
            for (e in 0 until 6) {
                val ex = (cx + (Noise.rnd(e, seed + 5) - 0.5f) * r * 5f * K).toInt(); val ey = (cy - Noise.rnd(e, seed + 6) * K * 1.5f).toInt()
                if (sin(t * 3 + e) > -0.3) c.set(ex, ey, Pal.FLAME[if (e % 2 == 0) 2 else 3])
            }
        }
        s.sparks(cx, cy - 11f * K, 3, seed)
        s.smoke(cx, cy - 16f * K, 0.9f, seed)
        s.light(cx, cy - 6f * K, 40f, 0.95f)
    }

    /** A rope from world ([x1], [y1], [z1]) to ([x2], [y2], [z2]) sagging by [sag] px, in [col]. Draw it inside a [prop] with no outline. */
    protected fun rope(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, col: Int = Col.hex(0xD8C49A), sag: Float = 1.2f) {
        val steps = 14
        var px = iso.ix(x1, y1); var py = iso.iy(x1, y1, z1)
        for (i in 1..steps) {
            val tt = i / steps.toFloat()
            val x = x1 + (x2 - x1) * tt; val y = y1 + (y2 - y1) * tt
            val z = z1 + (z2 - z1) * tt - sag * 4f * tt * (1 - tt)
            val nx = iso.ix(x, y); val ny = iso.iy(x, y, z)
            c.line(px, py, nx, ny, col)
            px = nx; py = ny
        }
    }

    /** A wooden post 3 × [detail] px thick from [z0] to [z1] at world ([x], [y]): a lit left edge, a dark right one. */
    protected fun post(x: Float, y: Float, z0: Float, z1: Float, l: Int = Pal.WOOD_L, m: Int = Pal.WOOD_M, d: Int = Pal.WOOD_D) {
        val pw = 3 * detail
        val px = iso.ix(x, y) - pw / 2
        val top = iso.iy(x, y, z1); val bot = iso.iy(x, y, z0)
        for (dx in 0 until pw) c.vline(px + dx, top, bot, when (dx / detail) { 0 -> l; 2 -> d; else -> m })
        c.hline(px, px + pw - 1, top, Col.mix(l, Col.hex(0xFFFFFF), 0.2f))
    }

    /** A tent stake at world ([x], [y]): a short peg leaning out. */
    protected fun stake(x: Float, y: Float) {
        val bx = iso.ix(x, y); val by = iso.iy(x, y, 0f)
        val d = detail
        for (o in 0 until d) c.line(bx + o, by, bx + K + o, by - 3 * K, Pal.WOOD_M)
        for (o in 0 until d) c.line(bx + d + o, by, bx + K + d + o, by - 3 * K, Pal.WOOD_D)
        c.fillRect(bx + K, by - 3 * K, d, d, Pal.WOOD_L)
    }

    // ------------------------------------------------------------------ weather

    /** Whether what object [id] drew is out under the sky: the vignette around the diorama and the plot's ground. */
    protected open fun outdoors(id: Int): Boolean = id == 0 || id == groundId

    /**
     * Whether the plot's ground at world ([x], [y]) lies under a roof, a tent's canvas or a bee house's eaves: no rain
     * falls on it and no drop splashes there, no snow, no mist. None by default.
     */
    protected open fun sheltered(x: Float, y: Float): Boolean = false

    /**
     * Whether picture pixel ([px], [py]) of this frame looks into a place under a roof, whatever is drawn there: through
     * a cutaway, as into the tent past its open front. The weather keeps off it, as off the ground [sheltered] by the
     * roof. None by default.
     */
    internal open fun indoors(px: Int, py: Int): Boolean = false

    /** Where the weather keeps off: the view into a place under a roof ([indoors]), the ground under one ([sheltered]). */
    private fun covered(px: Int, py: Int): Boolean =
        indoors(px, py) || (c.ids[c.index(px, py)] == groundId && sheltered(flatX(px, py, 0f), flatY(px, py, 0f)))

    /**
     * The weather around the diorama: rain, snow and fog in the dark around it and over its open ground (never in a
     * room, nor under a roof: [indoors], [sheltered]), splashes on the grass, a far flash lighting up the dark. What a
     * place shows of it itself (a tent's wet canvas and the drips off its eave) it paints with the rest.
     */
    override fun weather() {
        val sky = frame.sky
        val wx = Weather(c, w, h, detail, t, env, sky, cover = { x, y -> covered(x, y) }, open = { outdoors(it) })
        wx.fog(sky.fog) { 0.8f }
        wx.rain()
        plotSplashes(sky.rain)
        wx.snow()
        wx.lighten(wx.flash(), 0.3f, 0.08f)
    }

    /** Rain splashing on the plot's open ground, never under a roof ([sheltered]): a crown, then a ring, here and there. */
    private fun plotSplashes(level: Float) {
        if (level <= 0.05f) return
        val col = Col.mix(env.lit(SPLASH), SPLASH, 0.25f)
        val d = detail
        val n = (plotW * plotD * 0.3f * level).toInt()
        for (j in 0 until n) {
            val cycle = t * 2.4 + Noise.rnd(j, 64)
            val ph = (cycle % 1.0).toFloat()
            if (ph > 0.3f) continue
            val k = cycle.toInt()
            val x = 0.3f + Noise.rnd(j, 65, k) * (plotW - 0.6f); val y = 0.3f + Noise.rnd(j, 66, k) * (plotD - 0.6f)
            if (sheltered(x, y)) continue
            val px = iso.ix(x, y); val py = iso.iy(x, y, 0f)
            if (!c.inside(px, py) || c.ids[c.index(px, py)] != groundId) continue
            if (ph < 0.12f) {
                c.blend(px - d, py - d, col, 0.7f); c.blend(px + d, py - d, col, 0.7f); c.blend(px, py - 2 * d, col, 0.5f)
            } else {
                for (o in -d..d) c.blend(px + o, py, col, 0.55f)
            }
        }
    }

    companion object {
        private val SPLASH = Col.hex(0xC4D2E6)

        /** Canvas px per nominal px on the scene's canvas: the scale the people sprites are made for. */
        const val BASE_K = 2

        /** Springing out of hiding, and dashing into it (seconds); how far into the dash they are gone. */
        const val POP_S = 0.45
        const val SLIP_S = 0.55
        private const val SLIP_GONE = 0.45f
        private val DUST = Col.hex(0xE8DCC4)

        /** How long a walk of [cells] takes, in seconds: a short one brisk, none longer than 1.9 s (all of a move within [Stage.MOVE_S]). */
        fun walkS(cells: Float): Float = (cells / 2.2f).coerceIn(0.7f, 1.9f)

        /** How thick a straw mattress on the floor is (nominal px), and how far over a bed's top a sleeper's chin lies. */
        const val MAT_H = 1.6f
        const val HEAD_Z = 1.2f

        /** A pillow's linen. */
        val BED_LINEN = Col.hex(0xFAF4EA)
    }
}
