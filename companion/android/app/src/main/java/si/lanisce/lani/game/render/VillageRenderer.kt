package si.lanisce.lani.game.render

import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.EventKind
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.PLOTS_PER_AGE
import si.lanisce.lani.game.TentMove
import si.lanisce.lani.game.render.scene.PeoplePainter
import si.lanisce.lani.game.scene.DaySky
import si.lanisce.lani.game.scene.Lightning
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.scene.TownSpots
import si.lanisce.lani.game.sky.SkyNow
import si.lanisce.lani.game.sky.SkyTap
import si.lanisce.lani.game.villagers.Villager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Per-frame inputs besides the game state. Rendering is a pure function of (state, frame, canvas size).
 *
 * @param time animation clock in seconds
 * @param hour local hour of day, 0..24
 * @param month 1..12 (seasons)
 * @param justBuiltProgress 0..1 of the construction animation of [justBuilt]; 1 = done
 * @param celebrate 0..1 progress of the fireworks; negative = none
 * @param visitors places where someone stands and waits (a happening is on there), one villager each: someone who isn't
 *   among [people] (see [TownPeople.waiting])
 * @param standing the people with a happening on now and its place ([si.lanisce.lani.game.scene.TownMarkers.standing]):
 *   they stand there, or aren't on the map when it's far off (see [TownPeople.of])
 * @param lens which part of the nominal canvas this canvas shows, at what detail (see [Lens]); the default is the canvas itself
 * @param people the villagers who live here, drawn around their homes (see [TownPeople]); the population beyond them walks about unnamed
 * @param today ISO date, for the marks practice left on the land (see [si.lanisce.lani.game.WorldMarks]); empty = none shown
 * @param moon the moon's age, days since the new moon ([Moon.age]; the app passes today's): its phase and when it is up; full by default
 * @param skyNow the real sky over the village at this minute (see [NightSky]): its stars, planets, Milky Way and shooting
 *   stars, and the real moon where it stands; null (tests, previews): the painted stars and the moon of [moon]
 * @param wall the clock's epoch ms, for the shooting stars ([si.lanisce.lani.game.sky.Meteors])
 * @param skyMark what of the sky to show brighter, its card being open ([si.lanisce.lani.game.sky.SkyTap.mark])
 * @param plots the plot chooser's marks (see [PlotMarks]); null when nobody is choosing a plot
 */
data class Frame(
    val time: Double = 0.0,
    val hour: Float = 12f,
    val month: Int = 6,
    val compact: Boolean = false,
    val highlightPlot: Int? = null,
    val justBuilt: String? = null,
    val justBuiltProgress: Float = 1f,
    val celebrate: Float = -1f,
    val visitors: List<TownPlace> = emptyList(),
    val lens: Lens = Lens(),
    val people: List<Villager> = emptyList(),
    val today: String = "",
    /** The sea shows along the horizon (a village by the coast: its culture pack's backdrop, [si.lanisce.lani.game.culture.Horizon.sea]). */
    val sea: Boolean = false,
    val standing: Map<String, TownPlace> = emptyMap(),
    val moon: Float = Moon.FULL,
    val skyNow: SkyNow? = null,
    val wall: Long = 0L,
    val skyMark: String? = null,
    val plots: PlotMarks? = null,
    /** The people as a test or a tool places them (who, where, doing what); null: [TownPeople] places [people]. */
    val placed: List<Placed>? = null,
    /** What the villagers' day needs besides the state (where each sleeps, the storytellers, who waits): see [si.lanisce.lani.game.villagers.Routine.Day]. */
    val day: si.lanisce.lani.game.villagers.Routine.Day = si.lanisce.lani.game.villagers.Routine.Day(),
) {
    /** The frame's date ([today]), or the tests' day when it has none. */
    val date: java.time.LocalDate get() = runCatching { java.time.LocalDate.parse(today) }.getOrNull() ?: TownPeople.DEFAULT_DATE
}

/**
 * The plot chooser on the map ([si.lanisce.lani.game.PlotChoice]): the [free] plots to choose from, each framed on the
 * ground; moving, the plots of other buildings it could swap with ([standing]: by building id, the plot it stands on now;
 * no mark of their own, a tap on the building takes its plot); the plot the game suggests, a ★ over it ([suggested]); the
 * plot [chosen], glowing, a pointer over the building shown there ([shown], its id: outlined in gold, as it would stand).
 */
data class PlotMarks(
    val free: List<Int>,
    val standing: Map<String, Int> = emptyMap(),
    val suggested: Int? = null,
    val chosen: Int? = null,
    val shown: String? = null,
) {
    /** The plots of the buildings it could swap with. */
    val swaps: List<Int> get() = standing.values.distinct().sorted()

    /** Every plot a tap can choose. */
    val all: List<Int> get() = free + swaps
}

/** Where the renderer's camera puts the fire (the village centre) and the horizon on a canvas. */
data class Composition(val fireX: Int, val fireY: Int, val horizon: Int)

/** What a tap landed on. */
sealed interface VillageHit {
    data class OnBuilding(val building: Building) : VillageHit
    data object OnFire : VillageHit
    /** Someone who lives here (their villager id). */
    data class OnVillager(val id: String) : VillageHit
    /** A named spot of the landscape (see [si.lanisce.lani.game.scene.TownSpots]): the woodpile, the rocks, the pond … */
    data class OnSpot(val id: String) : VillageHit
    /** A village project's landmark, finished or being built (its project id; see [LandmarkLayout]). */
    data class OnLandmark(val project: String) : VillageHit
    /** A wild animal round the village (see [Wildlife]): its [word], to look up ("divji prašič"). */
    data class OnAnimal(val word: String) : VillageHit
}

/** A wild animal drawn in the last frame (see [Wildlife]): where it stands (or flies), in nominal canvas pixels. */
data class WildSeen(val kind: Wild, val at: CanvasPoint)

/**
 * The village's map: the camera of a canvas ([composition]) and the clearing's metric. Where plot i lies, where the stream
 * runs and the clearing's edge are the village's land's ([of], see [Terrain]): an organic layout of plots round the fire
 * (plot 0 closest), in rows along the road, [Terrain.COL] cells apart in a row and [Terrain.ROW] cells from row to row,
 * every other row shifted along and each plot a little off the line. On the screen the next row lies down and to the
 * left, so the room between the rows is what keeps a building in front from hiding the one behind it: with 7 cells a
 * house or a hayrack in front leaves most of a beehive, a tent or a well behind it in view (with 4.5, as before, it hid
 * most of them). Zoomed out, the whole village shows; the details come with zooming in (see [CameraRig]).
 */
object VillageLayout {
    const val MAX_PLOTS = 28

    /** The land of [state]'s village, laid out (see [Terrain]). */
    fun of(state: GameState): Terrain = Terrain.of(state)

    fun metric(cx: Float, cy: Float): Float = screenMetric((cx - cy) * 4f, (cx + cy) * 2f)

    /** Elliptical "distance from the fire" in screen pixels; wider than tall like a phone screen. */
    fun screenMetric(dx: Float, dy: Float): Float = sqrt(dx * dx + (2.4f * dy) * (2.4f * dy))

    /** Canvases up to this tall keep the classic framing; taller ones (the full-screen town) add forest below, not sky. */
    const val TALL = 320

    /** How far above the fire the horizon lies (px): just behind the back row of plots, so every building stands on the land. */
    const val HORIZON = 60

    /**
     * The camera of a canvas: the fire in the middle, a little below the centre, the horizon [HORIZON] px above it.
     * Compact (a short strip): the horizon sits at least 30 px down so the Alps keep their height behind the
     * village. Tall canvases (a phone's full screen): past [TALL] rows a bit less than half of the extra height
     * goes above the fire, so the village sits in the middle, between the HUD and the bar, forest below it.
     */
    fun composition(w: Int, h: Int, compact: Boolean): Composition {
        val fireY = when {
            compact -> (h * 0.62f).roundToInt()
            h <= TALL -> (h * 0.61f).roundToInt()
            else -> (TALL * 0.61f + (h - TALL) * TALL_SKY).roundToInt()
        }
        val horizon = if (compact) min(fireY - 26, max(fireY - HORIZON, 30)) else fireY - HORIZON
        return Composition(w / 2, fireY, horizon)
    }

    /** Share of a tall canvas's extra rows that goes above the fire; the rest is the woods below (see [Foreground]). */
    private const val TALL_SKY = 0.35f

    /** Angle of a canvas offset from the fire, in the clearing's elliptical metric. */
    fun angle(dx: Float, dy: Float): Float = atan2(dy * 2.4f, dx)
}

/** What kind of ground a canvas pixel is (see the ground pass); the detail pass stamps tufts, stones and ripples by it. */
internal object Ground {
    const val OTHER: Byte = 0
    const val GRASS: Byte = 1
    const val FLOOR: Byte = 2
    const val DIRT: Byte = 3
    const val WATER: Byte = 4
    const val COBBLE: Byte = 5
}

/** Shared per-frame drawing context. */
internal class SceneCtx(val canvas: PixelCanvas) {
    val iso = Iso(canvas)
    var env = Env(12f, 6, false)
    /** The land the frame's village stands on (see [Terrain]). */
    var land: Terrain = Terrain.CLASSIC
    var time = 0.0
    var hour = 12f
    /** Which part of the nominal canvas this canvas shows, at what detail. */
    var lens = Lens(1, 0, 0, canvas.width, canvas.height)
    val k: Int get() = lens.k
    /** ISO day of the frame, for the marks on the land ("" = none). */
    var today = ""
    /** The berry bushes are picked bare and the fields' rows harvested today. */
    var harvested = false
    /** The real sky of the frame, drawn by [night] (null: the painted stars); see [Frame.skyNow]. */
    var skyNow: SkyNow? = null
    var night: NightSky? = null
    var wall = 0L
    var skyMark: String? = null
    /** How clear the sky is for the stars ([skyClear]). */
    var clear = 1f
    /** The buildings whose windows are dark tonight, everyone in them asleep ([TownPeople.darkWindows]). */
    var darkWindows: Set<String> = emptySet()
    val trees = TreePainter(this)
    val effects = Effects(this)
    val woods = WoodsPainter(this)
    val wildlife = WildPainter(this)
    val people = PeoplePainter()
    val lightBuf = FloatArray(canvas.width * canvas.height)
    /** Ground kinds by pixel (see [Ground]), written by the ground pass. */
    val ground = ByteArray(canvas.width * canvas.height)

    /** Nominal canvas x → this canvas's x. */
    fun cx(bx: Float): Float = lens.cx(bx)
    fun cy(by: Float): Float = lens.cy(by)

    /** Draws a pixel-space sprite around its anchor at the lens's detail (whole blocks at k > 1). */
    inline fun sprite(ax: Int, ay: Int, draw: () -> Unit) {
        if (k > 1) canvas.zoomAt(ax, ay, k)
        draw()
        if (k > 1) canvas.zoomOff()
    }

    val fxList = ArrayList<() -> Unit>()
    fun fx(block: () -> Unit) { fxList.add(block) }
    fun smoke(x: Float, y: Float, amount: Float, seed: Int, dark: Boolean = false) = fx { effects.smoke(x, y, amount, seed, dark) }
    fun sparks(x: Float, y: Float, n: Int, seed: Int) = fx { effects.sparks(x, y, n, seed) }

    var nLights = 0
    val lx = FloatArray(MAX_LIGHTS); val ly = FloatArray(MAX_LIGHTS); val lr = FloatArray(MAX_LIGHTS); val li = FloatArray(MAX_LIGHTS)

    /** A light at canvas point ([x], [y]) with a nominal radius [r] (it scales with the detail). */
    fun light(x: Float, y: Float, r: Float, i: Float) {
        if (nLights >= MAX_LIGHTS || i <= 0f) return
        lx[nLights] = x; ly[nLights] = y; lr[nLights] = r * k; li[nLights] = i; nLights++
    }

    val outline = IntArray(MAX_IDS)
    private var nextId = 1
    fun newObject(outlineColor: Int): Int {
        val id = min(nextId++, MAX_IDS - 1)
        outline[id] = outlineColor
        canvas.penId = id
        return id
    }

    fun reset() { fxList.clear(); nLights = 0; nextId = 1; outline.fill(0) }

    companion object { const val MAX_LIGHTS = 192; const val MAX_IDS = 8192 }
}

/**
 * Software renderer for "Moja vas". No Android dependencies: renders into a [PixelCanvas].
 * Keeps the tappable things of the last frame for [hitTest].
 */
class VillageRenderer {
    private val ctxs = LinkedHashMap<PixelCanvas, SceneCtx>()
    private val hits = ArrayList<Pair<VillageHit, IntArray>>()
    /** Ground spots that are areas, not objects: (what, ellipse [cx, cy, rx, ry] in canvas px). */
    private val areas = ArrayList<Pair<VillageHit, FloatArray>>()
    /** Object id -> 1 + index into [hits] (0 = not tappable), for pixel-exact taps. */
    private val owner = IntArray(SceneCtx.MAX_IDS)
    /** The palisade's slot in [owner] (0 = none): long and thin round everything, it gives way in [hitTest]. */
    private var ringSlot = 0
    private var lastCanvas: PixelCanvas? = null
    private var treeKey = ""
    private var treeCache: List<FloatArray> = emptyList()
    /** Indices into [treeCache] of the trees that can be felled, nearest the woodpile first. */
    private var choppable = IntArray(0)
    private var fgKey = ""
    private var fgCache: Foreground? = null
    private var pathXs = FloatArray(0)
    private val stages = HashMap<Int, Regrowth>()
    private var lmState: GameState? = null
    private var lmSize = 0L
    private var lmSpots: List<LandmarkSpot> = emptyList()
    private val anchors = HashMap<String, CanvasPoint>()
    /** Which slots of [owner] are wild animals': they take only a tap right on them, or one nothing else is within reach of. */
    private val wild = BooleanArray(SceneCtx.MAX_IDS)
    private val seen = ArrayList<WildSeen>()
    private var wildState: GameState? = null
    private var wildKey = ""
    private var wildPlaces: WildPlaces? = null
    private var planKey = ""
    private var plan: List<Sighting> = emptyList()
    /** The real night sky (see [Frame.skyNow]): its caches, and what the last frame drew of it to tap. */
    private val night = NightSky()
    private var skyDrawn = false

    /** Sky colour at the top of the last frame (for letterboxing). */
    var skyColor: Int = Col.hex(0x4D93DC)
        private set

    /** The lens of the last frame: taps in nominal pixels map through it to the frame's pixels. */
    var lens: Lens = Lens()
        private set

    /** Where the people who live here were drawn in the last frame: villager id → the point above their head, in nominal canvas pixels. */
    val villagerAnchors: Map<String, CanvasPoint> get() = anchors

    /** The wild animals drawn in the last frame (see [Wildlife]). */
    val wildlife: List<WildSeen> get() = seen

    /** Where the wild animals of the last frame's village could be (see [Wildlife.places]); null before any came out. */
    internal val wildPlacesShown: WildPlaces? get() = wildPlaces

    /** With [profile] on, the last frame's time per pass (ms), for the frame-time tests. */
    var profile = false
    var lastProfile = ""
        private set
    private val marks = LongArray(8)
    private fun mark(i: Int) { if (profile) marks[i] = System.nanoTime() }

