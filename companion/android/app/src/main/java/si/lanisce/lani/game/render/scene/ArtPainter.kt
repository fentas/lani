package si.lanisce.lani.game.render.scene

import si.lanisce.lani.game.render.Backdrop
import si.lanisce.lani.game.render.Col
import si.lanisce.lani.game.render.Dither
import si.lanisce.lani.game.render.Env
import si.lanisce.lani.game.render.Lens
import si.lanisce.lani.game.render.Moon
import si.lanisce.lani.game.render.NightSky
import si.lanisce.lani.game.render.Noise
import si.lanisce.lani.game.render.SkyView
import si.lanisce.lani.game.render.skyClear
import si.lanisce.lani.game.sky.SkyTap
import si.lanisce.lani.game.render.Pal
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.render.SceneCtx
import si.lanisce.lani.game.scene.PersonInScene
import si.lanisce.lani.game.scene.Poked
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.scene.SceneArt
import si.lanisce.lani.game.scene.SceneFixtures
import si.lanisce.lani.game.scene.SceneFrame
import si.lanisce.lani.game.scene.SceneHit
import si.lanisce.lani.game.scene.ScenePainter
import si.lanisce.lani.game.scene.SceneTarget
import si.lanisce.lani.game.scene.Sleep
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What the four scene painters share: the per-canvas drawing context of the village renderer (object ids,
 * outlines, lights, effects), the things and people registered while painting, their hit rectangles (the
 * bounding box of the pixels each one actually drew, so a hit is exactly the visible thing), what reacts to a tap
 * ([pokes]: a thing's pixels, or scenery drawn with [pokeable]), the outline, light and highlight passes, and the
 * nominal 240×160 stage centred in a bigger canvas.
 *
 * A scene draws in stage coordinates offset by ([ox], [oy]); the sky, walls or floor fill the rest.
 *
 * Closer up it is drawn finer ([detail] 2 or 3, see [ScenePainter.render]): the same composition in a picture
 * [detail] times as big ([w] × [h]), of which the canvas may hold just a window. A painter always draws the whole
 * picture in its coordinates (the canvas clips; loops over big areas should keep to [PixelCanvas.left] ..
 * [PixelCanvas.right] and [PixelCanvas.top] .. [PixelCanvas.bottom]): sizes in pixels grow with [detail], so
 * the scene keeps its layout, and what is drawn in world units (planes, ground, shapes) comes out finer. People
 * are the same sprites, [detail] × [detail] pixels per sprite pixel. Hits are always in the scene canvas's pixels.
 */
internal abstract class ArtPainter : ScenePainter {
    private var ctx: SceneCtx? = null
    private var lightBuf = FloatArray(0)
    private var outlineTmp = IntArray(0)
    private var bbox = IntArray(0)

    protected lateinit var s: SceneCtx
    protected lateinit var frame: SceneFrame
    protected val c: PixelCanvas get() = s.canvas
    protected val env: Env get() = s.env
    protected val t: Double get() = s.time

    /** The picture's size: the scene canvas's (at detail 1) times [detail]. */
    protected var w = 0
        private set
    protected var h = 0
        private set

    /** Picture pixels per scene canvas pixel: 1, or 2 and 3 closer up. Pixel sizes and offsets are multiplied by it. */
    protected var detail = 1
        private set
    protected val people = PeoplePainter()

    /** Offset of the nominal stage in the canvas. */
    protected var ox = 0
    protected var oy = 0

    private class Reg(val target: SceneTarget, val tappable: Boolean, val slop: Int) {
        val ranges = ArrayList<Int>() // pairs id0, id1
        var rect: IntArray? = null
        /** The last object id painted for it: hits are listed in this order, so the front-most comes last. */
        var order = 0
    }

    private val regs = ArrayList<Reg>()
    private var maxId = 0

    /** Object id → the tappable thing or person that painted it, for the last frame on [ownersOf]. */
    private var owners: Array<SceneTarget?> = emptyArray()
    private var ownersOf: PixelCanvas? = null

    /** What [pokeable] drew under which object ids (ids from, to, in pairs), and object id → the poke that painted it. */
    private val pokeRegs = ArrayList<String>()
    private val pokeRanges = ArrayList<Int>()
    private var pokeOwners: Array<String?> = emptyArray()
    private val pokeSlots: Set<String> by lazy { pokes.map { it.id }.toSet() }

    /**
     * One object slot drawn alone (a sticker of it, see [Stickers]): everything else still runs but paints nothing,
     * on [soloBackdrop], without effects and lights. Null (the default): the whole scene.
     */
    internal var solo: String? = null
    internal var soloBackdrop: Int = 0xFF000000.toInt()

    final override fun render(canvas: PixelCanvas, frame: SceneFrame, lens: Lens): List<SceneHit> {
        val s = ctx?.takeIf { it.canvas === canvas } ?: SceneCtx(canvas).also { ctx = it }
        this.s = s
        this.frame = frame
        val n = canvas.width * canvas.height
        if (lightBuf.size != n) { lightBuf = FloatArray(n); outlineTmp = IntArray(n) }
        s.reset()
        s.time = frame.time
        s.hour = frame.hour
        s.env = Env(frame.hour, frame.month, false, frame.sky.gloom * gloomShare, frame.moon, frame.skyNow)
        night.begin()
        skyDrawn = false
        regs.clear()
        pokeRegs.clear(); pokeRanges.clear()
        maxId = 0
        detail = lens.k.coerceIn(1, Lens.MAX_DETAIL)
        w = lens.baseW * detail
        h = lens.baseH * detail
        // the scene's layout on its canvas, then as much bigger as the picture is: every detail frames the same
        ox = (lens.baseW - STAGE_W) / 2 * detail
        oy = stageTop(lens.baseH) * detail
        s.lens = Lens(detail, 0, 0, w, h) // effects and lights come out [detail] times bigger
        canvas.originX = lens.x0
        canvas.originY = lens.y0
        val alone = solo != null
        canvas.reset(if (alone) soloBackdrop else backdrop(frame))
        // alone, nothing lands on the canvas but inside the one thing's [thing] (see [soloOn])
        if (alone) canvas.clipTop = HIDDEN
        paint()
        canvas.penId = 0; canvas.penEmissive = false
        outlinePass()
        if (alone) { canvas.clipTop = 0; return collectHits() }
        for (f in s.fxList) f()
        canvas.penId = 0; canvas.penEmissive = false
        val amb = ambient()
        lightingPass(amb[0], amb[1], amb[2])
        if (!frame.sky.clear) weather()
        canvas.penId = 0; canvas.penEmissive = false
        // what of the real sky is still to be seen (not behind the hills, the clouds, the rain)
        if (skyDrawn) night.settle()
        val hits = collectHits()
        highlightPass()
        return hits
    }

    /** Draws the whole scene, registering things with [thing] and people with [person]. */
    protected abstract fun paint()

    /** The real night sky of the frame ([SceneFrame.skyNow]), for the painters that show the sky ([nightSky]). */
    internal val night = NightSky()

    /** The last frame drew the real sky: its things can be tapped ([skyAt]). */
    protected var skyDrawn = false

    override fun skyAt(x: Float, y: Float, slop: Float, wall: Long): SkyTap? = if (skyDrawn) night.targets.at(x, y, slop, wall) else null

    override val showsSky: Boolean get() = skyDrawn

    override fun skyUp(): List<SkyTap> = if (skyDrawn) night.targets.up() else emptyList()

    /**
     * The real sky of the frame, when it has one ([SceneFrame.skyNow]): seen toward [facing], the horizon on picture row
     * [horizon] under the middle of the stage, the stars down to picture row [bottom]; the lens's radius [SKY_R], so a
     * vista's sky shows some 120° of the horizon and up to about 55° over it, above the hills. Returns the view (where the
     * moon goes), or null for a frame without the real sky: then the painter draws its own stars.
     */
    protected fun nightSky(facing: Double, horizon: Int, bottom: Int): SkyView? {
        val real = frame.skyNow ?: return null
        val d = detail
        val view = SkyView(facing, (ox + STAGE_W * d / 2f) / d, horizon.toFloat() / d, SKY_R)
        night.draw(c, real, view, w / d, bottom.toFloat() / d, d, 0, 0, env.dark, skyClear(frame.sky), t, frame.wall, frame.skyMark)
        skyDrawn = true
        return view
    }

    /** Ambient light multipliers (r, g, b) for the light pass; the outdoor default follows the sky. */
    protected open fun ambient(): FloatArray = floatArrayOf(env.ambR, env.ambG, env.ambB)

    /**
     * How much of the sky's gloom ([si.lanisce.lani.game.scene.Sky.gloom]) darkens the light here: all of it outdoors,
     * less in a room.
     */
    protected open val gloomShare: Float = 1f

    /** The weather of the frame ([SceneFrame.sky], not clear), drawn over the lit picture (see [Weather]). */
    protected open fun weather() {}

    /** Swaying things sway this much more in the wind: 1 when still, up to 2.5 in a gale. */
    protected val gust: Float get() = 1f + 1.5f * frame.sky.wind

    /** The level a dialog set for effect [name] (one of the art's [SceneArt.effects]), or null when none did. */
    protected fun fx(name: String): Float? = frame.fx[name]

    /** The level of effect [name], 0 when no dialog set it. */
    protected fun fxOn(name: String): Float = frame.fx[name] ?: 0f

    /** The poke [id] (one of [pokes]) playing in this frame: its step and when it began ([Poked.age] at [t]); null when none. */
    protected fun poked(id: String): Poked? = frame.pokes[id]

    /** Whether the village has what object slot [slot] of this art needs ([SceneFixtures]): draw it only then. */
    protected fun there(slot: String): Boolean = SceneFixtures.there(art, slot, frame.world)

    /** Steps done of village project [project] (0 … its steps; all of them by default, [SceneWorld.ALL]): how far its fixture stands. */
    protected fun stage(project: String): Int = frame.world.stage(project)

    /** How many steps village project [project] has. */
    protected fun stages(project: String): Int = frame.world.stages(project)

    /**
     * Steam rising from ([x], [y]): thin white wisps curling up and fading, more and higher the more [amount] (0..1);
     * blended over the picture after the outlines, keeping what is under it tappable.
     */
    protected fun wisps(x: Float, y: Float, amount: Float, seed: Int) {
        if (amount <= 0.02f) return
        val d = detail
        s.fx {
            val n = 4
            for (j in 0 until n) {
                val ph = ((t * 0.55 + j.toFloat() / n + Noise.rnd(j, seed) * 0.2) % 1.0).toFloat()
                val rise = ph * (9f + 9f * amount) * d
                val drift = sin(ph * 5f + j * 2.1f + seed).toFloat() * 1.8f * d * ph
                val a = (1f - ph) * (0.3f + 0.45f * amount)
                val len = 3 * d
                for (q in 0 until len) {
                    val qy = y - rise - q
                    val qx = x + drift + sin(qy / d * 0.6f + j).toFloat() * 0.8f * d
                    c.blend(floor(qx).toInt(), floor(qy).toInt(), STEAM, a * (1f - q / len.toFloat()))
                }
            }
        }
    }

    /** Where the stage's top row lands in a canvas [height] px tall: centred by default. */
    protected open fun stageTop(height: Int): Int = (height - STAGE_H) / 2

    // ------------------------------------------------------------------ registering

    private fun reg(target: SceneTarget, tappable: Boolean, slop: Int): Reg {
        for (r in regs) if (r.target == target) return r
        return Reg(target, tappable, slop).also { regs.add(it) }
    }

    /**
     * Draws one object slot under fresh object ids (outlined in [outline]); its hit rectangle is the box of
     * the pixels it left visible, grown by [slop]. Calling it again for the same slot (a stone ring's back
     * and front halves around the fire) extends the same hit.
     */
    /** Draws a slot; it can be tapped when requested and [visible] (the sun isn't at night). */
    protected inline fun thing(slot: String, outline: Int = Pal.OUTLINE, slop: Int = 0, visible: Boolean = true, draw: () -> Unit) {
        val id0 = begin(outline)
        val alone = soloOn(slot)
        draw()
        soloOff(alone)
        end(SceneTarget.Thing(slot), id0, visible && slot in frame.objects, slop)
    }

    /** Drawing [solo] alone: lets [slot]'s pixels onto the canvas when it is the one (true), see [soloOff]. */
    @PublishedApi internal fun soloOn(slot: String): Boolean {
        if (solo != slot || c.clipTop != HIDDEN) return false
        c.clipTop = 0
        return true
    }

    @PublishedApi internal fun soloOff(alone: Boolean) { if (alone) c.clipTop = HIDDEN }

    /** A slot whose picture is not made of id pixels (smoke, sparks, the stars): a fixed rectangle, in the picture's pixels. */
    protected fun thingRect(slot: String, left: Int, top: Int, right: Int, bottom: Int, visible: Boolean = true) {
        val r = reg(SceneTarget.Thing(slot), visible && slot in frame.objects, 0)
        r.rect = intArrayOf(max(0, left), max(0, top), min(w, right), min(h, bottom))
        r.order = maxId
    }

    /**
     * Draws [p] with their feet at ([bx], [by]) in their pose (talking alone is [si.lanisce.lani.game.scene.Pose.TALK]) and registers them.
     * Asleep ([Pose.SLEEP]) away from a bed, they lie there on a pillow of their own (see [sleeper]).
     */
    protected fun person(p: PersonInScene, bx: Int, by: Int, flip: Boolean, walking: Boolean = false, seated: Boolean = false, crouch: Boolean = false) {
        if (p.pose == Pose.SLEEP) {
            val (hx, hy, dx) = people.flat(0, 0, flip)
            sleeper(p, bx + hx * detail, by + hy * detail, bx + (hx + dx) * detail, by + hy * detail, pillow = true)
            return
        }
        val id0 = begin(Pal.OUTLINE)
        sprite(bx, by) { people.draw(c, p.art, bx, by, t, Noise.hash(p.id.hashCode(), 7) ushr 1, p.talking, flip, walking, seated, p.pose, crouch = crouch) }
        end(SceneTarget.Person(p.id), id0, true, 1)
    }

    /** Whether a frame has been drawn (the painter's last frame is there to hit-test). */
    protected val rendered: Boolean get() = ::s.isInitialized

    /**
     * Draws [p] asleep in a bed ([Pose.SLEEP], see [PeoplePainter.lying]): the head on the pillow, its middle and chin at
     * ([hx], [hy]) (the picture's px), the body under [cover] (its light, middle and dark; null for a wool blanket) along the
     * bed to the feet at ([fx], [fy]), and registers them; their Zzz rise over them in the effects pass. Tapped, they turn
     * over; again soon after, they grumble ([Sleep.poke], played from [SceneFrame.pokes]).
     */
    protected fun sleeper(p: PersonInScene, hx: Int, hy: Int, fx: Int, fy: Int, cover: IntArray? = null, pillow: Boolean = false) {
        val seed = Noise.hash(p.id.hashCode(), 7) ushr 1
        val pk = poked(Sleep.pokeId(p.id)); val a = pk?.age(t) ?: 0f
        val turn = if (pk?.step == 0) (a / Sleep.TURN_S.toFloat()).coerceIn(0f, 1f) else 0f
        val m = Sleep.MUMBLE_S.toFloat()
        val stir = if (pk?.step == 1) PokeArt.ease(a, 0f, 0.3f) * (1f - PokeArt.ease(a, m - 0.6f, m)) else 0f
        val dx = (fx - hx) / detail; val dy = (fy - hy) / detail
        val id0 = begin(Pal.OUTLINE)
        sprite(hx, hy) { people.lying(c, p.art, hx, hy, dx, dy, t, seed, cover, pillow, turn, stir) }
        end(SceneTarget.Person(p.id), id0, true, 1)
        // the Zzz: more and bigger while they turn over, gone while they grumble
        val big = if (turn > 0f) sin(turn * PI.toFloat()) * 0.6f + 0.4f else 0f
        val d = if (dx < 0) -1 else 1
        s.fx { sprite(hx, hy) { people.zzz(c, p.art, hx, hy, d, t, seed, big, stir) } }
    }

    /**
     * Draws a pixel-space motif (a sprite, a tuft, a little flower: whole pixels at offsets from ([ax], [ay])) at
     * the [detail]: [detail] × [detail] pixels per pixel of it, around its anchor. Only offsets from the anchor may
     * be drawn inside, not other points of the picture.
     */
    protected inline fun sprite(ax: Int, ay: Int, draw: () -> Unit) {
        if (detail > 1) c.zoomAt(ax, ay, detail)
        draw()
        if (detail > 1) c.zoomOff()
    }

    /** People of the frame standing at [slot]. */
    protected fun peopleAt(slot: String): List<PersonInScene> = frame.people.filter { it.slot == slot }

    /**
     * Scenery that is no word but reacts to a tap, poke [id] of [pokes] (the cat on the bench): drawn like a [prop], its
     * pixels answer [pokeAt]. A thing's own poke (the pot) needs none of this: a slot's pixels answer for its poke too.
     */
    protected inline fun pokeable(id: String, outline: Int = Pal.OUTLINE, draw: () -> Unit) {
        val id0 = begin(outline)
        draw()
        endPokeable(id, id0)
    }

    @PublishedApi internal fun endPokeable(id: String, id0: Int) {
        pokeRegs.add(id); pokeRanges.add(id0); pokeRanges.add(c.penId)
        maxId = max(maxId, c.penId)
        c.penId = 0
        c.penEmissive = false
    }

    @PublishedApi internal fun begin(outline: Int): Int = s.newObject(outline)

    @PublishedApi internal fun end(target: SceneTarget, id0: Int, tappable: Boolean, slop: Int) {
        val id1 = c.penId
        val r = reg(target, tappable, slop)
        r.ranges.add(id0); r.ranges.add(id1)
        r.order = id1
        maxId = max(maxId, id1)
        c.penId = 0
        c.penEmissive = false
    }

    /** A new object id inside a [thing] (e.g. the flames, without an outline, after the logs). */
    protected fun part(outline: Int = Pal.OUTLINE) { s.newObject(outline) }

    /** Scenery under its own object id, outlined like the things but never tappable; [outline] 0 draws no outline (ropes, chains). */
    protected inline fun prop(outline: Int = Pal.OUTLINE, draw: () -> Unit) {
        part(outline)
        draw()
        c.penId = 0
        c.penEmissive = false
    }

    private fun collectHits(): List<SceneHit> {
        val n = maxId + 1
        if (bbox.size < n * 4) bbox = IntArray(n * 4)
        for (i in 0 until n) { bbox[i * 4] = Int.MAX_VALUE; bbox[i * 4 + 1] = Int.MAX_VALUE; bbox[i * 4 + 2] = -1; bbox[i * 4 + 3] = -1 }
        val ids = c.ids
        var i = 0
        // the canvas's pixels, as the picture's points
        for (y in c.top until c.bottom) for (x in c.left until c.right) {
            val id = ids[i++]
            if (id == 0 || id >= n) continue
            val k = id * 4
            if (x < bbox[k]) bbox[k] = x
            if (y < bbox[k + 1]) bbox[k + 1] = y
            if (x > bbox[k + 2]) bbox[k + 2] = x
            if (y > bbox[k + 3]) bbox[k + 3] = y
        }
        if (owners.size < n) owners = arrayOfNulls(n) else owners.fill(null)
        for (r in regs) {
            if (!r.tappable || r.rect != null) continue
            var k = 0
            while (k < r.ranges.size) { for (id in r.ranges[k]..min(r.ranges[k + 1], n - 1)) owners[id] = r.target; k += 2 }
        }
        ownersOf = c
        if (pokeSlots.isNotEmpty()) pokeOwners(n)
        val out = ArrayList<SceneHit>(regs.size)
        regs.sortBy { it.order }
        for (r in regs) {
            if (!r.tappable) continue
            val rect = rectOf(r) ?: continue
            // in the scene canvas's pixels: what a pixel of the picture touches
            val d = detail
            val l = rect[0] / d; val tp = rect[1] / d; val rt = (rect[2] + d - 1) / d; val bt = (rect[3] + d - 1) / d
            out.add(SceneHit(r.target, l, tp, rt, bt, (l + rt) / 2, tp))
        }
        return out
    }

    private fun rectOf(r: Reg): IntArray? {
        r.rect?.let { return it }
        var x0 = Int.MAX_VALUE; var y0 = Int.MAX_VALUE; var x1 = -1; var y1 = -1
        var k = 0
        while (k < r.ranges.size) {
            for (id in r.ranges[k]..r.ranges[k + 1]) {
                val b = id * 4
                if (bbox[b + 2] < 0) continue
                x0 = min(x0, bbox[b]); y0 = min(y0, bbox[b + 1]); x1 = max(x1, bbox[b + 2]); y1 = max(y1, bbox[b + 3])
            }
            k += 2
        }
        if (x1 < 0) return null
        val slop = r.slop * detail
        return intArrayOf(max(0, x0 - slop), max(0, y0 - slop), min(w, x1 + 1 + slop), min(h, y1 + 1 + slop))
    }

    /** Object id → the poke of the pixels it drew: a poked slot's, whether it can be tapped for its word or not, and [pokeable]'s. */
    private fun pokeOwners(n: Int) {
        if (pokeOwners.size < n) pokeOwners = arrayOfNulls(n) else pokeOwners.fill(null)
        for (r in regs) {
            val slot = (r.target as? SceneTarget.Thing)?.slot ?: continue
            if (slot !in pokeSlots) continue
            var k = 0
            while (k < r.ranges.size) { for (id in r.ranges[k]..min(r.ranges[k + 1], n - 1)) pokeOwners[id] = slot; k += 2 }
        }
        for ((j, name) in pokeRegs.withIndex()) for (id in pokeRanges[j * 2]..min(pokeRanges[j * 2 + 1], n - 1)) pokeOwners[id] = name
    }

    override fun targetAt(canvas: PixelCanvas, x: Int, y: Int, slop: Int): SceneTarget? =
        if (canvas !== ownersOf) null else nearest(canvas, x, y, slop, owners)

    override fun pokeAt(canvas: PixelCanvas, x: Int, y: Int, slop: Int): String? =
        if (canvas !== ownersOf || pokeSlots.isEmpty()) null else nearest(canvas, x, y, slop, pokeOwners)

    /** What [of] has for the object that painted [canvas]'s pixel ([x], [y]), or else for the nearest one within [slop] px. */
    private fun <T : Any> nearest(canvas: PixelCanvas, x: Int, y: Int, slop: Int, of: Array<T?>): T? {
        val ids = canvas.ids; val cw = canvas.width; val ch = canvas.height
        fun owner(px: Int, py: Int): T? {
            if (px < 0 || py < 0 || px >= cw || py >= ch) return null
            val id = ids[py * cw + px]
            return if (id in of.indices) of[id] else null
        }
        owner(x, y)?.let { return it }
        var best: T? = null; var bestD = Int.MAX_VALUE
        for (dy in -slop..slop) for (dx in -slop..slop) {
            val d = dx * dx + dy * dy
            if (d > slop * slop || d >= bestD) continue
            best = owner(x + dx, y + dy) ?: continue
            bestD = d
        }
        return best
    }

    // ------------------------------------------------------------------ passes

    /** 1-px dark outline just outside every object, onto whatever lies behind it (as in the village). */
    private fun outlinePass() {
        val ids = c.ids; val px = c.pixels
        val tmp = outlineTmp
        val w = c.width; val h = c.height // the canvas's own pixels
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val me = ids[i]
            var best = 0
            if (x > 0 && ids[i - 1] > me && ids[i - 1] > best) best = ids[i - 1]
            if (x < w - 1 && ids[i + 1] > me && ids[i + 1] > best) best = ids[i + 1]
            if (y > 0 && ids[i - w] > me && ids[i - w] > best) best = ids[i - w]
            if (y < h - 1 && ids[i + w] > me && ids[i + w] > best) best = ids[i + w]
            tmp[i] = if (best > 0) s.outline[best] else 0
        }
        for (i in 0 until w * h) if (tmp[i] != 0) {
            px[i] = if (ids[i] == 0) tmp[i] else Col.mix(px[i], tmp[i], 0.8f)
            c.emissive[i] = false
        }
    }

    private fun lightingPass(ar: Float, ag: Float, ab: Float) {
        val ambLum = ar * 0.3f + ag * 0.59f + ab * 0.11f
        val ls = ((1.05f - ambLum) * 1.5f).coerceIn(0f, 1.2f)
        val plain = abs(ar - 1f) < 0.01f && abs(ag - 1f) < 0.01f && abs(ab - 1f) < 0.01f
        if (plain) return
        val L = lightBuf
        L.fill(0f)
        val w = c.width; val h = c.height // the canvas's own pixels; the lights stand in the picture
        if (ls > 0.02f) {
            for (k in 0 until s.nLights) {
                val cx = s.lx[k] - c.originX; val cy = s.ly[k] - c.originY; val r = s.lr[k]; val inten = s.li[k]
                val ry = r / 1.7f
                for (y in max(0, (cy - ry).toInt())..min(h - 1, (cy + ry).toInt())) {
                    val dy = (y + 0.5f - cy) * 1.7f
                    for (x in max(0, (cx - r).toInt())..min(w - 1, (cx + r).toInt())) {
                        val dx = x + 0.5f - cx
                        val d2 = dx * dx + dy * dy
                        if (d2 >= r * r) continue
                        val v = 1f - sqrt(d2) / r
                        L[y * w + x] += inten * v * v
                    }
                }
            }
        }
        val px = c.pixels; val em = c.emissive
        for (i in 0 until w * h) {
            if (em[i]) continue
            val col = px[i]
            val l = min(L[i] * ls, 1.3f)
            val fr = ar + l * 1.0f; val fg = ag + l * 0.72f; val fb = ab + l * 0.42f
            px[i] = Col.rgb((Col.r(col) * fr).toInt(), (Col.g(col) * fg).toInt(), (Col.b(col) * fb).toInt())
        }
    }

    /** A bright pulsing 1-px outline around the tapped thing or person. */
    private fun highlightPass() {
        val key = frame.highlight ?: return
        val r = regs.firstOrNull { val tg = it.target; (tg is SceneTarget.Thing && tg.slot == key) || (tg is SceneTarget.Person && tg.id == key) } ?: return
        val pulse = 0.5f + 0.5f * sin(t * 5).toFloat()
        val col = Col.mix(Pal.GOLD, Col.hex(0xFFFFFF), pulse)
        val fixed = r.rect
        if (fixed != null) {
            val x0 = fixed[0] - 1; val y0 = fixed[1] - 1; val x1 = fixed[2]; val y1 = fixed[3]
            for (x in x0..x1) { if ((x + (t * 8).toInt()) % 3 != 0) { c.set(x, y0, col); c.set(x, y1, col) } }
            for (y in y0..y1) { if ((y + (t * 8).toInt()) % 3 != 0) { c.set(x0, y, col); c.set(x1, y, col) } }
            return
        }
        val ids = c.ids; val px = c.pixels
        val inR = { id: Int ->
            var yes = false
            var k = 0
            while (k < r.ranges.size) { if (id >= r.ranges[k] && id <= r.ranges[k + 1]) { yes = true; break }; k += 2 }
            yes
        }
        val rect = rectOf(r) ?: return
        val w = c.width; val h = c.height // the canvas's own pixels
        val ox = c.originX; val oy = c.originY
        for (y in max(0, rect[1] - 2 - oy) until min(h, rect[3] + 2 - oy)) for (x in max(0, rect[0] - 2 - ox) until min(w, rect[2] + 2 - ox)) {
            val i = y * w + x
            if (inR(ids[i])) continue
            val near = (x > 0 && inR(ids[i - 1])) || (x < w - 1 && inR(ids[i + 1])) || (y > 0 && inR(ids[i - w])) || (y < h - 1 && inR(ids[i + w]))
            if (near) px[i] = col
        }
    }

    // ------------------------------------------------------------------ drawing helpers

    protected inline fun glow(block: () -> Unit) {
        val was = c.penEmissive; c.penEmissive = true; block(); c.penEmissive = was
    }

    /** A box with a light top-left edge and a dark bottom-right edge. */
    protected fun box(x: Int, y: Int, bw: Int, bh: Int, light: Int, mid: Int, dark: Int) {
        c.fillRect(x, y, bw, bh, mid)
        c.hline(x, x + bw - 1, y, light); c.vline(x, y, y + bh - 1, light)
        c.hline(x + 1, x + bw - 1, y + bh - 1, dark); c.vline(x + bw - 1, y + 1, y + bh - 1, dark)
    }

    /** Ordered-dither blend of two colours over a rectangle; [f] = share of [b]. */
    protected fun ditherRect(x: Int, y: Int, bw: Int, bh: Int, a: Int, b: Int, f: Float) {
        for (yy in y until y + bh) for (xx in x until x + bw) c.set(xx, yy, if (f > Dither.at(xx, yy)) b else a)
    }

    /** Ground shadow (darkens only id-0 pixels). */
    protected fun shadow(cx: Float, cy: Float, rx: Float, ry: Float, f: Float = 0.82f) {
        for (y in max(c.top, (cy - ry).toInt())..min(c.bottom - 1, (cy + ry).toInt())) for (x in max(c.left, (cx - rx).toInt())..min(c.right - 1, (cx + rx).toInt())) {
            val dx = (x + 0.5f - cx) / rx; val dy = (y + 0.5f - cy) / ry
            if (dx * dx + dy * dy <= 1f) c.shadeGround(x, y, f)
        }
    }

    /** The sky colour at [tt] (0 = zenith, 1 = horizon) of this frame. */
    protected fun skyAt(tt: Float): Int = Backdrop(s).skyAt(tt)

    /** Sky gradient rows [y0, y1) from the top colour to the horizon colour, banded and dithered like the village's. */
    protected fun sky(y0: Int, y1: Int, x0: Int = 0, x1: Int = w) {
        val bd = Backdrop(s)
        val bands = 9f
        c.penEmissive = true
        for (y in max(max(0, y0), c.top) until min(min(h, y1), c.bottom)) {
            val tt = ((y - y0).toFloat() / max(1, y1 - y0)).coerceIn(0f, 1f)
            for (x in max(max(0, x0), c.left) until min(min(w, x1), c.right)) {
                val q = floor(tt * bands + Dither.at(x, y) - 0.5f) / bands
                c.set(x, y, bd.skyAt(q.coerceIn(0f, 1f)))
            }
        }
        c.penEmissive = false
    }

    /** A few soft clouds drifting over rows above [horizon]; coloured by the time of day. */
    protected fun clouds(horizon: Int, n: Int, seed: Int) {
        val lightC = when {
            env.dark > 0.6f -> Col.hex(0x2E3558)
            env.sun < 0.2f -> Col.mix(Col.hex(0xFFE6C8), env.skyHorizon, 0.35f)
            else -> Col.hex(0xFAFCFF)
        }
        val shadeC = when {
            env.dark > 0.6f -> Col.hex(0x1E2442)
            env.sun < 0.2f -> Col.mix(Col.hex(0xB07A8A), env.skyMid, 0.3f)
            else -> Col.hex(0xD2DEEE)
        }
        c.penEmissive = true
        val d = detail
        for (k in 0 until n) {
            val speed = 0.8 + Noise.rnd(k, seed + 7) * 0.8
            val span = w / d + 60
            val x = ((((Noise.rnd(k, seed + 8) * span + t * speed) % span) - 30) * d).toFloat()
            val y = 4f * d + Noise.rnd(k, seed + 9) * horizon * 0.45f
            val sz = (0.8f + Noise.rnd(k, seed + 10) * 0.6f) * d
            for (b in 0..3) {
                val bx = x + (b - 1.5f) * 5 * sz; val by = y - (if (b == 1 || b == 2) 2.5f else 0f) * sz
                c.fillEllipse(bx, by, 5f * sz, 3f * sz, shadeC)
            }
            for (b in 0..3) {
                val bx = x + (b - 1.5f) * 5 * sz - 0.8f * d; val by = y - (if (b == 1 || b == 2) 2.5f else 0f) * sz - 1.2f * d
                c.fillEllipse(bx, by, 4.2f * sz, 2.4f * sz, lightC)
            }
        }
        c.penEmissive = false
    }

    /** The sun by day, where the hour puts it between [x0] and [x1]; nothing when it is down. */
    protected fun sun(x0: Int, x1: Int, horizon: Int) {
        if (env.sun <= -0.12f) return
        val span = (s.hour - env.sunrise) / (env.sunset - env.sunrise)
        val x = x0 + (x1 - x0) * span.coerceIn(0f, 1f)
        val d = detail
        val y = max(7f * d, horizon - 4f * d - env.sun * horizon * 0.95f)
        val low = (1 - env.sun * 2.5f).coerceIn(0f, 1f)
        val core = Col.mix(Col.hex(0xFFF6D8), Col.hex(0xFFB04A), low)
        c.penEmissive = true
        c.ditherCircle(x, y, 9f * d, Col.mix(core, env.skyHorizon, 0.4f), 0.28f)
        c.fillCircle(x, y, 5f * d, core)
        c.fillCircle(x - d, y - d, 2.8f * d, Col.mix(core, Col.hex(0xFFFFFF), 0.5f))
        c.penEmissive = false
    }

    /** Twinkling stars over rows [y0, y1); brighter the darker the night. */
    protected fun stars(y0: Int, y1: Int, n: Int = 70, seed: Int = 0) {
        val a = ((env.dark - 0.3f) / 0.5f).coerceIn(0f, 1f)
        if (a <= 0f) return
        c.penEmissive = true
        for (k in 0 until n) {
            val x = (Noise.rnd(k, seed + 1) * w).toInt(); val y = y0 + (Noise.rnd(k, seed + 2) * (y1 - y0)).toInt()
            val tw = 0.6f + 0.4f * sin(t * (1.5 + Noise.rnd(k, seed + 3) * 2) + k).toFloat()
            val b = a * tw * (0.5f + Noise.rnd(k, seed + 4) * 0.5f)
            c.blend(x, y, Col.hex(0xFFF8E0), b)
            if (k % 9 == 0 && b > 0.6f) {
                c.blend(x - 1, y, Col.hex(0xC8D0F0), b * 0.5f); c.blend(x + 1, y, Col.hex(0xC8D0F0), b * 0.5f)
                c.blend(x, y - 1, Col.hex(0xC8D0F0), b * 0.5f); c.blend(x, y + 1, Col.hex(0xC8D0F0), b * 0.5f)
            }
        }
        c.penEmissive = false
    }

    /**
     * The moon as the date has it ([Env.moonAge], see [Moon]): lit on the right while it
     * waxes and on the left while it wanes, from a sliver to half to full, softer along the terminator; a crescent's dark
     * part faintly shows by night (the earthshine), and a halo round it the brighter the fuller it is. By night, and a
     * faint ghost by day; nothing while it is down or new ([Env.moonShows]). Drawn with ids so it can be a slot.
     */
    protected fun moon(cx: Int, cy: Int, r: Int, skyCol: Int) {
        if (!env.moonShows) return
        val a = if (env.dark > 0.25f) ((env.dark - 0.25f) / 0.4f).coerceIn(0.35f, 1f) else 0.22f
        val lit = Col.mix(skyCol, Col.hex(0xF6F0D0), a); val shade = Col.mix(skyCol, Col.hex(0xD8D2B0), a)
        val earth = if (env.dark > 0.3f) earthshine() * 0.4f * a else 0f
        val shine = Col.mix(skyCol, Col.hex(0x5A6488), earth)
        c.penEmissive = true
        if (env.dark > 0.3f) c.ditherCircle(cx.toFloat(), cy.toFloat(), r * 1.6f, Col.hex(0x6A78A8), 0.3f * a * (0.4f + 0.6f * env.moonLit))
        val band = 1.5f / r // the terminator's soft edge, a pixel and a half wide
        for (yy in -r..r) for (xx in -r..r) {
            if (xx * xx + yy * yy > r * r) continue
            val u = Moon.light(xx.toFloat() / r, yy.toFloat() / r, env.moonAge)
            when {
                u > band -> c.set(cx + xx, cy + yy, lit)
                u > 0f -> c.set(cx + xx, cy + yy, shade)
                earth > 0f -> c.set(cx + xx, cy + yy, shine)
            }
        }
        c.penEmissive = false
    }

    /** How much the earthshine lights the moon's dark part: most on a thin crescent, none from the quarters on. */
    protected fun earthshine(): Float = ((0.5f - env.moonLit) / 0.45f).coerceIn(0f, 1f)

    /** The moon's row along its way across the sky: [top] when it stands highest, [low] as it rises or sets. */
    protected fun moonRow(top: Int, low: Int): Int = (low + (top - low) * env.moonHeight).roundToInt()

    /** A drifting glow: fireflies on warm nights. */
    protected fun fireflies(x0: Int, y0: Int, x1: Int, y1: Int, n: Int, seed: Int) {
        if (env.dark < 0.4f || env.month !in 5..8) return
        s.fx {
            c.penEmissive = true
            for (k in 0 until n) {
                val ph = ((t * 0.7 + Noise.rnd(k, seed) * 4) % 4.0).toFloat()
                if (ph > 1.6f) continue
                val x = x0 + (Noise.rnd(k, seed + 1) * (x1 - x0)) + sin(t * 1.3 + k).toFloat() * 4f * detail
                val y = y0 + (Noise.rnd(k, seed + 2) * (y1 - y0)) + sin(t * 0.9 + k * 2).toFloat() * 2f * detail
                c.fillRect(x.toInt(), y.toInt(), detail, detail, Col.hex(0xD8FF7A))
            }
            c.penEmissive = false
        }
    }

    companion object {
        const val STAGE_W = 240
        const val STAGE_H = 160

        /**
         * The radius of a scene's sky lens (scene canvas px, see [SkyView]): a little wider than the painted camera sees
         * the ground (its focal length is 190 px), so a vista's strip of sky over the hills holds the constellations
         * that stand halfway up.
         */
        const val SKY_R = 110f
        private val STEAM = Col.hex(0xF4F6FA)
        /** A clip below every row: nothing drawn lands (see [solo]). */
        private const val HIDDEN = Int.MAX_VALUE
    }
}