    private class Obj(val depth: Float, val draw: () -> Unit)

    private fun ctxFor(canvas: PixelCanvas): SceneCtx {
        ctxs[canvas]?.let { return it }
        if (ctxs.size >= 2) ctxs.remove(ctxs.keys.first())
        return SceneCtx(canvas).also { ctxs[canvas] = it }
    }

    fun render(canvas: PixelCanvas, state: GameState, frame: Frame) {
        val s = ctxFor(canvas)
        mark(0)
        s.reset()
        hits.clear()
        areas.clear()
        anchors.clear()
        owner.fill(0)
        wild.fill(false)
        seen.clear()
        ringSlot = 0
        lastCanvas = canvas
        val w = canvas.width; val h = canvas.height
        val lens = frame.lens.let { if (it.baseW <= 0 || it.baseH <= 0) Lens(1, 0, 0, w, h) else it }
        this.lens = lens
        s.lens = lens; s.iso.k = lens.k
        val k = lens.k
        val bw = lens.baseW; val bh = lens.baseH
        val event = state.event?.kind
        val storm = event == EventKind.STORM
        // the sky a scene's dialog left today (a shower after Luka's hay): rain and a grey sky, not the storm event
        val sky = DaySky.of(state, frame.today)
        s.time = frame.time
        s.hour = frame.hour
        s.today = frame.today
        s.env = Env(frame.hour, frame.month, storm, sky.gloom, frame.moon, frame.skyNow)
        night.begin()
        skyDrawn = frame.skyNow != null
        s.skyNow = frame.skyNow; s.night = night; s.wall = frame.wall; s.skyMark = frame.skyMark
        s.clear = skyClear(sky, storm)
        val env = s.env
        s.darkWindows = if (env.windows > 0.35f) TownPeople.darkWindows(state, frame.people, frame.hour, frame.month, frame.date, frame.day, frame.standing) else emptySet()
        skyColor = env.skyTop
        canvas.reset(env.skyTop)

        // camera: fire at the centre, a little below the middle (see VillageLayout.composition), mapped through the lens.
        // Compact: Triglav moves to the right third.
        val comp = VillageLayout.composition(bw, bh, frame.compact)
        val fireX = lens.cx(comp.fireX.toFloat()).roundToInt()
        val fireY = lens.cy(comp.fireY.toFloat()).roundToInt()
        val horizon = lens.cy(comp.horizon.toFloat()).roundToInt()
        s.iso.ox = fireX.toFloat(); s.iso.oy = fireY.toFloat()

        val age = state.age
        val land = VillageLayout.of(state)
        s.land = land
        val clearR = land.clearing(age)
        val roadLeft = age.ordinal >= Age.ZASELEK.ordinal
        val roadHalf = if (age.ordinal >= Age.VAS.ordinal) 1f else 0.55f
        val plazaR = when {
            age.ordinal >= Age.TRG.ordinal -> 3.2f
            age.ordinal >= Age.ZASELEK.ordinal -> 2.6f
            else -> 1.9f
        }
        val buildings = state.buildings.filter { it.type.onPlot && it.plot in 0 until VillageLayout.MAX_PLOTS }
            .groupBy { it.plot }.map { it.value.last() }.sortedBy { it.plot }
        val palisade = state.buildings.firstOrNull { it.type == BuildingType.PALISADE }
        // the woods below the village, on a tall canvas
        val fg = if (frame.compact) null else foregroundFor(land, age, bw, bh)
        // the marks practice left: which trees are stumps or growing back, how much is picked and quarried
        val today = frame.today
        val recentFelled = state.world.felled.count { Regrowth.days(it, today) < Regrowth.GROWN_DAYS }
        val recentQuarried = state.world.quarried.count { Regrowth.days(it, today) < Regrowth.GROWN_DAYS }
        s.harvested = today.isNotEmpty() && state.world.picked.lastOrNull() == today

        // ---- backdrop
        val backdrop = Backdrop(s)
        backdrop.draw(horizon, storm, shift = if (frame.compact) COMPACT_SHIFT else 0f, sea = frame.sea, skyline = land.skyline)
        mark(1)

        // ---- ground
        val paths = PathGrid()
        for (b in buildings) {
            val p = land.centers[b.plot]
            if (b.type == BuildingType.PALISADE || b.type == BuildingType.FIELD) continue
            // straight to the road (where it runs past the plot)
            val road = land.roadY(p[0])
            paths.mark(p[0] - 0.5f, min(p[1], road), p[0] + 0.5f, max(p[1], road))
        }
        // the footpath down to the landing at the stream: through the palisade's river gate where the ring would stand, or
        // inside it where the ring takes the stream in
        val ring = Palisade.ring(land, age, fg)
        val footpath = if (frame.compact) null else Palisade.footpath(ring)
        val landing = footpath?.first
        val riverGate = footpath?.second
        if (landing != null && riverGate != null) paths.mark(landing[0] + 0.2f, landing[1] - 0.3f, riverGate[0] + 2.2f, landing[1] + 0.3f)
        groundPass(s, horizon, clearR, roadLeft, roadHalf, plazaR, age, paths, fg)
        if (k > 1) groundDetail(s, horizon)
        // no far treeline where the sea comes up to the horizon (a coast)
        if (land.sea != null) backdrop.treeline(horizon, from = comp.fireX + land.shoreX((comp.horizon - comp.fireY).toFloat()) + Terrain.BEACH) else backdrop.treeline(horizon)
        frame.highlightPlot?.let { if (it in 0 until VillageLayout.MAX_PLOTS) highlightGround(s, it) }
        frame.plots?.let { plotGround(s, it) }
        mark(2)

        // ---- objects, painter's order by iso depth
        val objs = ArrayList<Obj>(600)
        val painter = BuildingPainter(s)
        val trees = treesFor(state, bw, bh, frame.compact, comp, horizon = comp.horizon, clearR, roadLeft, fg)
        stagesFor(state, today)
        // the village projects' landmarks, where the trees and bushes make way for them
        val landmarks = landmarksFor(state, bw, bh, frame.compact)
        if (fg != null) addWoods(s, state, fg, objs, event, recentFelled, landmarks)
        // the tent that moved out of the village (game/TentMove): a campsite by the pond, where the trees make way for it
        val camp = if (fg != null && TentMove.moved(state)) Campsite(fg) else null
        if (camp != null) addCamp(s, state, camp, painter, objs)
        if (!frame.compact) addEdgeSpots(s, state, bw, bh, fg == null, objs, recentFelled, recentQuarried)
        // the wild animals round the village, out of the woods at their time of day; not while wolves or a bear are about,
        // in a storm or at a festival
        val calm = event != EventKind.WOLVES && event != EventKind.BEAR && event != EventKind.STORM && event != EventKind.FESTIVAL
        if (!frame.compact && calm) addWildlife(s, state, frame, comp, fg, trees, landmarks, camp, palisade, buildings, objs, sky.rain)
        // only the trees whose base is on this canvas (their crowns rise from it, their shadows hang a little below)
        val cullX0 = -30f * k; val cullX1 = w + 30f * k; val cullY0 = -4f * k; val cullY1 = h + 30f * k
        for ((ti, tr) in trees.withIndex()) {
            val tx = tr[0]; val ty = tr[1]; val kind = tr[2].toInt(); val size = tr[3]; val seed = tr[4].toInt()
            if (landmarks.isNotEmpty() && landmarks.any { it.clears(tx, ty) }) continue
            if (camp != null && camp.clears(tx, ty)) continue
            if (k > 1) {
                val cxp = s.iso.sx(tx, ty); val cyp = s.iso.sy(tx, ty, 0f)
                if (cxp < cullX0 || cxp > cullX1 || cyp < cullY0 || cyp > cullY1) continue
            }
            val stage = stages[ti]
            objs.add(Obj(tx + ty) {
                val bx = s.iso.ix(tx, ty); val by = s.iso.iy(tx, ty, 0f)
                if (stage == null || stage == Regrowth.GROWN) {
                    shadowEllipse(s, bx + 2f * k, by.toFloat(), (if (kind == 2) 2f else 5f * size) * k, 1.8f * k)
                    when (kind) {
                        0 -> { s.newObject(Pal.OUTLINE_TREE); s.trees.pine(bx, by, size, seed) }
                        1 -> { s.newObject(Pal.OUTLINE_TREE); s.trees.broadleaf(bx, by, size, seed) }
                        2 -> { s.newObject(Pal.OUTLINE_TREE); s.trees.bush(bx, by, seed) }
                        else -> { s.newObject(Pal.OUTLINE); s.trees.rock(bx, by, seed) }
                    }
                } else when (stage) {
                    Regrowth.STUMP -> { s.newObject(Pal.OUTLINE); s.woods.stump(bx, by, seed, fresh = true) }
                    Regrowth.SAPLING -> { s.newObject(Pal.OUTLINE_TREE); s.woods.sapling(bx, by, seed) }
                    else -> {
                        shadowEllipse(s, bx + 1f * k, by.toFloat(), 2.5f * size * k, 1.2f * k)
                        s.newObject(Pal.OUTLINE_TREE)
                        if (kind == 0) s.trees.pine(bx, by, size * 0.5f, seed) else s.trees.broadleaf(bx, by, size * 0.5f, seed)
                    }
                }
            })
        }
        if (roadLeft) {
            // the road's bridge over the stream: the stream's place, as the water under it is
            val bxw = land.bridge[0]; val byw = land.bridge[1]
            objs.add(Obj(bxw + byw - 1f) {
                owner[s.newObject(Pal.OUTLINE)] = hits.size + 1
                bridge(s, bxw, byw)
                hits.add(VillageHit.OnSpot("riverbank") to intArrayOf(s.iso.sx(bxw - 1.2f, byw + 1.1f).toInt(), s.iso.sy(bxw, byw, 4f).toInt(), s.iso.sx(bxw + 1.2f, byw - 1.1f).toInt(), s.iso.sy(bxw + 1.2f, byw + 1.1f, 0f).toInt()))
            })
        }
        // the stream's near bank above the bridge: a place to listen (an area, not an object); and the water itself,
        // wherever it runs through the clearing, in small areas along it: a tap on the stream opens its place
        if (!frame.compact) {
            val wy = if (land.classic) 2.6f else land.riverbank[1] - 0.4f; val wx = land.streamX(wy)
            areas += VillageHit.OnSpot("riverbank") to floatArrayOf(s.iso.sx(wx, wy), s.iso.sy(wx, wy, 0f), 13f * k, 7f * k)
            var sy = -16f
            while (sy <= 28f) {
                val sx = land.streamX(sy)
                val dx = (sx - sy) * 4f; val dy = (sx + sy) * 2f
                // in and about the clearing, well below the horizon (the land there is the horizon's)
                if (dy > 8f - VillageLayout.HORIZON && VillageLayout.screenMetric(dx, dy) < land.forestEdge(age, VillageLayout.angle(dx, dy)) + 24f && !(land.water && land.wetScreen(dx, dy)))
                    areas += VillageHit.OnSpot("riverbank") to floatArrayOf(s.iso.sx(sx, sy), s.iso.sy(sx, sy, 0f), 6f * k, 3.5f * k)
                sy += 1.5f
            }
        }
        // the landing at the water and the stepping stones up from it (through the river gate): the way to the stream, a place to tap
        if (landing != null && riverGate != null && landmarks.none { it.covers(landing[0], landing[1], 0.8f) }) {
            objs.add(Obj(landing[0] + landing[1]) {
                owner[s.newObject(Pal.OUTLINE)] = hits.size + 1
                hits.add(VillageHit.OnSpot("riverbank") to riverLanding(s, landing, riverGate))
            })
        }

        addLandmarks(s, landmarks, painter, objs)
        if (palisade != null) addPalisade(s, state, palisade, fg, landmarks, painter, objs, horizon, frame)

        for (b in buildings) {
            val o = land.origin(b.plot)
            val x = o[0]; val y = o[1]
            val hgt = painter.height(b.type)
            val building = frame.justBuilt != null && frame.justBuilt == b.id && frame.justBuiltProgress < 1f
            // the building the plot chooser shows on the chosen plot: outlined in gold
            val outline = if (frame.plots != null && b.id == frame.plots.shown) Pal.GOLD_L else Pal.OUTLINE
            objs.add(Obj(x + y + 3f) {
                if (b.type != BuildingType.FIELD) shadowDiamond(s, x, y)
                owner[s.newObject(outline)] = hits.size + 1
                val baseY = s.iso.sy(x + 3f, y + 3f, 0f); val topY = s.iso.sy(x, y, hgt)
                if (building) {
                    val p = frame.justBuiltProgress.coerceIn(0f, 1f)
                    val r = smooth((p / 0.8f).coerceIn(0f, 1f))
                    canvas.clipTop = (baseY - r * (baseY - topY + 4 * k)).roundToInt()
                    painter.draw(b, x, y)
                    canvas.clipTop = 0
                    painter.scaffold(x, y, hgt * r, 1f - smooth(((p - 0.75f) / 0.25f).coerceIn(0f, 1f)))
                    s.fx { dust(s, x, y, p) }
                } else {
                    painter.draw(b, x, y)
                    if (b.damaged) painter.damage(b, x, y)
                }
                val lx = s.iso.sx(x, y + 3f).toInt(); val rx = s.iso.sx(x + 3f, y).toInt()
                hits.add(VillageHit.OnBuilding(b) to intArrayOf(lx, topY.toInt() - 2 * k, rx, baseY.toInt()))
            })
        }

        // fire
        val fire = state.fire.coerceIn(0, 100) / 100f
        val fireScale = TownAnchors.FIRE_SCALE[age.ordinal]
        objs.add(Obj(-0.01f) {
            val slot = hits.size + 1
            owner[s.newObject(Pal.OUTLINE)] = slot
            s.effects.hearth(fireX, fireY, fireScale, back = true)
            owner[s.newObject(0)] = slot
            val fh = s.effects.flames(fireX, fireY - k, fire, fireScale)
            owner[s.newObject(Pal.OUTLINE)] = slot
            s.effects.hearth(fireX, fireY, fireScale, back = false)
            s.sparks(fireX.toFloat(), fireY - fh * 0.8f, 3 + (fire * 6).toInt(), 1)
            s.smoke(fireX + 1f * k, fireY - fh - 2f * k, 0.5f + fire * 0.6f, 3)
            // the flames, the stones and a little more: a tap there is the fire's (see [hitTest])
            val reach = (TownAnchors.fireReach(fireScale) * k).roundToInt(); val front = (TownAnchors.fireFront(fireScale) * k).roundToInt()
            hits.add(VillageHit.OnFire to intArrayOf(fireX - reach, fireY - fh - 6 * k, fireX + reach, fireY + front))
        })

        // people: those who live here around their homes, the rest of the population walking about unnamed
        val placed = frame.placed ?: TownPeople.of(state, frame.people, frame.time, frame.hour, frame.month, bw, bh, storm, event == EventKind.FESTIVAL, frame.standing, frame.date, frame.day)
        addPeople(s, placed, comp, objs)
        // round the fire, the unnamed after the named (and a visitor waiting there): each in a place of their own, past the
        // named's places (they keep theirs while others come and go, so some round it may be free)
        val named = placed.filter { it.place == TownPlace.Fire }.maxOfOrNull { it.slot + 1 } ?: 0
        val atFire = named + if (!frame.compact && TownPlace.Fire in frame.visitors) 1 else 0
        addVillagers(s, state, (state.villagers - frame.people.count { it.art != "baby" }).coerceAtLeast(0), buildings, objs, event, atFire, bw, bh)
        if (!frame.compact) addVisitors(s, state, frame.visitors, objs, comp, bw, bh, named)

        // events
        when (event) {
            EventKind.WOLVES -> {
                val n = 2 + (state.event?.strength ?: 1).coerceIn(1, 5) / 2
                // with a palisade they gather outside it: at the gate of the path from the woods, or at the ring's lower left
                val ring = if (palisade != null) Palisade.ring(land, age, fg) else null
                val gate = ring?.gates?.firstOrNull { it.kind == PalisadeRing.Kind.PATH }?.let { ring.middle(it) }
                for (j in 0 until n) {
                    val step = ((s.time * 5).toInt() + j) and 1
                    if (fg != null) {
                        // out of the woods, up the path from the meadow towards the village (or its gate)
                        val py = if (gate != null) comp.fireY + (gate[0] + gate[1]) * 2f + 6f + j * 6f else fg.meadowY - fg.meadowRy - 3f - j * 9f
                        val px = fg.pathX(py) + (j % 2 * 2 - 1) * 4f + sin(s.time * 0.6 + j).toFloat() * 2f
                        objs.add(Obj((py - comp.fireY) / 2f) {
                            s.newObject(Pal.OUTLINE)
                            val ax = s.cx(px).roundToInt(); val ay = s.cy(py).roundToInt()
                            s.sprite(ax, ay) { s.effects.wolf(ax, ay, faceRight = j % 2 == 0, eyes = env.dark > 0.3f, step = step) }
                        })
                        continue
                    }
                    val a = PI * (0.66 + j * 0.1)
                    val p = dry(land, edgePoint(comp, if (ring != null) land.forestEdge(age, a.toFloat()) + 2f else clearR * 0.98f, a, bw, bh))
                    val wx = p[0] + sin(s.time * 0.6 + j).toFloat() * 0.8f; val wy = p[1] - j * 0.3f
                    objs.add(Obj(wx + wy) {
                        s.newObject(Pal.OUTLINE)
                        val ax = s.iso.ix(wx, wy); val ay = s.iso.iy(wx, wy, 0f)
                        s.sprite(ax, ay) { s.effects.wolf(ax, ay, faceRight = true, eyes = env.dark > 0.3f, step = step) }
                    })
                }
            }
            EventKind.BEAR -> {
                val field = buildings.firstOrNull { it.type == BuildingType.FIELD }
                val p = if (field != null) {
                    val c = land.centers[field.plot]; val len = sqrt(c[0] * c[0] + c[1] * c[1])
                    floatArrayOf(c[0] + c[0] / len * 3.2f, c[1] + c[1] / len * 3.2f)
                }
                else dry(land, edgePoint(comp, clearR * 0.85f, PI * 0.12, bw, bh))
                val bx = p[0] + sin(s.time * 0.5).toFloat() * 0.5f; val by = p[1]
                objs.add(Obj(bx + by) {
                    s.newObject(Pal.OUTLINE)
                    val ax = s.iso.ix(bx, by); val ay = s.iso.iy(bx, by, 0f)
                    s.sprite(ax, ay) { s.effects.bear(ax, ay, faceRight = false, step = ((s.time * 3).toInt()) and 1) }
                })
            }
            EventKind.MERCHANT -> {
                val u = (s.time / 7.0).coerceIn(0.0, 1.0).toFloat()
                val e = 1 - (1 - u) * (1 - u)
                // park just past the last building that would hide the road
                var stop = 6.5f
                for (b in buildings) { val c = land.centers[b.plot]; if (c[1] > land.roadY(c[0])) stop = max(stop, c[0] - c[1] + 8f) }
                stop = min(stop, (bw / 2f - 22f) / 4f)
                val cx = 30f - (30f - stop) * e; val cy = 0.1f + land.roadY(cx)
                objs.add(Obj(cx + cy + 0.6f) { s.newObject(Pal.OUTLINE); s.effects.cart(cx, cy, u < 1f) })
            }
            EventKind.FESTIVAL -> {
                val poles = arrayOf(floatArrayOf(-3.4f, -3.4f), floatArrayOf(3.4f, -3.4f), floatArrayOf(3.4f, 3.4f), floatArrayOf(-3.4f, 3.4f))
                for (p in poles) objs.add(Obj(p[0] + p[1]) {
                    s.newObject(Pal.OUTLINE)
                    s.iso.post(p[0], p[1], 0f, 16f, Pal.WOOD_D)
                    s.iso.px(p[0], p[1], 17f, Pal.FLAG_RED)
                })
                s.fx {
                    for (j in 0..3) {
                        val a = poles[j]; val b = poles[(j + 1) % 4]
                        s.effects.bunting(s.iso.sx(a[0], a[1]), s.iso.sy(a[0], a[1], 16f), s.iso.sx(b[0], b[1]), s.iso.sy(b[0], b[1], 16f), j, env.windows > 0.3f)
                    }
                }
            }
            else -> Unit
        }

        mark(3)
        objs.sortBy { it.depth }
        for (o in objs) o.draw()
        canvas.penId = 0
        mark(4)

        outlinePass(s)
        mark(5)

        // ---- effects on top
        canvas.penId = 0
        for (f in s.fxList) f()
        if (!frame.compact) s.effects.leaves(env.season, env.month)
        if (storm) s.effects.rain(horizon) else if (sky.rain > 0.02f) s.effects.rain(horizon, sky.rain)
        frame.highlightPlot?.let { if (it in 0 until VillageLayout.MAX_PLOTS) highlightMarker(s, it) }
        frame.plots?.let { plotMarkers(s, state, painter, it) }
        s.effects.fireworks(frame.celebrate, horizon)

        mark(6)
        // what of the sky is still to be seen (not behind the hills, the clouds, the rain), before the light greys it
        if (skyDrawn) night.settle()
        // ---- light
        val desat = ((55f - state.morale) / 55f).coerceIn(0f, 1f) * 0.45f
        lightingPass(s, desat)
        mark(7)
        if (profile) lastProfile = listOf("backdrop", "ground", "gather", "objects", "outline", "fx", "light").withIndex()
            .joinToString(" ") { (i, n) -> "%s %.1f".format(n, (marks[i + 1] - marks[i]) / 1e6) }
        if (storm || sky.lightning) {
            // when is the thunder's business too (game/ambient/Thunder.kt): the same flashes
            val lightning = Lightning.village(storm)
            val phase = (lightning.since(s.time) / lightning.period).toFloat()
            val bolt = lightning.index(s.time)
            if (phase < Lightning.VILLAGE_SHOWS) {
                s.effects.lightning(horizon, phase, bolt)
                flash(canvas, 0.55f * (1f - phase / Lightning.VILLAGE_SHOWS))
            }
        }
    }

    /**
     * What is drawn at a pixel position of the last frame (its canvas's pixels; see [lens]): exact pixels first,
     * then anywhere within the fire's flames and stones (its rectangle: the village's most important place), then the
     * nearest tappable pixel within a finger's reach ([slop] px), so small sprites stay easy to hit;
     * then the ground spots that are areas (the pond, the stream bank). The palisade, round everything, takes a tap
     * right on it, or one nothing else is within reach of. So do the wild animals, small and out at the edges: a tap
     * right on one, or within half the reach of one where nothing else (not even an area) is.
     */
    fun hitTest(x: Int, y: Int, slop: Int = 4): VillageHit? {
        val c = lastCanvas ?: return null
        val exact = if (x in 0 until c.width && y in 0 until c.height) owner[c.ids[y * c.width + x]] else 0
        if (exact != 0 && wild[exact]) return hits[exact - 1].first
        // the fire, the village's heart and the easiest to tap: anywhere within its flames, stones and smoke that isn't
        // right on someone or something drawn there is the fire's, before whoever stands beside it
        if (exact == 0 || hits[exact - 1].first == VillageHit.OnFire) hits.firstOrNull { it.first == VillageHit.OnFire }?.second?.let { r ->
            if (x in r[0]..r[2] && y in r[1]..r[3]) return VillageHit.OnFire
        }
        var bestSlot = 0; var bestD = Int.MAX_VALUE; var ringD = Int.MAX_VALUE
        var wildSlot = 0; var wildD = Int.MAX_VALUE
        val reach = max(1, slop / 2)
        for (dy in -slop..slop) for (dx in -slop..slop) {
            val px = x + dx; val py = y + dy
            if (px < 0 || py < 0 || px >= c.width || py >= c.height) continue
            val slot = owner[c.ids[py * c.width + px]]
            val d = dx * dx + dy * dy
            if (slot != 0 && slot == ringSlot) { if (d <= slop * slop) ringD = min(ringD, d); continue }
            if (slot != 0 && wild[slot]) { if (d <= reach * reach && d < wildD) { wildD = d; wildSlot = slot }; continue }
            if (slot != 0 && d < bestD && d <= slop * slop) { bestD = d; bestSlot = slot }
        }
        if (bestSlot != 0 && ringD > 0) return hits[bestSlot - 1].first
        if (ringD != Int.MAX_VALUE) return hits[ringSlot - 1].first
        for ((what, e) in areas) {
            val ddx = (x + 0.5f - e[0]) / e[2]; val ddy = (y + 0.5f - e[1]) / e[3]
            if (ddx * ddx + ddy * ddy <= 1f) return what
        }
        if (wildSlot != 0) return hits[wildSlot - 1].first
        return null
    }

    /**
     * What of the real sky the last frame drew is at nominal point ([x], [y]) (see [SkyTargets.at]): the moon, a star, a
     * planet, a constellation's line, the Milky Way or a shooting star, within [slop] nominal px, at [wall] epoch ms; null
     * for none, or a frame without the real sky.
     */
    fun skyAt(x: Float, y: Float, slop: Float, wall: Long): SkyTap? = if (skyDrawn) night.targets.at(x, y, slop, wall) else null

    /** What of the real sky the last frame shows, to open from a list (TalkBack's "Tonight's sky", [SkyTargets.up]). */
    fun skyUp(): List<SkyTap> = if (skyDrawn) night.targets.up() else emptyList()

    /** The real sky's tappable things of the last frame (tests). */
    val skyTargets: si.lanisce.lani.game.sky.SkyTargets get() = night.targets

    /** Last frame's building rectangles in internal pixels: (building or null for the fire) -> [x0, y0, x1, y1]. */
    fun hitRects(): List<Pair<Building?, IntArray>> =
        hits.filter { it.first is VillageHit.OnBuilding || it.first == VillageHit.OnFire }.map { (it.first as? VillageHit.OnBuilding)?.building to it.second }

    // ------------------------------------------------------------------ ground

    private class PathGrid {
        val n = 256
        val cells = BooleanArray(n * n)
        fun mark(x0: Float, y0: Float, x1: Float, y1: Float) {
            for (gy in floor(y0 * 2).toInt() until floor(y1 * 2).toInt() + 1) for (gx in floor(x0 * 2).toInt() until floor(x1 * 2).toInt()) {
                val ix = gx + n / 2; val iy = gy + n / 2
                if (ix in 0 until n && iy in 0 until n) cells[iy * n + ix] = true
            }
        }
        fun at(x: Float, y: Float): Boolean {
            val ix = floor(x * 2).toInt() + n / 2; val iy = floor(y * 2).toInt() + n / 2
            return ix in 0 until n && iy in 0 until n && cells[iy * n + ix]
        }
    }