/** All built-in scene painters, in the order of [SceneArt.objects]. */
internal object ScenePaintersList {
    private val makers: List<() -> ScenePainter> = listOf(
        ::CampfirePainter, ::KitchenPainter, ::ForestPainter, ::SquarePainter,
        ::TentPainter, ::FieldPainter, ::SmithyPainter, ::SchoolPainter,
        // the culture packs' own landscapes: Primorska's vineyard on the hills (from its vineyard project), Friuli's sea
        ::HillsPainter, ::SeaPainter,
        // Primorska's mountains at the horizon: the pasture above the lake under Triglav
        ::AlpsPainter,
        // the stream by the mill and the pond in the woods (their scenes open from those spots of the landscape)
        ::StreamPainter, ::PondPainter,
        // the church inside and the apiary: their scenes open from those buildings
        ::ChurchPainter, ::ApiaryPainter,
        // the market at the square's edge and the view from the watchtower (their scenes open from those buildings)
        ::MarketPainter, ::WatchtowerPainter,
        // the rooms of the houses: the living room, the workshop, the wine cellar, the attic (game/scene/Homes)
        ::LivingRoomPainter, ::WorkshopPainter, ::CellarPainter, ::AtticPainter,
    )

    fun all(): List<ScenePainter> = makers.map { it() }

    /** A new painter for [art], or null for an art without one. */
    fun create(art: String): ScenePainter? = makers.asSequence().map { it() }.firstOrNull { it.art == art }
}