    private fun groundPass(
        s: SceneCtx, horizon: Int, clearR: Float, roadLeft: Boolean, roadHalf: Float, plazaR: Float, age: Age, paths: PathGrid, fg: Foreground?,
    ) {
        val c = s.canvas; val env = s.env; val iso = s.iso; val lens = s.lens; val k = s.k
        val land = s.land
        // a generated land's water, rises, vineyards and torrent (the classic land has none of them)
        val lake = land.lake
        val seaOn = land.sea != null
        val relief = land.relief
        val vines = land.vineyards
        val torrent = land.torrent
        val sHalf = land.streamHalf; val sDeep = land.streamDeep; val sBank = land.streamBank
        // the side of the lake facing the fire, where the path along its shore runs (the fire's direction in the lake's radii)
        val lakeFx: Float; val lakeFy: Float
        if (lake != null) { val fx0 = -lake[0] / lake[2]; val fy0 = -lake[1] / lake[3]; val n = sqrt(fx0 * fx0 + fy0 * fy0); lakeFx = fx0 / n; lakeFy = fy0 / n } else { lakeFx = 0f; lakeFy = 0f }
        if (relief > 0f) reliefFor(land)
        val g = env.grass; val ff = env.forestFloor
        val cobbled = age.ordinal >= Age.TRG.ordinal
        val waterT = (s.time * 3).toInt()
        val autumnFloor = !env.snow && env.season == Season.AUTUMN
        val bank = if (env.snow) Pal.SNOW_D else Col.hex(0x4E7A3A)
        val kinds = s.ground
        // the path south: its centre column per row
        val fgTop = if (fg == null) c.height else max(horizon, lens.cy(fg.pathTop).toInt())
        if (fg != null) {
            if (pathXs.size != c.height) pathXs = FloatArray(c.height)
            for (y in max(fgTop, 0) until c.height) pathXs[y] = lens.cx(fg.pathX(lens.by(y + 0.5f)))
        }
        c.penId = 0; c.penEmissive = false
        val nominal = lens.nominal
        val ux = 4f * k; val uy = 2f * k
        for (y in max(horizon, 0) until c.height) {
            val dy = (y + 0.5f - iso.oy) / k
            val b = (y + 0.5f - iso.oy) / uy
            // the sea's shore on this row
            val shore = if (seaOn) land.shoreX(dy) else 0f
            val byf = if (nominal) y + 0.5f else lens.by(y + 0.5f)
            val byi = if (nominal) y else floor(byf).toInt()
            val woods = y >= fgTop
            for (x in 0 until c.width) {
                val dx = (x + 0.5f - iso.ox) / k
                val bxf = if (nominal) x + 0.5f else lens.bx(x + 0.5f)
                val bxi = if (nominal) x else floor(bxf).toInt()
                val a = (x + 0.5f - iso.ox) / ux
                val wx = (a + b) / 2f; val wy = (b - a) / 2f
                val jag = (Noise.rnd(x, y, 3) - 0.5f) * 0.35f
                var col: Int
                var kind = Ground.OTHER
                val streamD = abs(wx - land.streamX(wy))
                // across the road (cells from its middle, where it bends)
                val rw = wy - land.roadY(wx)
                val onRoad = abs(rw) < roadHalf + jag && (wx > 0f || roadLeft)
                val plaza = wx * wx + wy * wy < (plazaR + jag) * (plazaR + jag)
                // the woods below: the pond, the path and the meadow
                var pond = 2f
                var path = false
                var meadow = 2f
                if (woods) {
                    pond = fg!!.pondD(bxf, byf)
                    path = pond >= 1f && abs(x + 0.5f - pathXs[y]) < (fg.pathHalf + jag * 4f) * k
                    if (!path && pond >= 1f) meadow = fg.meadowD(bxf, byf)
                }
                // the lake and the sea
                val lakeD = if (lake != null) land.lakeD(dx, dy) else 9f
                val seaD = if (seaOn) dx - shore else 1e9f
                val shorePath = lake != null && lakeD > 1.2f && lakeD < 1.34f + jag * 0.2f &&
                    ((dx - lake[0]) / lake[2] * lakeFx + (dy - lake[1]) / lake[3] * lakeFy) / lakeD > -0.25f
                if (seaD < 0f) {
                    // the sea: surf at the beach, lighter over the shallows, deeper further out, glints drifting on it
                    col = when {
                        seaD > -1.6f + jag * 2f && (Noise.rnd(byi, waterT / 2, 23) < 0.7f) -> SURF
                        seaD > -6f -> SHALLOWS
                        Noise.rnd(bxi / 2, byi, waterT + bxi / 7) < 0.08f -> Pal.WATER_L
                        seaD < -40f -> SEA_DEEP
                        else -> Pal.WATER_M
                    }
                    kind = Ground.WATER
                } else if (streamD < sHalf + jag * 0.5f || pond < 1f || lakeD < 1f) {
                    val deep = if (pond < 1f) pond < 0.82f else if (lakeD < 1f) lakeD < 0.88f else streamD <= sDeep
                    col = if (env.snow) (if (Noise.rnd(x, y, 8) < 0.2f) Pal.ICE_L else Pal.ICE_M)
                    else when {
                        !deep -> Pal.WATER_D
                        Noise.rnd(bxi / 2, byi, waterT + bxi / 7) < 0.1f -> Pal.WATER_L
                        else -> Pal.WATER_M
                    }
                    // the torrent breaks white over its stones
                    if (torrent && !env.snow && pond >= 1f && lakeD >= 1f && Noise.rnd(bxi, byi / 2, waterT * 3 + 17) < 0.2f) col = SURF
                    kind = Ground.WATER
                } else if (plaza && cobbled) {
                    val fx = wx * 2 - floor(wx * 2); val fy = wy * 2 - floor(wy * 2)
                    col = if (fx < 0.16f || fy < 0.16f) Pal.COBBLE_D else if (Noise.rnd(floor(wx * 2).toInt(), floor(wy * 2).toInt(), 5) < 0.5f) Pal.COBBLE_L else Pal.COBBLE_M
                    if (env.snow && Noise.rnd(x, y, 6) < 0.55f) col = Pal.SNOW_M
                    kind = Ground.COBBLE
                } else if (onRoad || plaza || path || shorePath || paths.at(wx + jag * 0.5f, wy + jag * 0.5f)) {
                    val r = Noise.rnd(x, y, 4)
                    col = if (r < 0.12f) Pal.DIRT_L else if (r > 0.9f) Pal.DIRT_D else Pal.DIRT_M
                    if (path && k == 1 && r > 0.97f) col = Pal.STONE_L // a stone in the path (the detail pass lays bigger ones)
                    if (env.snow) col = Col.mix(col, Pal.SNOW_D, 0.55f)
                    if (onRoad && !plaza && abs(abs(rw) - roadHalf * 0.5f) < 0.12f && roadHalf > 0.8f) col = Col.scale(col, 0.88f)
                    kind = Ground.DIRT
                } else {
                    val m = VillageLayout.screenMetric(dx, dy)
                    val ang = VillageLayout.angle(dx, dy)
                    val edge = land.forestEdge(age, ang)
                    var forest = (m - edge) / 12f
                    if (meadow < 1.4f) forest = min(forest, (meadow - 1f) * 5f)
                    // the shores are open grass: no woods by the water
                    if (lakeD < 1.5f || seaD < Terrain.BEACH + 12f) forest = min(forest, -1f)
                    val cxI = floor(wx).toInt(); val cyI = floor(wy).toInt()
                    var tone = Noise.rnd(cxI, cyI, 1)
                    // the land's rises: the grass lighter on the slopes facing the light, darker away from it
                    if (relief > 0f) tone += relief * slopeAt(cxI, cyI)
                    val r = Noise.rnd(x, y, 2)
                    col = if (forest > Dither.at(x, y)) {
                        kind = Ground.FLOOR
                        if (r < 0.1f) ff[2] else if (tone < 0.5f) ff[0] else ff[1]
                    } else {
                        kind = Ground.GRASS
                        var gc = if (tone < 0.22f) g[0] else if (tone < 0.84f) g[1] else g[2]
                        if (r < 0.05f) gc = g[3] else if (r > 0.95f) gc = g[2]
                        gc
                    }
                    if (streamD < sBank) { col = Col.mix(col, bank, 0.4f); kind = Ground.OTHER }
                    if (pond < 1.25f) { col = if (pond < 1.12f && Noise.rnd(x, y, 10) < 0.3f) (if (r < 0.5f) Pal.STONE_L else Pal.STONE_M) else Col.mix(col, bank, 0.4f); kind = Ground.OTHER }
                    if (lakeD < 1.16f) { col = if (lakeD < 1.07f && Noise.rnd(x, y, 10) < 0.3f) (if (r < 0.5f) Pal.STONE_L else Pal.STONE_M) else Col.mix(col, bank, 0.4f); kind = Ground.OTHER }
                    // the beach: sand, darker where the waves wet it, snow over it in winter
                    if (seaD < Terrain.BEACH + jag * 4f) {
                        col = if (seaD < 1.8f) SAND_WET else if (r < 0.18f) SAND_D else if (r > 0.9f) SAND_L else SAND_M
                        if (env.snow && seaD > 2f) col = Col.mix(col, Pal.SNOW_M, 0.6f)
                        kind = Ground.OTHER
                    }
                    // the vineyards' terraces on the slopes behind the village, where the woods would be
                    if (vines && forest > 0f && m < edge + 90f && land.vineyard(ang)) { col = terrace(s, land, dx, dy, x); kind = Ground.OTHER }
                    // tiny flowers in spring/summer meadows; fallen leaves on the forest floor in autumn
                    if (!env.snow && forest < 0f && env.month in 4..8 && Noise.rnd(bxi, byi, 9) < 0.006f) col = Pal.PAINTED[(bxi + byi) % 6]
                    else if (autumnFloor && forest > 0.5f && Noise.rnd(bxi, byi, 9) < 0.035f) col = LEAF_LITTER[(bxi + byi).mod(3)]
                }
                val i = y * c.width + x
                c.pixels[i] = col; c.ids[i] = 0; c.emissive[i] = false
                kinds[i] = kind
            }
        }
    }

    /** The light on the land's slopes by world cell (see [Terrain.slope]), for the land [reliefKey] is of: NaN not yet asked. */
    private val reliefGrid = FloatArray(RELIEF_N * RELIEF_N)
    private var reliefKey = ""
    private var reliefLand: Terrain = Terrain.CLASSIC

    /** Makes the relief's cache [land]'s. */
    private fun reliefFor(land: Terrain) {
        if (land.key == reliefKey) return
        reliefKey = land.key; reliefLand = land
        reliefGrid.fill(Float.NaN)
    }

    /** [Terrain.slope] at world cell ([cx], [cy]) of the land [reliefFor] was last given, cached. */
    private fun slopeAt(cx: Int, cy: Int): Float {
        val ix = cx + RELIEF_N / 2; val iy = cy + RELIEF_N / 2
        if (ix !in 0 until RELIEF_N || iy !in 0 until RELIEF_N) return reliefLand.slope(cx, cy)
        val i = iy * RELIEF_N + ix
        val v = reliefGrid[i]
        if (!v.isNaN()) return v
        return reliefLand.slope(cx, cy).also { reliefGrid[i] = it }
    }

    /**
     * A vineyard's terraces at nominal ([dx], [dy]) from the fire (see [Terrain.vineyard]): rows of vines across the slope,
     * earth between them, a dry-stone wall under every third row; the leaves green from spring, gold and red in autumn,
     * the bare stocks on the snow in winter.
     */
    private fun terrace(s: SceneCtx, land: Terrain, dx: Float, dy: Float, x: Int): Int {
        val env = s.env
        val t = dy + Noise.v1(dx * 0.03f, land.vineSeed) * 6f
        val row = floor(t / 3f).toInt(); val f = t - row * 3f
        val odd = (x / s.k + row).mod(3) == 0
        return when {
            row.mod(3) == 0 && f < 0.9f -> if (env.snow) Pal.SNOW_D else if (odd) Pal.STONE_M else Pal.STONE_D
            f < 1.7f -> when {
                env.snow || env.season == Season.WINTER -> if (odd) VINE_STOCK else if (env.snow) Pal.SNOW_L else VINE_EARTH
                env.season == Season.AUTUMN -> if (odd) VINE_RED else VINE_GOLD
                env.season == Season.SPRING -> if (odd) VINE_DARK else VINE_SPRING
                else -> if (odd) VINE_DARK else VINE_LEAF
            }
            else -> if (env.snow) Pal.SNOW_M else VINE_EARTH
        }
    }

    /**
     * What the extra pixels of a closer look allow on the ground (k ≥ 2): tufts of grass, stones on the paths and
     * roads, needles and twigs on the forest floor, ripples on the water; one per nominal pixel at most, by the
     * same noise at every level, so zooming in only adds.
     */
    private fun groundDetail(s: SceneCtx, horizon: Int) {
        val c = s.canvas; val lens = s.lens; val k = s.k; val env = s.env
        val kinds = s.ground; val w = c.width; val h = c.height
        val grassD = Col.scale(env.grass[2], 0.85f); val grassL = env.grass[3]
        val floorD = Col.scale(env.forestFloor[2], 0.8f)
        val ripple = if (env.snow) Pal.ICE_L else Pal.WATER_L
        val waterT = (s.time * 2).toInt()
        val by0 = lens.by(max(horizon, 0).toFloat()).toInt(); val by1 = lens.by(h.toFloat()).toInt() + 1
        val bx0 = lens.bx(0f).toInt(); val bx1 = lens.bx(w.toFloat()).toInt() + 1
        c.penId = 0; c.penEmissive = false
        for (by in by0..by1) for (bx in bx0..bx1) {
            val x = lens.cx(bx.toFloat()).toInt(); val y = lens.cy(by.toFloat()).toInt()
            if (x < 0 || y < 0 || x >= w || y >= h) continue
            when (kinds[y * w + x]) {
                Ground.GRASS -> if (!env.snow && Noise.rnd(bx, by, 41) < 0.05f) {
                    // a tuft: two or three blades leaning apart, a lighter one in the middle
                    val tall = k + (Noise.hash(bx, by, 43) ushr 5) % 2
                    c.vline(x, y - tall, y, grassD)
                    c.vline(x + k - 1, y - tall + 1, y, grassL)
                    if (k >= 3) c.vline(x - 1, y - tall + 1, y, grassD)
                }
                Ground.FLOOR -> if (!env.snow && Noise.rnd(bx, by, 41) < 0.04f) {
                    // a twig or a cone on the needles
                    if ((bx + by) % 2 == 0) c.hline(x, x + k, y, floorD) else c.set(x, y, floorD)
                }
                Ground.DIRT -> if (Noise.rnd(bx, by, 42) < 0.035f) {
                    // a stone: light on top, shaded underneath
                    val sw = k + (Noise.hash(bx, by, 44) ushr 4) % 2
                    c.fillRect(x, y, sw, k - 1 + (k / 3), Pal.STONE_M)
                    c.hline(x, x + sw - 2, y, Pal.STONE_L)
                    c.hline(x + 1, x + sw - 1, y + k - 1 + (k / 3) - 1, Pal.STONE_D)
                }
                Ground.WATER -> if (!env.snow && (bx + waterT + by * 3).mod(11) == 0 && Noise.rnd(bx, by, 45) < 0.5f) {
                    c.hline(x, x + 2 * k - 1, y, ripple)
                }
                else -> Unit
            }
        }
    }

    private fun shadowDiamond(s: SceneCtx, x: Float, y: Float) {
        val c = s.canvas; val iso = s.iso
        c.polyBegin()
        c.polyAdd(iso.sx(x + 0.3f, y + 0.2f), iso.sy(x + 0.3f, y + 0.2f, 0f))
        c.polyAdd(iso.sx(x + 3.5f, y + 0.2f), iso.sy(x + 3.5f, y + 0.2f, 0f))
        c.polyAdd(iso.sx(x + 3.5f, y + 3.0f), iso.sy(x + 3.5f, y + 3.0f, 0f))
        c.polyAdd(iso.sx(x + 0.3f, y + 3.0f), iso.sy(x + 0.3f, y + 3.0f, 0f))
        c.polyScan { px, py -> c.shadeGround(px, py, 0.8f) }
    }

    private fun shadowEllipse(s: SceneCtx, cx: Float, cy: Float, rx: Float, ry: Float) {
        val c = s.canvas
        for (y in (cy - ry).toInt()..(cy + ry).toInt()) for (x in (cx - rx).toInt()..(cx + rx).toInt()) {
            val dx = (x + 0.5f - cx) / rx; val dy = (y + 0.5f - cy) / ry
            if (dx * dx + dy * dy <= 1f) c.shadeGround(x, y, 0.82f)
        }
    }

    /**
     * The landing at the stream ([p], world cells; [gate]: the top of the stones, the river gate's middle or a spot inside
     * the ring, see [Palisade.footpath]): a few planks on two posts reaching over the water's edge, and flat stepping stones
     * up the footpath. Returns what it covers on the canvas (left, top, right, bottom).
     */
    private fun riverLanding(s: SceneCtx, p: FloatArray, gate: FloatArray): IntArray {
        val i = s.iso
        val snow = s.env.snow
        // the stepping stones, from the top (inside the gate) down to the landing
        var x = gate[0] + 1.2f
        var j = 0
        while (x > p[0] + 0.8f) {
            val y = p[1] + if (j % 2 == 0) -0.1f else 0.12f
            i.box(x - 0.22f, y - 0.2f, 0f, 0.44f, 0.4f, 0.6f, if (snow) Pal.SNOW_L else Pal.STONE_L, Pal.STONE_M, Pal.STONE_D)
            x -= 0.75f; j++
        }
        // the landing: planks along the bank, half over the water, two posts at its outer corners
        val x0 = p[0] - 0.6f; val y0 = p[1] - 0.55f
        i.box(x0, y0, 0f, 0.95f, 1.1f, 1f, if (snow) Pal.SNOW_L else Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        if (s.k > 1 && !snow) for (n in 1..2) i.line(x0 + 0.1f, y0 + n * 0.37f, 1f, x0 + 0.95f, y0 + n * 0.37f, 1f, Pal.WOOD_M)
        i.post(x0, y0, 0f, 3f, Pal.WOOD_D); i.post(x0, y0 + 1.1f, 0f, 3f, Pal.WOOD_D)
        val l = i.sx(x0, y0 + 1.1f); val r = i.sx(gate[0] + 1.45f, p[1] - 0.3f)
        val t = min(i.sy(x0, y0, 3f), i.sy(gate[0] + 1.45f, p[1] - 0.3f, 1f)); val b = i.sy(x0 + 0.95f, y0 + 1.1f, 0f)
        return intArrayOf(l.toInt(), t.toInt(), r.toInt() + 1, b.toInt() + 1)
    }

    /** The road's bridge over the stream at world ([sx], [sy]) (see [Terrain.bridge]). */
    private fun bridge(s: SceneCtx, sx: Float, sy: Float) {
        val i = s.iso
        val n = sy - 1.1f; val f = sy + 1.1f
        i.box(sx - 1.2f, n, 0.5f, 2.4f, 2.2f, 1f, Pal.WOOD_L, Pal.WOOD_M, Pal.WOOD_D)
        for (k in 0..4) i.line(sx - 1.2f + k * 0.5f, n, 1.5f, sx - 1.2f + k * 0.5f, f, 1.5f, Pal.WOOD_M)
        if (s.k > 1) for (k in 0..8) i.line(sx - 1.2f + k * 0.25f + 0.125f, n, 1.5f, sx - 1.2f + k * 0.25f + 0.125f, f, 1.5f, Col.mix(Pal.WOOD_L, Pal.WOOD_M, 0.5f))
        i.post(sx - 1.1f, f, 1.5f, 4f, Pal.WOOD_D); i.post(sx + 1.1f, f, 1.5f, 4f, Pal.WOOD_D)
        i.line(sx - 1.1f, f, 4f, sx + 1.1f, f, 4f, Pal.WOOD_L)
        if (s.k > 1) { i.line(sx - 1.1f, f, 2.8f, sx + 1.1f, f, 2.8f, Pal.WOOD_M); i.post(sx, f, 1.5f, 4f, Pal.WOOD_D) }
    }

    private fun highlightGround(s: SceneCtx, plot: Int) {
        val o = s.land.origin(plot)
        val pulse = 0.5f + 0.5f * sin(s.time * 5).toFloat()
        val c = s.canvas; val iso = s.iso
        c.polyBegin()
        c.polyAdd(iso.sx(o[0], o[1]), iso.sy(o[0], o[1], 0f)); c.polyAdd(iso.sx(o[0] + 3, o[1]), iso.sy(o[0] + 3, o[1], 0f))
        c.polyAdd(iso.sx(o[0] + 3, o[1] + 3), iso.sy(o[0] + 3, o[1] + 3, 0f)); c.polyAdd(iso.sx(o[0], o[1] + 3), iso.sy(o[0], o[1] + 3, 0f))
        c.polyScan { x, y -> c.blend(x, y, Pal.GOLD_L, 0.2f + 0.2f * pulse) }
    }

    /**
     * The plot chooser's ground (see [PlotMarks]): every free plot to choose from lit a little and framed at its corners
     * (glowing at night too), the chosen one glowing gold. Under everything standing, so a building in front still hides it.
     */
    private fun plotGround(s: SceneCtx, m: PlotMarks) {
        val c = s.canvas; val iso = s.iso
        val frameCol = Col.hex(0xFFF1C4)
        for (p in m.free) {
            if (p == m.chosen || p !in 0 until VillageLayout.MAX_PLOTS) continue
            val o = s.land.origin(p)
            val x0 = o[0]; val y0 = o[1]
            c.polyBegin()
            c.polyAdd(iso.sx(x0, y0), iso.sy(x0, y0, 0f)); c.polyAdd(iso.sx(x0 + 3, y0), iso.sy(x0 + 3, y0, 0f))
            c.polyAdd(iso.sx(x0 + 3, y0 + 3), iso.sy(x0 + 3, y0 + 3, 0f)); c.polyAdd(iso.sx(x0, y0 + 3), iso.sy(x0, y0 + 3, 0f))
            c.polyScan { x, y -> c.blend(x, y, Pal.GOLD_L, 0.18f) }
            // its corners, like a frame: a bracket of 0.9 cells along both edges from each
            c.penEmissive = true
            for ((cx, cy) in listOf(0f to 0f, 3f to 0f, 3f to 3f, 0f to 3f)) {
                val dx = if (cx == 0f) 0.9f else -0.9f; val dy = if (cy == 0f) 0.9f else -0.9f
                iso.line(x0 + cx, y0 + cy, 0f, x0 + cx + dx, y0 + cy, 0f, frameCol)
                iso.line(x0 + cx, y0 + cy, 0f, x0 + cx, y0 + cy + dy, 0f, frameCol)
            }
            c.penEmissive = false
        }
        m.chosen?.let { if (it in 0 until VillageLayout.MAX_PLOTS) highlightGround(s, it) }
    }

    /**
     * The plot chooser's pointers, over everything: the chosen plot framed and pointed at from above the building shown
     * there, and a ★ over the plot the game suggests (above the pointer when it is the chosen one).
     */
    private fun plotMarkers(s: SceneCtx, state: GameState, painter: BuildingPainter, m: PlotMarks) {
        val shown = m.shown?.let { id -> state.buildings.firstOrNull { it.id == id }?.type }
        val lift = max(16f, (shown?.let { painter.height(it) } ?: 0f) + 7f)
        m.chosen?.let { if (it in 0 until VillageLayout.MAX_PLOTS) highlightMarker(s, it, lift) }
        m.suggested?.let { if (it in 0 until VillageLayout.MAX_PLOTS) star(s, it, if (it == m.chosen) lift + 9f else 9f) }
    }

    /** A gold ★ floating [lift] px over [plot]'s middle, bobbing a little: the plot the game suggests. */
    private fun star(s: SceneCtx, plot: Int, lift: Float) {
        val o = s.land.origin(plot)
        val c = s.canvas; val iso = s.iso
        val ax = iso.ix(o[0] + 1.5f, o[1] + 1.5f)
        val ay = iso.iy(o[0] + 1.5f, o[1] + 1.5f, lift) + (sin(s.time * 3 + 1) * 1.5).roundToInt() * s.k
        c.penEmissive = true
        s.sprite(ax, ay) {
            // the star's rows (dx from, dx to) from its top point down to its two feet; a dark rim round it first
            for ((dy, spans) in STAR.withIndex()) for ((a, b) in spans) {
                c.hline(ax + a - 1, ax + b + 1, ay + dy - 4, Pal.OUTLINE)
                c.hline(ax + a, ax + b, ay + dy - 5, Pal.OUTLINE)
                c.hline(ax + a, ax + b, ay + dy - 3, Pal.OUTLINE)
            }
            for ((dy, spans) in STAR.withIndex()) for ((a, b) in spans) c.hline(ax + a, ax + b, ay + dy - 4, if (dy < 3) Pal.GOLD_L else Pal.GOLD)
        }
        c.penEmissive = false
    }

    private fun highlightMarker(s: SceneCtx, plot: Int, lift: Float = 16f) {
        val o = s.land.origin(plot)
        val pulse = 0.5f + 0.5f * sin(s.time * 5).toFloat()
        val c = s.canvas; val iso = s.iso
        val col = Col.mix(Pal.GOLD, Col.hex(0xFFFFFF), pulse)
        c.penEmissive = true
        val x0 = o[0]; val y0 = o[1]
        iso.line(x0, y0, 0f, x0 + 3, y0, 0f, col); iso.line(x0 + 3, y0, 0f, x0 + 3, y0 + 3, 0f, col)
        iso.line(x0 + 3, y0 + 3, 0f, x0, y0 + 3, 0f, col); iso.line(x0, y0 + 3, 0f, x0, y0, 0f, col)
        val ax = iso.ix(x0 + 1.5f, y0 + 1.5f); val ay = iso.iy(x0 + 1.5f, y0 + 1.5f, lift) + (sin(s.time * 4) * 2).roundToInt() * s.k
        s.sprite(ax, ay) {
            for (r in 0..3) c.hline(ax - 3 + r, ax + 3 - r, ay + r, if (r == 0) Pal.GOLD_L else Pal.GOLD)
            c.vline(ax, ay - 4, ay - 1, Pal.GOLD); c.vline(ax - 1, ay - 4, ay - 1, Pal.GOLD_L)
        }
        c.penEmissive = false
    }

    private fun dust(s: SceneCtx, x: Float, y: Float, p: Float) {
        if (p >= 1f) return
        val iso = s.iso
        for (k in 0 until 7) {
            val a = k * 2 * PI / 7 + p * 2
            val wx = x + 1.5f + cos(a).toFloat() * (1.6f + p * 1.2f); val wy = y + 1.5f + sin(a).toFloat() * (1.6f + p * 1.2f)
            s.canvas.ditherCircle(iso.sx(wx, wy), iso.sy(wx, wy, 1f + p * 5f), (1.5f + p * 3f) * s.k, Col.hex(0xD8C8A8), (1f - p) * 0.9f)
        }
    }

    // ------------------------------------------------------------------ trees

    /** The village projects' landmarks on this canvas (see [LandmarkLayout]): looked up again only for a new state or canvas. */
    private fun landmarksFor(state: GameState, w: Int, h: Int, compact: Boolean): List<LandmarkSpot> {
        val size = (w.toLong() shl 32) or (h.toLong() shl 1) or (if (compact) 1L else 0L)
        if (state !== lmState || size != lmSize) { lmState = state; lmSize = size; lmSpots = LandmarkLayout.of(state, w, h, compact) }
        return lmSpots
    }

    /** The woods below the village of [land] for this age and canvas, or null; cached, it's asked for every frame. */
    private fun foregroundFor(land: Terrain, age: Age, w: Int, h: Int): Foreground? {
        val key = "${land.key}/$age/$w/$h"
        if (key != fgKey) { fgKey = key; fgCache = Foreground.of(age, w, h, land) }
        return fgCache
    }

    /**
     * Cached tree/bush/rock placements on the nominal canvas: [x, y, kind(0 pine,1 broadleaf,2 bush,3 rock),
     * size, seed]; with them, which trees can be felled ([choppable]): the trees near the woodpile, nearest first.
     */
    private fun treesFor(state: GameState, w: Int, h: Int, compact: Boolean, comp: Composition, horizon: Int, clearR: Float, roadLeft: Boolean, fg: Foreground?): List<FloatArray> {
        val age = state.age
        val land = VillageLayout.of(state)
        val key = "${land.key}/$age/$w/$h/$compact"
        if (key == treeKey) return treeCache
        val fx = comp.fireX; val fy = comp.fireY
        // the land's own noise (the classic land's is 0: its trees stand where they always did)
        val salt = land.treeSalt
        val out = ArrayList<FloatArray>()
        val sp = 2.2f
        val reserved = PLOTS_PER_AGE[age.ordinal]
        // the grid of the world under this canvas's ground (a tree's place is its grid cell's, whatever the canvas)
        val sx0 = -10f - fx; val sx1 = w + 10f - fx; val sy0 = horizon + 2f - fy; val sy1 = h + 20f - fy
        val i0 = floor(((sx0 / 4f + sy0 / 2f) / 2f - 1f) / sp).toInt(); val i1 = ceil(((sx1 / 4f + sy1 / 2f) / 2f + 1f) / sp).toInt()
        val j0 = floor(((sy0 / 2f - sx1 / 4f) / 2f - 1f) / sp).toInt(); val j1 = ceil(((sy1 / 2f - sx0 / 4f) / 2f + 1f) / sp).toInt()
        for (j in j0..j1) for (i in i0..i1) {
            val wx = i * sp + (Noise.rnd(i, j, 21 + salt) - 0.5f) * 1.4f
            val wy = j * sp + (Noise.rnd(i, j, 22 + salt) - 0.5f) * 1.4f
            val sx = (wx - wy) * 4f; val sy = (wx + wy) * 2f
            val X = fx + sx; val Y = fy + sy
            if (X < -10 || X > w + 10 || Y < horizon + 2 || Y > h + 20) continue
            val roadFront = if (wx > 1.5f) 3.4f else 1.7f
            val rw = wy - land.roadY(wx)
            if (rw > -1.7f && rw < roadFront && (wx > 1.5f || roadLeft)) continue
            if (abs(wx - land.streamX(wy)) < 1.8f) continue
            // nothing in the lake, the sea or on the beach, and a strip of open shore
            if (land.water && land.wetScreen(sx, sy, 12f)) continue
            val m = VillageLayout.screenMetric(sx, sy)
            val ang = atan2(sy * 2.4f, sx)
            val edge = land.forestEdge(age, ang)
            // the vineyards' terraces where the woods would be
            if (land.vineyards && m > edge + 2f && m < edge + 90f && land.vineyard(ang)) continue
            val seed = Noise.hash(i, j, 23 + salt) ushr 1
            if (m < edge + 4f) {
                // meadow decoration, away from plots and the fire
                if (m < 30f) continue
                var onPlot = false
                for (k in 0 until reserved) {
                    val p = land.centers[k]
                    if (abs(wx - p[0]) < 2.3f && abs(wy - p[1]) < 2.3f) { onPlot = true; break }
                }
                if (onPlot) continue
                val r = Noise.rnd(i, j, 24 + salt)
                if (r < 0.05f) out.add(floatArrayOf(wx, wy, 2f, 1f, seed.toFloat()))
                else if (r < land.rocksMeadow) out.add(floatArrayOf(wx, wy, 3f, 1f, seed.toFloat()))
                continue
            }
            if (fg != null && !fg.treeFree(X.toFloat(), Y.toFloat())) {
                // the small meadow gets a few bushes and rocks of its own, the path some stones at its sides
                val r = Noise.rnd(i, j, 24 + salt)
                if (fg.meadowD(X.toFloat(), Y.toFloat()) < 0.8f) {
                    if (r < 0.07f) out.add(floatArrayOf(wx, wy, 2f, 1f, seed.toFloat()))
                    else if (r < 0.12f) out.add(floatArrayOf(wx, wy, 3f, 1f, seed.toFloat()))
                } else if (fg.onPath(X.toFloat(), Y.toFloat(), slack = 3.5f) && !fg.onPath(X.toFloat(), Y.toFloat(), slack = 1f) && r < 0.2f) {
                    out.add(floatArrayOf(wx, wy, 3f, 1f, seed.toFloat()))
                }
                continue
            }
            if (m < edge + 14f && Noise.rnd(i, j, 25 + salt) < land.thin) continue
            val deep = (m - edge) / 160f
            // glades deep in the woods: the ground shows, with a bush or a rock
            if (deep > 0.25f && Noise.v2(wx * 0.09f, wy * 0.09f, 28 + salt) > land.glades) {
                val r = Noise.rnd(i, j, 24 + salt)
                if (r < 0.12f) out.add(floatArrayOf(wx, wy, 2f, 1f, seed.toFloat()))
                else if (r < land.rocksGlade) out.add(floatArrayOf(wx, wy, 3f, 1f, seed.toFloat()))
                continue
            }
            // spruce takes over with depth, but never the whole wood: beech patches keep their autumn colour
            val pineBias = Noise.v2(wx * 0.12f, wy * 0.12f, 26 + salt) + min(deep, 0.42f)
            val kind = if (pineBias > land.spruce) 0f else 1f
            val size = 0.85f + Noise.rnd(i, j, 27 + salt) * 0.35f + min(deep, 0.6f) * 0.25f
            out.add(floatArrayOf(wx, wy, kind, size, seed.toFloat()))
        }
        treeKey = key; treeCache = out
        // the trees that can be felled: the ones within reach of the woodpile, nearest first
        val pile = if (compact) null else TownAnchors.spotAnchor(state, w, h, "woodpile")
        choppable = if (pile == null) IntArray(0) else out.indices
            .filter { out[it][2] < 2f }
            .map { it to hypot(fx + (out[it][0] - out[it][1]) * 4f - pile[0], fy + (out[it][0] + out[it][1]) * 2f - pile[1]) }
            .filter { it.second < CHOP_REACH }
            .sortedBy { it.second }
            .map { it.first }
            .toIntArray()
        return out
    }

    /** The regrowth stage of every felled tree, by tree index: the n-th mark takes the n-th choppable tree; the newest mark wins. */
    private fun stagesFor(state: GameState, today: String) {
        stages.clear()
        if (choppable.isEmpty() || today.isEmpty()) return
        for ((n, mark) in state.world.felled.withIndex()) {
            val stage = Regrowth.of(Regrowth.days(mark, today))
            val tree = choppable[n % choppable.size]
            if (stage == Regrowth.GROWN) stages.remove(tree) else stages[tree] = stage
        }
    }

    /**
     * The small things at the clearing's edge that the named spots point at (see [TownAnchors.SPOTS]): a rock
     * cluster at "rocks" (shrinking as stone is quarried, a quarry face growing behind it), and (a canvas without
     * the woods below) a small woodpile at "woodpile". Someone standing at the spot stands in front of them. Both
     * are tappable places.
     */
    private fun addEdgeSpots(s: SceneCtx, state: GameState, w: Int, h: Int, woodpile: Boolean, objs: ArrayList<Obj>, felled: Int, quarried: Int) {
        val fireY = s.lens.baseH.let { VillageLayout.composition(w, h, false).fireY }
        TownAnchors.spotAnchor(state, w, h, "rocks")?.let { p ->
            objs.add(Obj((p[1] - 3f - fireY) / 2f) {
                owner[s.newObject(Pal.OUTLINE)] = hits.size + 1
                val ax = s.cx(p[0].toFloat()).roundToInt(); val ay = s.cy(p[1].toFloat()).roundToInt()
                s.woods.rocks(ax, ay, quarried, fresh = state.world.quarried.lastOrNull() == s.today && s.today.isNotEmpty())
                hits.add(VillageHit.OnSpot("rocks") to intArrayOf(ax - 12 * s.k, ay - 14 * s.k, ax + 12 * s.k, ay + 2 * s.k))
            })
        }
        if (woodpile) TownAnchors.spotAnchor(state, w, h, "woodpile")?.let { p ->
            val wx = s.iso.worldX(s.cx(p[0] - 3f), s.cy(p[1] - 8f)); val wy = s.iso.worldY(s.cx(p[0] - 3f), s.cy(p[1] - 8f))
            objs.add(Obj((p[1] - 3f - fireY) / 2f) {
                owner[s.newObject(Pal.OUTLINE)] = hits.size + 1
                s.woods.woodStack(wx, wy, felled)
                val ax = s.cx(p[0].toFloat()).roundToInt(); val ay = s.cy(p[1].toFloat()).roundToInt()
                hits.add(VillageHit.OnSpot("woodpile") to intArrayOf(ax - 12 * s.k, ay - 16 * s.k, ax + 8 * s.k, ay))
            })
        }
    }

    /**
     * The things in the woods below the village (see [Foreground]), in painter's order with everything else: the
     * woodcutter's stack where the path enters the woods (bigger with recent fellings), the pond's reeds and ducks,
     * the meadow's log, berry bushes (picked bare today after gathering food), flowers, mushrooms and deer (gone
     * while wolves or a bear are about, and at night), the high seat with its owl at night, the charcoal pile with its smoke, and
     * fireflies on summer nights. The stack, the pond, the meadow's bushes, log, flowers and mushrooms, the high seat and the
     * charcoal pile are tappable places (their spots); the meadow's deer are their word, as the wild animals are.
     */
    private fun addWoods(s: SceneCtx, state: GameState, fg: Foreground, objs: ArrayList<Obj>, event: EventKind?, felled: Int, landmarks: List<LandmarkSpot>) {
        val env = s.env; val iso = s.iso; val woods = s.woods; val k = s.k
        val fireY = fg.comp.fireY.toFloat()
        /**
         * Whether a small thing standing at a nominal canvas point ([x], [y] its feet, [rx] px to each side, [up] px tall)
         * keeps clear of the landmarks: the bee meadow may take the meadow's flowers, mushrooms and grazing deer.
         */
        fun free(x: Float, y: Float, rx: Float = 3f, up: Float = 4f): Boolean {
            if (landmarks.isEmpty()) return true
            val dx = x - fg.comp.fireX; val dy = y - fireY
            return landmarks.none { val r = it.screenRect(); dx + rx > r[0] - 1f && dx - rx < r[2] + 1f && dy > r[1] - 1f && dy - up < r[3] + 1f }
        }
        fun depth(y: Float) = (y - fireY) / 2f
        fun at(x: Float, y: Float, draw: (Int, Int) -> Unit) {
            objs.add(Obj(depth(y)) {
                s.newObject(Pal.OUTLINE)
                val ax = s.cx(x).roundToInt(); val ay = s.cy(y).roundToInt()
                s.sprite(ax, ay) { draw(ax, ay) }
            })
        }
        fun world(x: Float, y: Float, depthAt: Float, draw: (Float, Float) -> Unit) {
            val wx = iso.worldX(s.cx(x), s.cy(y)); val wy = iso.worldY(s.cx(x), s.cy(y))
            objs.add(Obj(depth(depthAt)) { s.newObject(Pal.OUTLINE); draw(wx, wy) })
        }
        /** [at], its pixels a tap on [hit] (a box of [rx] px each side and [up] px above its feet, nominal). */
        fun at(x: Float, y: Float, hit: VillageHit, rx: Int, up: Int, draw: (Int, Int) -> Unit) {
            objs.add(Obj(depth(y)) {
                owner[s.newObject(Pal.OUTLINE)] = hits.size + 1
                val ax = s.cx(x).roundToInt(); val ay = s.cy(y).roundToInt()
                s.sprite(ax, ay) { draw(ax, ay) }
                hits.add(hit to intArrayOf(ax - rx * k, ay - up * k, ax + rx * k, ay + k))
            })
        }
        /** [world], its pixels a tap on [hit]. */
        fun world(x: Float, y: Float, depthAt: Float, hit: VillageHit, draw: (Float, Float) -> Unit) {
            val wx = iso.worldX(s.cx(x), s.cy(y)); val wy = iso.worldY(s.cx(x), s.cy(y))
            objs.add(Obj(depth(depthAt)) {
                owner[s.newObject(Pal.OUTLINE)] = hits.size + 1
                draw(wx, wy)
                val ax = s.cx(x).roundToInt(); val ay = s.cy(y).roundToInt()
                hits.add(hit to intArrayOf(ax - 6 * k, ay - 14 * k, ax + 6 * k, ay + 4 * k))
            })
        }
        /** The meadow's deer: their word, as a wild animal's (a tap right on one). */
        fun deer(x: Float, y: Float, draw: (Int, Int) -> Unit) {
            objs.add(Obj(depth(y)) {
                val slot = hits.size + 1
                owner[s.newObject(Pal.OUTLINE)] = slot
                val ax = s.cx(x).roundToInt(); val ay = s.cy(y).roundToInt()
                s.sprite(ax, ay) { draw(ax, ay) }
                hits.add(VillageHit.OnAnimal(Wild.DEER.word) to intArrayOf(ax - 5 * k, ay - 7 * k, ax + 5 * k, ay + k))
                if (slot < wild.size) wild[slot] = true
            })
        }
        val meadow = VillageHit.OnSpot("meadow")

        // the woodcutter's stack: a place
        run {
            val wx = iso.worldX(s.cx(fg.stackX - 5f), s.cy(fg.stackY - 6f)); val wy = iso.worldY(s.cx(fg.stackX - 5f), s.cy(fg.stackY - 6f))
            objs.add(Obj(depth(fg.stackY)) {
                owner[s.newObject(Pal.OUTLINE)] = hits.size + 1
                woods.woodStack(wx, wy, felled)
                val ax = s.cx(fg.stackX).roundToInt(); val ay = s.cy(fg.stackY).roundToInt()
                hits.add(VillageHit.OnSpot("woodpile") to intArrayOf(ax - 14 * k, ay - 14 * k, ax + 10 * k, ay + 4 * k))
            })
        }

        // the pond: reeds on the near bank, lily pads in summer, ducks by day from spring to autumn; the water is a place
        areas += VillageHit.OnSpot("pond") to floatArrayOf(s.cx(fg.pondX), s.cy(fg.pondY), fg.pondRx * k, fg.pondRy * k)
        for ((j, p) in arrayOf(floatArrayOf(-12f, 7f), floatArrayOf(9f, 8f), floatArrayOf(17f, 3f), floatArrayOf(-19f, 1f)).withIndex()) {
            if (j == 2 && TentMove.moved(state)) continue // where the campsite's jetty goes into the water (see Campsite)
            at(fg.pondX + p[0], fg.pondY + p[1]) { x, y -> woods.reeds(x, y, 61 + j) }
        }
        if (!env.snow && env.month in 6..9) for (j in 0..1) at(fg.pondX - 6f + j * 9f, fg.pondY + 1f + j * 2f) { x, y -> woods.lilyPad(x, y, bloom = j == 0 && env.month in 6..8) }
        if (!env.snow && env.month in 4..10 && env.dark < 0.5f) for (j in 0..1) {
            val u = sin(s.time * 0.25 + j * 2.4).toFloat()
            val x = fg.pondX + u * fg.pondRx * 0.45f + j * 3f; val y = fg.pondY - 2f + j * 3f + cos(s.time * 0.2 + j).toFloat() * 1.5f
            at(x, y) { bx, by -> woods.duck(bx, by, flip = cos(s.time * 0.25 + j * 2.4) < 0) }
        }

        // the meadow: a fallen log, berry bushes at its upper rim, flowers from spring to summer, mushrooms in late summer and
        // autumn (all of them the meadow's, to tap), deer
        world(fg.meadowX - 18f, fg.meadowY + 5f, fg.meadowY + 6f, meadow) { x, y -> woods.log(x, y) }
        for ((j, p) in arrayOf(floatArrayOf(-25f, -8f), floatArrayOf(-16f, -13f)).withIndex()) {
            val x = fg.meadowX + p[0]; val y = fg.meadowY + p[1]
            objs.add(Obj(depth(y)) {
                owner[s.newObject(Pal.OUTLINE_TREE)] = hits.size + 1
                val ax = s.cx(x).roundToInt(); val ay = s.cy(y).roundToInt()
                woods.berryBush(ax, ay, 83 + j, berries = !s.harvested && !env.snow && env.month in 6..10)
                hits.add(VillageHit.OnSpot("meadow") to intArrayOf(ax - 5 * k, ay - 6 * k, ax + 5 * k, ay + k))
            })
        }
        if (!env.snow && env.month in 4..8) for (j in 0..2) {
            val a = 1.1f + j * 2.2f
            val fx = fg.meadowX + cos(a) * fg.meadowRx * 0.55f; val fy = fg.meadowY + sin(a) * fg.meadowRy * 0.6f + 2f
            if (free(fx, fy)) at(fx, fy, meadow, 3, 4) { x, y -> woods.flowers(x, y, j) }
        }
        if (!env.snow && env.month in 8..10) for (j in 0..3) {
            val a = 0.5f + j * 1.7f
            val mx = fg.meadowX + cos(a) * fg.meadowRx * 0.92f; val my = fg.meadowY + sin(a) * fg.meadowRy * 0.95f + 2f
            if (free(mx, my)) at(mx, my, meadow, 2, 4) { x, y -> woods.mushroom(x, y, j * 7 + 1) }
        }
        val calm = event != EventKind.WOLVES && event != EventKind.BEAR && event != EventKind.STORM
        // at night the deer are off in the woods: the meadow is the wild boars' (see Wildlife)
        if (calm && Wildlife.time(env, s.hour) != WildTime.NIGHT) {
            // right of the path, below where it enters the meadow (someone may wait there), short of the high seat
            val graze = sin(s.time * 0.45) > -0.2
            if (free(fg.meadowX + 13f, fg.meadowY + 3f, rx = 5f, up = 8f)) deer(fg.meadowX + 13f, fg.meadowY + 3f) { x, y -> woods.deer(x, y, flip = false, graze = graze) }
            if (free(fg.meadowX + 22f, fg.meadowY + 8f, rx = 3f, up = 5f)) deer(fg.meadowX + 22f, fg.meadowY + 8f) { x, y -> woods.fawn(x, y, flip = sin(s.time * 0.3 + 2) > 0.6) }
        }

        // the high seat, an owl on its roof at night: the hunter's place
        world(fg.seatX - 5f, fg.seatY - 5f, fg.seatY, VillageHit.OnSpot("highseat")) { x, y ->
            woods.highSeat(x, y)
            if (env.dark > 0.35f) { val top = woods.highSeatTop(x, y); s.sprite(top[0], top[1]) { woods.owl(top[0], top[1]) } }
        }

        // the charcoal pile, smouldering: a place (its story, and the burner who sits by it some evenings)
        if (fg.kopa) {
            at(fg.kopaX, fg.kopaY, VillageHit.OnSpot(TownSpots.KOPA), 12, 8) { x, y -> woods.charcoalPile(x, y) }
            s.smoke(s.cx(fg.kopaX + 0.5f), s.cy(fg.kopaY - 8f), 0.45f, 5)
        }

        // fireflies over the meadow and the pond on summer nights
        if (env.month in 6..8) s.fx {
            woods.fireflies(s.cx(fg.meadowX), s.cy(fg.meadowY), fg.meadowRx * k, fg.meadowRy * k, 12, 91)
            woods.fireflies(s.cx(fg.pondX), s.cy(fg.pondY), (fg.pondRx + 4f) * k, (fg.pondRy + 3f) * k, 6, 92)
        }
    }

    /**
     * The campsite by the pond where the tent stands once it moved out of the village (see [Campsite]): each part in
     * painter's order with the woods, and all of it the tent's to tap (its place card leads into the tent's scene).
     */
    private fun addCamp(s: SceneCtx, state: GameState, camp: Campsite, painter: BuildingPainter, objs: ArrayList<Obj>) {
        val tent = TentMove.atPond(state) ?: return
        val slot = hits.size + 1
        val iso = s.iso
        val top = iso.sy(camp.wx, camp.wy, Campsite.TENT_HEIGHT + 2f).toInt()
        val bottom = iso.sy(camp.fireX, camp.fireY, 0f).toInt() + s.k
        hits.add(VillageHit.OnBuilding(tent) to intArrayOf(iso.sx(camp.fireX - 1f, camp.fireY + 1f).toInt(), top, iso.sx(camp.wx + 1.2f, camp.wy - 1.2f).toInt(), bottom))
        CampsitePainter(s, painter).gather(camp, tent, add = { depth, draw -> objs.add(Obj(depth, draw)) }, tag = { owner[it] = slot })
    }

    /**
     * The wild animals about now (see [Wildlife]), each at a place it likes ([WildPlaces]): on the ground and in the trees in
     * painter's order with the trees and the palisade; the birds, the bats and a bear's eyes on top (in the air, in the
     * dark); tracks in the snow on the ground. A tap right on one looks up its word (see [hitTest]). Birds and bats stay in
     * out of the rain.
     */
    private fun addWildlife(
        s: SceneCtx, state: GameState, frame: Frame, comp: Composition, fg: Foreground?, trees: List<FloatArray>,
        landmarks: List<LandmarkSpot>, camp: Campsite?, palisade: Building?, buildings: List<Building>, objs: ArrayList<Obj>, rain: Float,
    ) {
        val window = Wildlife.time(s.env, frame.hour)
        val day = Wildlife.day(frame.today, frame.hour, window)
        val pk = "${state.seed}/$day/$window/${frame.month}"
        if (pk != planKey) { planKey = pk; plan = Wildlife.plan(state.seed, day, window, frame.month) }
        if (plan.isEmpty()) return
        val bw = s.lens.baseW; val bh = s.lens.baseH
        val key = "$bw/$bh/${frame.today}"
        if (state !== wildState || key != wildKey || wildPlaces == null) {
            wildState = state; wildKey = key
            val ring = if (palisade != null) Palisade.ring(s.land, state.age, fg) else null
            wildPlaces = Wildlife.places(state, bw, bh, fg, trees, landmarks, camp, ring) { stages.containsKey(it) }
        }
        val places = wildPlaces ?: return
        val k = s.k; val fireY = comp.fireY.toFloat(); val paint = s.wildlife; val t = s.time
        val cw = s.canvas.width; val ch = s.canvas.height
        fun depth(y: Float) = (y - fireY) / 2f
        fun shown(x: Float, y: Float): Boolean {
            if (k == 1) return true
            val ax = s.cx(x); val ay = s.cy(y)
            return ax > -24f * k && ax < cw + 24f * k && ay > -8f * k && ay < ch + 32f * k
        }
        fun tag(slot: Int) { if (slot < wild.size) wild[slot] = true }
        /** An animal (or a family) with its feet at nominal ([x], [y]), in painter's order at [d]: one tappable object. */
        fun ground(kind: Wild, x: Float, y: Float, d: Float = depth(y), draw: (Int, Int) -> Unit) {
            seen += WildSeen(kind, CanvasPoint(x, y))
            if (!shown(x, y)) return
            objs.add(Obj(d) {
                val slot = hits.size + 1
                owner[s.newObject(Pal.OUTLINE)] = slot
                val ax = s.cx(x).roundToInt(); val ay = s.cy(y).roundToInt()
                s.sprite(ax, ay) { draw(ax, ay) }
                hits.add(VillageHit.OnAnimal(kind.word) to intArrayOf(ax - 6 * k, ay - 8 * k, ax + 6 * k, ay + k))
                tag(slot)
            })
        }
        /** Something in the air or the dark over everything, at nominal ([x], [y]): drawn with the effects, no outline, tappable. */
        fun above(kind: Wild, x: Float, y: Float, draw: () -> Unit) {
            seen += WildSeen(kind, CanvasPoint(x, y))
            if (!shown(x, y)) return
            s.fx {
                val slot = hits.size + 1
                owner[s.newObject(0)] = slot
                draw()
                s.canvas.penId = 0
                val ax = s.cx(x).roundToInt(); val ay = s.cy(y).roundToInt()
                hits.add(VillageHit.OnAnimal(kind.word) to intArrayOf(ax - 8 * k, ay - 6 * k, ax + 8 * k, ay + 2 * k))
                tag(slot)
            }
        }
        val taken = ArrayList<FloatArray>()
        /** A place from [list] from [pick] on, [apart] px from the others' (it's theirs then). */
        fun spot(list: List<FloatArray>, pick: Float, apart: Float = 24f): FloatArray? {
            if (list.isEmpty()) return null
            val start = (pick * list.size).toInt().coerceIn(0, list.size - 1)
            for (j in list.indices) {
                val p = list[(start + j) % list.size]
                if (taken.none { hypot(it[0] - p[0], it[1] - p[1]) < apart }) { taken += p; return p }
            }
            return null
        }
        for (sg in plan) {
            val seed = sg.seed
            // its own phase and side
            val ph = ((seed ushr 4) and 1023) / 163.0
            val left = (seed ushr 14) and 1 == 1
            when (sg.kind) {
                Wild.DEER -> {
                    val p = spot(places.edge, sg.pick) ?: continue
                    // a second one at the next place along the edge, a few steps off
                    val pair = if (sg.n > 1) places.edge.firstOrNull { q -> hypot(q[0] - p[0], q[1] - p[1]).let { it in 6f..13f } } else null
                    for ((i, q) in listOfNotNull(p, pair).withIndex()) {
                        val graze = sin(t * 0.45 + ph + i * 1.7) > -0.2
                        ground(Wild.DEER, q[0], q[1]) { ax, ay -> s.woods.deer(ax, ay, flip = left != (i == 1), graze = graze) }
                    }
                }
                Wild.FOX -> {
                    val p = spot(places.edge, sg.pick) ?: continue
                    // slinking along outside the palisade, and back
                    val a = t * 0.22 + ph
                    val u = sin(a).toFloat() * 6f; val v = cos(a).toFloat() * p[2]
                    val step = if (abs(cos(a)) > 0.15) ((t * 5).toInt() and 1) else 0
                    ground(Wild.FOX, p[0] + p[2] * u, p[1] + p[3] * u) { ax, ay -> paint.fox(ax, ay, flip = v > 0f, step = step) }
                }
                Wild.HEDGEHOG -> {
                    val p = spot(places.bushes, sg.pick) ?: spot(places.edge, sg.pick) ?: continue
                    val a = t * 0.3 + ph
                    ground(Wild.HEDGEHOG, p[0] + sin(a).toFloat() * 1.5f, p[1]) { ax, ay -> paint.hedgehog(ax, ay, flip = cos(a) > 0) }
                }
                Wild.HARE -> {
                    val p = spot(places.meadow, sg.pick) ?: spot(places.edge, sg.pick) ?: continue
                    // sitting a while, then a hop over and back
                    val q = (t + ph * 1.2) / 6.5
                    val hop = q - floor(q) < 0.08
                    val there = floor(q).toInt() and 1 == 1
                    val f = if (hop) ((q - floor(q)) / 0.08).toFloat() else 1f
                    val off = if (there) 5f * f else 5f * (1f - f)
                    val dx = if (left) -off else off
                    ground(Wild.HARE, p[0] + dx, p[1]) { ax, ay -> paint.hare(ax, ay, flip = (there == left), hop = hop) }
                }
                Wild.BOARS -> {
                    val meadow = if (window == WildTime.NIGHT) places.meadowNight else places.meadow
                    val p = spot(meadow, sg.pick, apart = 30f) ?: spot(places.edge, sg.pick, apart = 30f) ?: continue
                    val stripes = frame.month in 4..8
                    val root = sin(t * 1.3 + ph) > -0.1
                    ground(Wild.BOARS, p[0], p[1]) { ax, ay ->
                        paint.boar(ax, ay, flip = left, root = root)
                        // the young about her, rooting and trotting a little
                        for (j in 0 until sg.n - 1) {
                            val side = if (j % 2 == 0) -1 else 1
                            val px = ax + side * (7 + j * 2) + (sin(t * 0.5 + j * 2.1 + ph) * 1.6).roundToInt()
                            val py = ay + 1 + (j % 3) - 1
                            paint.piglet(px, py, flip = (side > 0) == left, stripes = stripes, step = ((t * 4).toInt() + j) and 1)
                        }
                    }
                }
                Wild.BADGER -> {
                    val p = spot(places.edge, sg.pick) ?: continue
                    val a = t * 0.18 + ph
                    val u = sin(a).toFloat() * 4f
                    val step = if (abs(cos(a)) > 0.2) ((t * 4).toInt() and 1) else 0
                    ground(Wild.BADGER, p[0] + p[2] * u, p[1] + p[3] * u) { ax, ay -> paint.badger(ax, ay, flip = cos(a) * p[2] > 0, step = step) }
                }
                Wild.OWL -> {
                    val p = spot(places.crowns, sg.pick, apart = 16f) ?: continue
                    // on top of a tree at the forest's edge, just over the tree it sits in
                    val x = p[0]; val y = p[1]
                    seen += WildSeen(Wild.OWL, CanvasPoint(x, y))
                    if (!shown(x, y)) continue
                    objs.add(Obj(depth(p[5]) + 0.01f) {
                        val slot = hits.size + 1
                        owner[s.newObject(Pal.OUTLINE)] = slot
                        val ax = s.cx(x).roundToInt(); val ay = s.cy(y).roundToInt()
                        s.sprite(ax, ay) { s.woods.owl(ax, ay) }
                        hits.add(VillageHit.OnAnimal(Wild.OWL.word) to intArrayOf(ax - 3 * k, ay - 5 * k, ax + 3 * k, ay + k))
                        tag(slot)
                    })
                }
                Wild.SQUIRREL -> {
                    val p = spot(places.perch, sg.pick, apart = 16f)
                    if (p == null) {
                        // no trunk in view: on top of a crown, looking about
                        val c = spot(places.crowns, sg.pick, apart = 16f) ?: continue
                        ground(Wild.SQUIRREL, c[0], c[1], d = depth(c[5]) + 0.01f) { ax, ay -> paint.squirrel(ax, ay, flip = sin(t * 0.7 + ph) > 0) }
                        continue
                    }
                    // up the trunk, a rest under the crown, down again, a rest at its foot
                    val q = ((t + ph * 2) % 10.0).toFloat()
                    val up = when {
                        q < 3f -> 0f
                        q < 4.5f -> (q - 3f) / 1.5f
                        q < 7.5f -> 1f
                        q < 9f -> 1f - (q - 7.5f) / 1.5f
                        else -> 0f
                    }
                    val x = p[0] + 2f; val y = p[1] - ((p[2] - 1f) * up).roundToInt()
                    ground(Wild.SQUIRREL, x, y, d = depth(p[1]) + 0.01f) { ax, ay -> paint.squirrel(ax, ay, flip = left) }
                }
                Wild.BIRDS -> {
                    if (rain > 0.3f) continue
                    val p = spot(places.sky, sg.pick) ?: continue
                    val cx = p[0]; val cy = p[1] - 22f - (seed and 7)
                    val a = t * 0.3 + ph
                    val xs = FloatArray(sg.n) { j -> cx + cos(a + j * 0.4).toFloat() * 18f + (j - sg.n / 2f) * 3f }
                    val ys = FloatArray(sg.n) { j -> cy + sin(a + j * 0.4).toFloat() * 5f + (j % 2) * 2f }
                    above(Wild.BIRDS, xs[0], ys[0]) {
                        for (j in 0 until sg.n) paint.bird(s.cx(xs[j]).roundToInt(), s.cy(ys[j]).roundToInt(), ((t * 5).toInt() + j) and 1)
                    }
                }
                Wild.BATS -> {
                    if (rain > 0.3f) continue
                    // over the roofs well away from the fire (the grass by it is for tapping), else over the far treeline
                    val roofs = buildings.filter { it.type != BuildingType.FIELD && it.type != BuildingType.PALISADE }
                        .map { b -> TownAnchors.top(b, state, bw, bh).let { floatArrayOf(it.x, it.y + 2f) } }
                        .filter { VillageLayout.screenMetric(it[0] - comp.fireX, it[1] - 8f - comp.fireY) > BAT_ROOM }
                    val p = (if (roofs.isNotEmpty()) spot(roofs, sg.pick, apart = 0f) else null)
                        ?: spot(places.sky, sg.pick)?.let { floatArrayOf(it[0], it[1] - 14f) } ?: continue
                    val xs = FloatArray(sg.n); val ys = FloatArray(sg.n)
                    for (j in 0 until sg.n) {
                        val a = t * 1.7 + ph + j * 2.3
                        xs[j] = p[0] + sin(a).toFloat() * 10f + (j - 1) * 4f
                        ys[j] = p[1] - 8f + sin(2 * a).toFloat() * 3f - j * 2f
                    }
                    above(Wild.BATS, xs[0], ys[0]) {
                        for (j in 0 until sg.n) {
                            val ax = s.cx(xs[j]).roundToInt(); val ay = s.cy(ys[j]).roundToInt()
                            s.sprite(ax, ay) { paint.bat(ax, ay, ((t * 9).toInt() + j) and 1) }
                        }
                    }
                }
                Wild.BEAR -> {
                    val p = spot(places.edge, sg.pick) ?: continue
                    // a little way in among the trees
                    val x = p[0] + cos(p[4]) * 9f; val y = p[1] + sin(p[4]) * 9f / 2.4f
                    val open = (t + ph) % 6.0 > 0.3
                    above(Wild.BEAR, x, y) {
                        val ax = s.cx(x).roundToInt(); val ay = s.cy(y).roundToInt()
                        paint.bearEyes(ax, ay, open)
                    }
                }
                Wild.TRACKS -> {
                    if (!s.env.snow) continue
                    val p = spot(places.edge, sg.pick) ?: continue
                    seen += WildSeen(Wild.TRACKS, CanvasPoint(p[0], p[1]))
                    if (!shown(p[0], p[1])) continue
                    // on the ground, under everything: drawn now
                    val slot = hits.size + 1
                    owner[s.newObject(0)] = slot
                    val dir = if (left) -1f else 1f
                    paint.tracks(s.cx(p[0]), s.cy(p[1]), p[2] * dir, p[3] * dir, 5)
                    s.canvas.penId = 0
                    val ax = s.cx(p[0]).roundToInt(); val ay = s.cy(p[1]).roundToInt()
                    hits.add(VillageHit.OnAnimal(Wild.TRACKS.word) to intArrayOf(ax - 12 * k, ay - 6 * k, ax + 12 * k, ay + 6 * k))
                    tag(slot)
                }
            }
        }
    }

    /**
     * The village projects' landmarks (see [LandmarkLayout], [LandmarkPainter]), each one object in painter's order with
     * the buildings and a tappable place: a tap opens its project.
     */
    private fun addLandmarks(s: SceneCtx, landmarks: List<LandmarkSpot>, buildings: BuildingPainter, objs: ArrayList<Obj>) {
        if (landmarks.isEmpty()) return
        val painter = LandmarkPainter(s, buildings)
        for (sp in landmarks) {
            // the footbridge lies low over the stream: whoever stands on the banks by it is in front of it
            val depth = sp.cx + sp.cy - if (sp.landmark.id == "most") 1f else 0f
            objs.add(Obj(depth) {
                owner[s.newObject(Pal.OUTLINE)] = hits.size + 1
                painter.draw(sp)
                val iso = s.iso
                hits.add(
                    VillageHit.OnLandmark(sp.landmark.project) to intArrayOf(
                        iso.sx(sp.x, sp.y + sp.d).toInt(), iso.sy(sp.x, sp.y, sp.height).toInt(),
                        iso.sx(sp.x + sp.w, sp.y).toInt(), iso.sy(sp.x + sp.w, sp.y + sp.d, 0f).toInt(),
                    ),
                )
            })
        }
    }

    /**
     * The palisade: a ring of stakes round the clearing (see [Palisade], [PalisadePainter]), each stake in painter's order
     * with the trees, the buildings and the people, leaving room for the rocks and the small woodpile at the clearing's
     * edge where they're drawn (see [addEdgeSpots]). A tap on any stretch of it, or its gate, opens the palisade.
     */
    private fun addPalisade(
        s: SceneCtx, state: GameState, b: Building, fg: Foreground?, landmarks: List<LandmarkSpot>, buildings: BuildingPainter,
        objs: ArrayList<Obj>, horizon: Int, frame: Frame,
    ) {
        val ring = Palisade.ring(s.land, state.age, fg)
        val painter = PalisadePainter(s, buildings)
        val keep = ArrayList<FloatArray>()
        if (!frame.compact) {
            // the rocks, and the quarry face behind them that widens as stone is taken (see WoodsPainter.rocks)
            val quarried = min(state.world.quarried.count { Regrowth.days(it, frame.today) < Regrowth.GROWN_DAYS }, 8)
            TownAnchors.spotWorld(state, "rocks")?.let { keep += floatArrayOf(it[0] - 0.4f, it[1] - 0.4f, max(1.6f, (7f + quarried) / 5.5f)) }
            if (fg == null) TownAnchors.spotWorld(state, "woodpile")?.let { keep += floatArrayOf(it[0] - 1f, it[1] - 0.7f, if (s.land.classic) 1.8f else 2.4f) }
        }
        val slot = hits.size + 1
        ringSlot = slot
        hits.add(VillageHit.OnBuilding(b) to painter.gateRect(ring))
        val grow = if (frame.justBuilt == b.id) frame.justBuiltProgress.coerceIn(0f, 1f) else 1f
        painter.gather(b, ring, landmarks, keep, grow, horizon, add = { depth, draw -> objs.add(Obj(depth, draw)) }, tag = { owner[it] = slot })
    }

    /** World point [p] (cells), moved in off the water of [land] (its lake or sea) when it is in it. */
    private fun dry(land: Terrain, p: FloatArray): FloatArray {
        if (!land.water) return p
        val sx = (p[0] - p[1]) * 4f; val sy = (p[0] + p[1]) * 2f
        if (!land.wetScreen(sx, sy, 4f)) return p
        val q = land.inland(sx, sy, 4f)
        return floatArrayOf((q[0] / 4f + q[1] / 2f) / 2f, (q[1] / 2f - q[0] / 4f) / 2f)
    }

    /** A world point [r] (screen metric) from the fire at angle [a], kept on the nominal canvas. */
    private fun edgePoint(comp: Composition, r: Float, a: Double, w: Int, h: Int): FloatArray {
        var sx = (r * cos(a)).toFloat(); var sy = (r * sin(a)).toFloat() / 2.4f
        sx = sx.coerceIn(14f - comp.fireX, w - 14f - comp.fireX)
        sy = sy.coerceIn(comp.horizon + 10f - comp.fireY, h - 8f - comp.fireY)
        return floatArrayOf((sx / 4f + sy / 2f) / 2f, (sy / 2f - sx / 4f) / 2f)
    }

    // ------------------------------------------------------------------ people

    /**
     * The people who live here, each where [TownPeople] put them, at the lens's detail: a 7 px figure, a 14 px one
     * with a hat and a prop, or the scene's people with faces at k = 3. Each is tappable and keeps a bubble anchor
     * above their head.
     */
    private fun addPeople(s: SceneCtx, placed: List<Placed>, comp: Composition, objs: ArrayList<Obj>) {
        val k = s.k
        for (p in placed) {
            val look = Effects.lookOf(p.villager.art, Noise.hash(p.id.hashCode(), 13))
            val height = when (k) { 1 -> 7f; 2 -> 7f; else -> s.people.height(p.villager.art) / 3f }
            anchors[p.id] = TownPeople.headAnchor(p, comp, if (p.seated) height * 0.7f else height)
            objs.add(Obj(p.x + p.y + (if (p.z > 0f) 0.05f else 0f)) {
                val bx = s.iso.ix(p.x, p.y); val by = s.iso.iy(p.x, p.y, p.z)
                owner[s.newObject(Pal.OUTLINE)] = hits.size + 1
                val seed = TownPeople.drawSeed(p.id)
                val hh = s.effects.person(bx, by, p.villager.art, look, seed, p.activity, p.flip, lantern = p.lantern, load = p.load)
                hits.add(VillageHit.OnVillager(p.id) to intArrayOf(bx - 5 * k, by - hh, bx + 5 * k, by + k))
            })
        }
    }

    /**
     * The population beyond the named villagers, unnamed: at a festival dancing round the fire; in the dark evening a few
     * round it; by day going between the doors and the plaza on errands of their own, a while at each (a word at a
     * neighbour's door), along the paths and the road, never through the fire or a building ([TownPeople.route]).
     */
    private fun addVillagers(s: SceneCtx, state: GameState, total0: Int, buildings: List<Building>, objs: ArrayList<Obj>, event: EventKind?, atFire: Int = 0, cw: Int = 0, ch: Int = 0) {
        val total = total0.coerceIn(0, 20)
        if (total == 0) return
        val env = s.env
        val seed0 = (state.seed xor (state.seed ushr 32)).toInt()
        val night = env.dark > 0.55f
        val storm = event == EventKind.STORM
        if (event == EventKind.FESTIVAL) {
            val n = min(total, 14)
            for (k in 0 until n) {
                val a = s.time * 0.7 + k * 2 * PI / n
                val r = 2.9f + (k % 2) * 0.4f
                val x = cos(a).toFloat() * r; val y = sin(a).toFloat() * r
                objs.add(Obj(x + y) {
                    s.newObject(Pal.OUTLINE)
                    s.effects.villager(s.iso.ix(x, y), s.iso.iy(x, y, 0f), Noise.hash(seed0, k), true, sin(a) > 0, bob = ((s.time * 6).toInt() + k) and 1)
                })
            }
            return
        }
        if (night || storm) {
            // the dark evening: a few by the fire, fewer as it gets late; from ten, and in the dark morning, they're home
            val n = when {
                storm -> min(total, 1)
                s.hour >= 22f || s.hour < 12f -> 0
                s.hour >= 21f -> min(total, 2)
                else -> min(total, 4)
            }
            // round the fire beside it, after whoever sits there already ([atFire]), never in its flames
            val scale = TownAnchors.FIRE_SCALE[state.age.ordinal]
            for (k in 0 until n) {
                val p = TownPeople.aroundFire(atFire + k, scale)
                val x = p[0]; val y = p[1]
                objs.add(Obj(x + y) {
                    s.newObject(Pal.OUTLINE)
                    s.effects.seated(s.iso.ix(x, y), s.iso.iy(x, y, 0f), Noise.hash(seed0, k), flip = x - y > 0)
                })
            }
            return
        }
        val frac = 0.45f + 0.55f * state.morale.coerceIn(0, 100) / 100f
        val n = max(1, (total * frac).roundToInt())
        val scale = TownAnchors.FIRE_SCALE[state.age.ordinal]
        val anchors = buildings.filter { it.type != BuildingType.PALISADE }.map {
            val p = s.land.centers[it.plot]
            // at its door, on the road's side (beside the fire's flames, never behind them)
            TownPeople.offFire(p[0], p[1] - 1.9f * sign(p[1] - s.land.roadY(p[0])), scale)
        }
        val ways = Walkways.of(state, cw, ch)
        // a spot on the plaza round the fire (beside it, never in its flames)
        fun plaza(k: Int): FloatArray { val ang = k * 1.3; return TownPeople.offFire(cos(ang).toFloat() * 3.6f, sin(ang).toFloat() * 3.6f, scale) }
        for (k in 0 until n) {
            val hh = Noise.hash(seed0, k, 5)
            val a: FloatArray; val b: FloatArray
            if (anchors.isEmpty()) {
                a = plaza(k); b = plaza(k + 3)
            } else {
                a = anchors[k % anchors.size]
                b = if (k % 3 == 0 || anchors.size < 2) plaza(k)
                else anchors[(k * 7 + 3) % anchors.size].let { if (it === a) anchors[(k + 1) % anchors.size] else it }
            }
            // the way: along the paths and the road, round the fire and the buildings
            val way = TownPeople.route(a, b, aWoods = false, bWoods = false, gate = null, land = s.land, seed = hh, ways = ways)
            val len = Walkways.length(way)
            val speed = 1.1f
            val walk = max(len / speed, 0.1f)
            // a while at each end: a word at the door, a look round the plaza
            val pause = 12f + Noise.rnd(hh, 1) * 18f
            val cycle = 2 * (walk + pause)
            val ph = ((s.time + Noise.rnd(hh, 2) * cycle) % cycle).toFloat()
            val d: Float; val dir: Int
            when {
                ph < pause -> { d = 0f; dir = 0 }
                ph < pause + walk -> { d = (ph - pause) * speed; dir = 1 }
                ph < 2 * pause + walk -> { d = len; dir = 0 }
                else -> { d = len - (ph - 2 * pause - walk) * speed; dir = -1 }
            }
            val (p, heading) = if (dir >= 0) TownPeople.along(way, d.coerceIn(0f, len)) else TownPeople.along(way.reversed(), len - d.coerceIn(0f, len))
            val flip = if (dir == 0) p[0] > 0f else heading < 0f
            val fx = p[0]; val fy = p[1]
            objs.add(Obj(fx + fy) {
                s.newObject(Pal.OUTLINE)
                s.effects.villager(s.iso.ix(fx, fy), s.iso.iy(fx, fy, 0f), hh, dir != 0, flip)
            })
        }
    }

    /**
     * Someone waiting where a happening is on: standing by the fire (in the place round it after the [atFire] there
     * already), at a building's door or at the forest edge.
     */
    private fun addVisitors(s: SceneCtx, state: GameState, places: List<TownPlace>, objs: ArrayList<Obj>, comp: Composition, w: Int, h: Int, atFire: Int = 0) {
        val seed0 = (state.seed xor (state.seed ushr 32)).toInt()
        for ((k, place) in places.distinct().withIndex()) {
            val p = (if (place == TownPlace.Fire) TownPeople.aroundFire(atFire, TownAnchors.FIRE_SCALE[state.age.ordinal]) else TownAnchors.visitorSpot(state, place, comp, w, h)) ?: continue
            val x = p[0]; val y = p[1]
            val seed = Noise.hash(seed0, k, 77)
            objs.add(Obj(x + y) {
                s.newObject(Pal.OUTLINE)
                // a little hop now and then: someone is waiting to talk
                val hop = if (((s.time * 1.3 + k * 0.7) % 3.0) < 0.3) 1 else 0
                s.effects.villager(s.iso.ix(x, y), s.iso.iy(x, y, 0f), seed, false, flip = if (place == TownPlace.Fire) x - y > 0f else x > 0f, bob = hop)
            })
        }
    }

    // ------------------------------------------------------------------ passes

    /** 1-px dark outline drawn just outside every object, onto whatever lies behind it. */
    private fun outlinePass(s: SceneCtx) {
        val c = s.canvas; val w = c.width; val h = c.height
        val ids = c.ids; val px = c.pixels
        val tmp = IntArray(w * h)
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

    private fun lightingPass(s: SceneCtx, desat: Float) {
        val c = s.canvas; val env = s.env
        val w = c.width; val h = c.height
        val ambLum = env.ambR * 0.3f + env.ambG * 0.59f + env.ambB * 0.11f
        val ls = ((1.05f - ambLum) * 1.5f).coerceIn(0f, 1.2f)
        val plain = abs(env.ambR - 1f) < 0.01f && abs(env.ambG - 1f) < 0.01f && abs(env.ambB - 1f) < 0.01f
        if (plain && desat <= 0f) return
        val L = s.lightBuf
        L.fill(0f)
        if (ls > 0.02f) {
            for (k in 0 until s.nLights) {
                val cx = s.lx[k]; val cy = s.ly[k]; val r = s.lr[k]; val inten = s.li[k]
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
            var col = px[i]
            if (!em[i]) {
                val l = min(L[i] * ls, 1.3f)
                val fr = env.ambR + l * 1.0f; val fg = env.ambG + l * 0.72f; val fb = env.ambB + l * 0.42f
                col = Col.rgb((Col.r(col) * fr).toInt(), (Col.g(col) * fg).toInt(), (Col.b(col) * fb).toInt())
            }
            if (desat > 0f) {
                val g = (Col.r(col) * 0.3f + Col.g(col) * 0.59f + Col.b(col) * 0.11f).toInt()
                col = Col.mix(col, Col.rgb(g, g, (g * 1.04f).toInt()), desat)
            }
            px[i] = col
        }
    }

    private fun flash(c: PixelCanvas, a: Float) {
        val white = Col.hex(0xE8EEFF)
        for (i in c.pixels.indices) c.pixels[i] = Col.mix(c.pixels[i], white, a * 0.6f)
    }

    private fun smooth(t: Float) = t * t * (3 - 2 * t)

    private companion object {
        /** Slides the Alps so Triglav (u ≈ 0.71) stands at ≈ 0.84 of the width, clear of the village centre. */
        const val COMPACT_SHIFT = 0.13f

        /** Trees within this many nominal pixels of the woodpile can be felled. */
        const val CHOP_REACH = 110f

        /** The plot chooser's ★ (see [PlotMarks]), 9 px across: each row's spans (dx from, dx to), from its point down to its feet. */
        val STAR: List<List<Pair<Int, Int>>> = listOf(
            listOf(0 to 0),
            listOf(-1 to 1),
            listOf(-4 to 4),
            listOf(-3 to 3),
            listOf(-2 to 2),
            listOf(-3 to -1, 1 to 3),
            listOf(-3 to -2, 2 to 3),
        )

        /** Bats flit over the roofs at least this far from the fire (the clearing's metric, see [VillageLayout.screenMetric]). */
        const val BAT_ROOM = 90f

        /** Fallen beech leaves on the forest floor in autumn. */
        val LEAF_LITTER = intArrayOf(Col.hex(0xC9651F), Col.hex(0xA84A2A), Col.hex(0xB08A2E))

        /** The relief's cache: world cells from -N/2 to N/2 each way. */
        const val RELIEF_N = 512

        /** The sea's surf and a torrent's white water, the sea over the shallows and far out; the beach's sand. */
        val SURF = Col.hex(0xE8F2FA)
        val SHALLOWS = Col.hex(0x5FA8D8)
        val SEA_DEEP = Col.hex(0x2F6FA8)
        val SAND_L = Col.hex(0xF0E2B0)
        val SAND_M = Col.hex(0xE2CF92)
        val SAND_D = Col.hex(0xCDB77A)
        val SAND_WET = Col.hex(0xB9A36C)

        /** The vineyards' leaves by season, their earth, and the bare stocks in winter. */
        val VINE_LEAF = Col.hex(0x6E9B3C)
        val VINE_DARK = Col.hex(0x4C7A2C)
        val VINE_SPRING = Col.hex(0x9CC45A)
        val VINE_GOLD = Col.hex(0xD2A437)
        val VINE_RED = Col.hex(0xA8452A)
        val VINE_EARTH = Col.hex(0x8C6B45)
        val VINE_STOCK = Col.hex(0x5A4332)
    }
}
