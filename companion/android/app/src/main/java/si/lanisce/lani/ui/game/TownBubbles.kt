package si.lanisce.lani.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import si.lanisce.lani.game.render.Camera
import si.lanisce.lani.game.render.CanvasPoint
import si.lanisce.lani.game.scene.TownMarker
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.ui.Portrait
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.l10n.bi
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Pixel-art speech-bubble colours: they sit on the pixel scene, so they're the same by day and night. */
private val BubblePaper = Color(0xFFFFF6E0)
/** Someone is here now: a fresh green, apart from the requests' paper. */
private val BubbleMint = Color(0xFFDDF3D6)
private val BubbleInk = Color(0xFF2B2118)

/** A square with stepped (pixel) corners, like the village's sprites. */
private val PixelShape = GenericShape { size, _ ->
    val s = size.minDimension / 11f
    val w = size.width; val h = size.height
    moveTo(2 * s, 0f); lineTo(w - 2 * s, 0f); lineTo(w - 2 * s, s); lineTo(w - s, s); lineTo(w - s, 2 * s); lineTo(w, 2 * s)
    lineTo(w, h - 2 * s); lineTo(w - s, h - 2 * s); lineTo(w - s, h - s); lineTo(w - 2 * s, h - s); lineTo(w - 2 * s, h)
    lineTo(2 * s, h); lineTo(2 * s, h - s); lineTo(s, h - s); lineTo(s, h - 2 * s); lineTo(0f, h - 2 * s)
    lineTo(0f, 2 * s); lineTo(s, 2 * s); lineTo(s, s); lineTo(2 * s, s); close()
}

private val BUBBLE = 44.dp
/** The small bubble of someone far off, with their face (see [TownBubbles]'s face). */
private val FACE = 34.dp
private val TOUCH = 52.dp
/** From a place's anchor up to the bubble's centre: the pointer and half a bubble. */
private val LIFT = 40.dp
private val SPACING = 54f
/** A bubble with its air, for keeping the places' bubbles apart (dp): the bubble, its badges and a bit more. */
private const val APART = 52f
/** Half a bubble and a little air from the screen's edge (dp). */
private const val EDGE = 30f
/** How far a bubble over someone at the screen's edge shifts in at most (dp): half its width, so it shows whole. */
const val BUBBLE_NUDGE = 22f
/** How far the bubbles push each other from where they'd be at most (dp): a bubble and a half, so the pointers stay short. */
const val BUBBLE_REACH = 1.5f * APART

/**
 * A quiet marker (companion/SCENES.md "Bubbles in the village"): what's on at a place (a happening, today's visitor, the
 * burner by his pile, a festival, the day's surprise, a project's step, the treasure map) shows as a small icon, full size
 * only near the view's centre ([QUIET_NEAR]); the event, who joined the village, an age's party and the requests of the
 * map's window are bubbles always.
 */
fun TownMarker.quiet(): Boolean = when {
    kind == TownMarker.Kind.EVENT || kind == TownMarker.Kind.TIP -> false
    id.startsWith("arrival:") || id.startsWith(si.lanisce.lani.game.scene.TownMarkers.PARTY) -> false
    id.startsWith("quest:") || id.startsWith("cluster:") || id.startsWith("more:") -> false
    else -> true
}

/** A quiet marker is full size within this share of the screen's shorter side from its centre, an icon from [QUIET_FAR]. */
const val QUIET_NEAR = 0.2f
const val QUIET_FAR = 0.35f

/** How small a quiet marker's bubble is, and how faint, away from the view's centre. */
private const val QUIET_SCALE = 0.6f
private const val QUIET_ALPHA = 0.8f

/** How quiet a marker at [dx], [dy] (px) from the view's centre is on a screen whose shorter side is [short] px: 0 near, 1 far. */
fun quietness(dx: Float, dy: Float, short: Float): Float {
    if (short <= 0f) return 0f
    val d = kotlin.math.hypot(dx, dy) / short
    return ((d - QUIET_NEAR) / (QUIET_FAR - QUIET_NEAR)).coerceIn(0f, 1f)
}

/** How a marker's bubble looks. */
private fun TownMarker.fill(threat: Boolean): Color = when {
    kind == TownMarker.Kind.EVENT && threat -> TriglavRed
    kind == TownMarker.Kind.EVENT -> XpGold
    kind == TownMarker.Kind.HAPPENING && !id.startsWith("more:") && !id.startsWith(si.lanisce.lani.game.scene.TownMarkers.BACK) -> BubbleMint
    else -> BubblePaper
}

/** What a group of bubbles hangs over: a place, or someone who lives here (their bubble follows them about). */
private sealed interface AnchorKey {
    val place: TownPlace

    data class At(override val place: TownPlace) : AnchorKey
    data class Person(val id: String, override val place: TownPlace) : AnchorKey
}

/**
 * The bubbles over the village: one per marker, pointing at its place (or at the villager it belongs to, wherever
 * they've wandered), gently bobbing; several at one place fan out, and the places' fans keep out of each other's
 * way (see [layoutBubbles]); one whose spot changes glides there ([BubbleGlide]), its tap target with it. Each stays
 * with its place: off the screen with it (at the edge nudged in by half its width at most, [BUBBLE_NUDGE]), fading out
 * as it goes under the HUD, pushed by the others a bubble and a half at most ([BUBBLE_REACH]: with no room that near,
 * stacked as cards, the nearer person's on top); while some are off the screen's sides a small chip there leads to them
 * ([EdgeChip]). Positions follow [camera] and the clock's [tick] in the layout and draw phases, so panning, zooming and
 * the villagers' steps never recompose them.
 *
 * @param threat the village's event is a threat (wolves, a bear, a storm): its bubble is red and pulses
 * @param badge a small second emoji for a marker (what a happening is about, e.g. 🍲), or null
 * @param topInset screen pixels the bubbles stay below: the HUD's, and what hangs under it (the recenter button)
 * @param hudInset screen pixels the HUD itself covers: a bubble fades out as its place goes under it
 * @param followId the villager a marker belongs to, when they're drawn on the map
 * @param anchorOf where that villager is right now (nominal canvas px, above their head), or null when they aren't out
 * @param tick the renderer's clock, read in the layout and draw phases so the bubbles keep up with the people
 * @param face the villager a marker's bubble shows the face of, small, instead of its emoji: someone away from the
 *   clearing, not on the map (in the mountains at the horizon, at the vineyard out in the woods)
 * @param fireArea the fire's tap area (nominal canvas px, [si.lanisce.lani.game.render.TownAnchors.fireArea]): the
 *   bubbles keep off it and off the fire's screen reader target (see [layoutBubbles]), so a tap on the fire is the
 *   fire's, whoever stands beside it with a bubble
 * @param fireBody the middle of the fire's body, where its screen reader's target is centred
 * @param bottomInset screen pixels the bottom bar covers: the edge chips keep above it
 * @param onReveal brings a canvas point (a bubble's place, nominal px) onto the screen: with it, a small chip at the
 *   screen's side while bubbles are off it there ([EdgeChip]) leads to them
 */
@Composable
fun TownBubbles(
    markers: List<TownMarker>,
    anchors: Map<TownPlace, CanvasPoint>,
    camera: () -> Camera,
    threat: Boolean,
    badge: (TownMarker) -> String?,
    onTap: (TownMarker) -> Unit,
    modifier: Modifier = Modifier,
    topInset: Int = 0,
    hudInset: Int = topInset,
    followId: (TownMarker) -> String? = { null },
    anchorOf: (String) -> CanvasPoint? = { null },
    tick: () -> Long = { 0L },
    face: (TownMarker) -> Villager? = { null },
    fireArea: FloatArray? = null,
    fireBody: CanvasPoint? = null,
    bottomInset: Int = 0,
    onReveal: ((CanvasPoint) -> Unit)? = null,
) {
    if (markers.isEmpty()) return
    val keys = markers.map { followId(it) }
    val groups = remember(markers, keys) {
        markers.groupBy { m -> followId(m)?.let { AnchorKey.Person(it, m.place) } ?: AnchorKey.At(m.place) }.toList()
    }
    val clock = rememberInfiniteTransition(label = "bubbles").animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "bob")
    // where each bubble shows on its way to its spot, by marker (it outlives a new layout: a fan grown by one glides too),
    // and the frame's time it glides by (the bob redraws every frame anyway)
    val glide = remember { BubbleGlide() }
    val frame = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { frame.longValue = it } }
    val now = { frame.longValue }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()
        val laid = remember(groups, anchors, density, viewW, viewH, topInset, hudInset, bottomInset, fireArea, fireBody) {
            BubbleLayout(groups, anchors, anchorOf, density.density, viewW, viewH, topInset.toFloat(), hudInset.toFloat(), bottomInset.toFloat(), fireArea, fireBody, glide)
        }

        // pointers first, so the bubbles cover their roots
        Canvas(Modifier.fillMaxSize()) {
            tick()
            val cam = camera()
            val stroke = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round)
            for ((g, group) in groups.withIndex()) {
                val a = laid.anchor(g, cam) ?: continue
                val presence = laid.presence(g, cam)
                if (presence <= 0f) continue
                // a quiet marker away from the centre is an icon: no pointer
                val pointer = presence * (1f - laid.quiet(g, cam))
                for (i in group.second.indices) {
                    val c = laid.center(g, i, cam, clock, now())
                    val half = 5.dp.toPx()
                    val tip = Offset(a.x, a.y - 3.dp.toPx())
                    val root = Offset(c.x, c.y + BUBBLE.toPx() / 2f - 4.dp.toPx())
                    val path = Path().apply {
                        moveTo(root.x - half, root.y); lineTo(tip.x, tip.y); lineTo(root.x + half, root.y); close()
                    }
                    if (pointer <= 0f) continue
                    drawPath(path, group.second[i].fill(threat), alpha = pointer)
                    drawPath(path, BubbleInk, style = stroke, alpha = pointer)
                }
                // where it is: a small ring on the ground, pulsing for a threat
                val m = group.second.first()
                val urgent = m.kind == TownMarker.Kind.EVENT && threat
                val pulse = if (urgent) (clock.value * 2f) % 1f else 0f
                drawCircle(if (urgent) TriglavRed.copy(alpha = 0.9f - 0.9f * pulse) else BubbleInk.copy(alpha = 0.55f), radius = 3.dp.toPx() + pulse * 10.dp.toPx(), center = a, style = Stroke(2.dp.toPx()))
            }
        }
        for ((g, group) in groups.withIndex()) {
            for ((i, m) in group.second.withIndex()) key(m.id) {
                Bubble(
                    m, badge(m), threat, face(m),
                    onTap = { if (laid.presence(g, camera()) >= 0.5f) onTap(m) },
                    presence = { laid.presence(g, camera()) },
                    quiet = { laid.quiet(g, camera()) },
                    // where it's drawn, gliding or not: a tap hits what's seen; stacked with others where there's no room
                    // near, the nearer person's (lower on screen) over the farther one's, as the village is drawn
                    modifier = Modifier.layout { measurable, c ->
                        val p = measurable.measure(c)
                        layout(p.width, p.height) {
                            tick()
                            val cam = camera()
                            val at = laid.center(g, i, cam, clock, now())
                            val depth = BUBBLES_Z + (laid.anchor(g, cam)?.y ?: 0f) + i * 0.01f
                            p.place((at.x - TOUCH.toPx() / 2f).roundToInt(), (at.y - TOUCH.toPx() / 2f).roundToInt(), zIndex = depth)
                        }
                    },
                )
            }
        }
        // a small chip at the screen's side while bubbles are off it there: how many, the first one's emoji; a tap brings it in
        if (onReveal != null) {
            val off by remember(laid) { derivedStateOf { tick(); laid.offScreen(camera()) } }
            for (left in listOf(true, false)) {
                val side = if (left) off.first else off.second
                // where it was last, for its fade out
                val shownAt = remember(left) { intArrayOf(0) }
                EdgeChip(
                    side?.let { groups[it.group].second.getOrNull(it.bubble) }, side?.n ?: 0, left,
                    top = {
                        val y = side?.let { laid.anchor(it.group, camera())?.y } ?: return@EdgeChip shownAt[0]
                        val chip = EDGE_CHIP.toPx()
                        val lo = topInset + 8.dp.toPx(); val hi = viewH - bottomInset - 8.dp.toPx() - chip
                        (y - chip / 2f).coerceIn(lo, max(lo, hi)).roundToInt().also { shownAt[0] = it }
                    },
                    onTap = { side?.let { s -> laid.place(s.group)?.let(onReveal) } },
                    // over the bubbles, one cut by the edge beside it too
                    modifier = Modifier.align(if (left) Alignment.TopStart else Alignment.TopEnd).zIndex(CHIPS_Z),
                )
            }
        }
    }
}

/** The edge chip's height, and the waiting chip's ([WaitingChip]). */
private val EDGE_CHIP = 30.dp

/** What the waiting chip takes above the bottom bar (its height and air): the edge chips keep above it. */
val WAITING_CHIP_ROOM = EDGE_CHIP + 10.dp

/**
 * The map's one quiet chip for the requests waiting in the Scroll's "Later" (companion/SCENES.md "Bubbles in the village"):
 * "📜 +5", small and dark like the edge chips, over the bottom bar's scroll; a tap opens the Scroll at the villagers'
 * requests ([onTap]). It fades in and out as [n] (the requests waiting: [si.lanisce.lani.game.scene.TownMarkers.waiting])
 * comes and goes; TalkBack: "Še 5 prošenj čaka v zvitku · 5 more requests wait in the scroll".
 */
@Composable
fun WaitingChip(n: Int, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val last = remember { intArrayOf(0) }
    if (n > 0) last[0] = n
    AnimatedVisibility(n > 0, modifier, enter = fadeIn(), exit = fadeOut()) {
        val count = last[0]
        val what = bi("townBubbles.waiting", "n" to count)
        Row(
            Modifier
                .height(EDGE_CHIP)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(15.dp))
                .clickable(onClick = onTap)
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = what
                    onClick { onTap(); true }
                }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📜 +$count", style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
    }
}

/** Drawing order: the bubbles over their pointers, by their places' height on screen (px) above this; the edge chips over all. */
private const val BUBBLES_Z = 10_000f
private const val CHIPS_Z = 100_000f

/**
 * The chip at the screen's [left] or right edge while [n] bubbles are off it there ([offScreen]), [first] the one that
 * comes first: "‹ 🐺 2", "2 🐺 ›", small and dark like the map's other buttons, at [top] (px, read when placing: beside
 * that bubble's place, between the HUD and the bottom bar). A tap brings [first] onto the screen ([onTap]); it fades in
 * and out as bubbles leave the screen there or all come back.
 */
@Composable
private fun EdgeChip(first: TownMarker?, n: Int, left: Boolean, top: Density.() -> Int, onTap: () -> Unit, modifier: Modifier) {
    // the last chip shown, for its fade out
    val last = remember { mutableStateOf<Pair<TownMarker, Int>?>(null) }
    if (first != null) last.value = first to n
    AnimatedVisibility(first != null, modifier.offset { IntOffset(0, top()) }, enter = fadeIn(), exit = fadeOut()) {
        val (m, count) = last.value ?: return@AnimatedVisibility
        val shape = if (left) RoundedCornerShape(topEnd = 15.dp, bottomEnd = 15.dp) else RoundedCornerShape(topStart = 15.dp, bottomStart = 15.dp)
        val what = bi(if (left) "townBubbles.offLeft" else "townBubbles.offRight", "n" to count, "what" to m.label)
        Row(
            Modifier
                .height(EDGE_CHIP)
                .background(Color.Black.copy(alpha = 0.5f), shape)
                .clickable(onClick = onTap)
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = what
                    onClick { onTap(); true }
                }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(if (left) "‹ ${m.emoji} $count" else "$count ${m.emoji} ›", style = style)
        }
    }
}

/**
 * Where each bubble goes on screen for a camera; pure arithmetic, run in the layout and draw phases. All the
 * places are laid out together ([layoutBubbles]) once per camera position (and per step of a followed villager),
 * off the fire's tap area ([fireKeepOff]), each held to where it was a frame ago against its place (the layout's
 * hysteresis); then once a frame [glide] takes the bubbles there, gliding where the layout stepped; the bob is added on
 * top (the fire's air allows for it).
 */
private class BubbleLayout(
    private val groups: List<Pair<AnchorKey, List<TownMarker>>>,
    private val anchors: Map<TownPlace, CanvasPoint>,
    private val anchorOf: (String) -> CanvasPoint?,
    private val density: Float,
    private val viewW: Float,
    private val viewH: Float,
    private val topInset: Float,
    private val hudInset: Float,
    private val bottomInset: Float,
    private val fireArea: FloatArray?,
    private val fireBody: CanvasPoint?,
    private val glide: BubbleGlide,
) {
    private val fire = anchors[TownPlace.Fire]
    private val ids = groups.map { (_, ms) -> ms.map { it.id } }
    private var lastCam: Camera? = null
    private var lastAnchors: List<CanvasPoint?> = emptyList()
    private var laid: List<List<BubbleOffset>> = emptyList()
    /** The laid out bubbles' markers and places (dp), in order, for [glide]. */
    private var laidIds: List<String> = emptyList()
    private var places: List<BubbleOffset> = emptyList()
    private var shownFor: List<List<BubbleOffset>>? = null
    private var shownAt = Long.MIN_VALUE
    private var shown: List<List<BubbleOffset>> = emptyList()
    private val minY get() = topInset / density + BUBBLE.value / 2f
    private val maxX get() = viewW / density - EDGE

    /** The group's anchor in nominal canvas px: the villager when they're out, else their place. */
    fun place(g: Int): CanvasPoint? = if (g in groups.indices) nominal(g) else null

    private fun nominal(g: Int): CanvasPoint? {
        val key = groups[g].first
        if (key is AnchorKey.Person) anchorOf(key.id)?.let { return it }
        return anchors[key.place] ?: fire
    }

    fun anchor(g: Int, cam: Camera): Offset? {
        if (g !in groups.indices) return null
        val p = nominal(g) ?: return null
        return Offset(cam.toScreenX(p.x), cam.toScreenY(p.y))
    }

    /** How quiet the group's bubbles are now (0: full; a quiet marker away from the view's centre: 1, see [quietness]). */
    fun quiet(g: Int, cam: Camera): Float {
        if (g !in groups.indices || groups[g].second.any { !it.quiet() }) return 0f
        val a = anchor(g, cam) ?: return 0f
        return quietness(a.x - viewW / 2f, a.y - viewH / 2f, minOf(viewW, viewH))
    }

    /** How much the group's bubbles show (they fade as their place goes under the HUD). */
    fun presence(g: Int, cam: Camera): Float {
        val a = anchor(g, cam) ?: return 0f
        return bubblePresence(a.y / density, hudInset / density + BUBBLE.value / 2f, APART)
    }

    /** Each bubble's spot for [cam] (dp), laid out again when the camera or a followed villager moved. */
    private fun positions(cam: Camera): List<List<BubbleOffset>> {
        val now = groups.indices.map { nominal(it) }
        if (cam == lastCam && now == lastAnchors) return laid
        val gs = groups.mapIndexed { g, (_, ms) ->
            val a = anchor(g, cam)
            if (a == null) BubbleGroup(0f, 0f, 0) else BubbleGroup(a.x / density, a.y / density, ms.size)
        }
        val keepOff = if (fireArea != null && fireBody != null) fireKeepOff(fireArea, fireBody, cam, density) else null
        laid = layoutBubbles(
            gs, size = APART, spacing = SPACING, lift = LIFT.value, minX = EDGE, maxX = maxX, minY = minY, keepOff = keepOff,
            was = { g, i -> glide.offset(ids[g][i]) }, nudge = BUBBLE_NUDGE, reach = BUBBLE_REACH,
        )
        places = laid.indices.flatMap { g -> List(laid[g].size) { BubbleOffset(gs[g].x, gs[g].y) } }
        laidIds = laid.indices.flatMap { g -> ids[g].take(laid[g].size) }
        lastCam = cam
        lastAnchors = now
        return laid
    }

    /** Where each bubble shows at the frame's time [nanos] (dp): on its way to its spot, once a frame. */
    private fun shown(cam: Camera, nanos: Long): List<List<BubbleOffset>> {
        val spots = positions(cam)
        if (spots === shownFor && nanos == shownAt) return shown
        val flat = glide.step(nanos, laidIds, spots.flatten(), places, minX = EDGE, maxX = maxX, minY = minY)
        var k = 0
        shown = spots.map { l -> List(l.size) { flat[k++] } }
        shownFor = spots
        shownAt = nanos
        return shown
    }

    /**
     * The bubbles wholly off the screen to the left and to the right ([offScreen]) whose places are beside it, between the
     * HUD and the bottom bar: the event first (a threat before all), then someone there now, then a request.
     */
    fun offScreen(cam: Camera): Pair<OffScreen?, OffScreen?> {
        val spots = positions(cam)
        val at = groups.indices.map { g -> anchor(g, cam)?.let { BubbleOffset(it.x / density, it.y / density) } }
        fun rank(g: Int, i: Int): Int {
            val m = groups[g].second.getOrNull(i) ?: return 9
            return when (m.kind) { TownMarker.Kind.EVENT -> 0; TownMarker.Kind.HAPPENING -> 1; TownMarker.Kind.QUEST -> 2; TownMarker.Kind.TIP -> 3 }
        }
        return offScreen(spots, at, ::rank, half = BUBBLE.value / 2f, width = viewW / density, top = hudInset / density, bottom = (viewH - bottomInset) / density)
    }

    fun center(g: Int, i: Int, cam: Camera, clock: State<Float>, nanos: Long): Offset {
        val p = shown(cam, nanos)[g].getOrNull(i) ?: return Offset(-1000f, -1000f)
        val bob = sin(2 * PI * (clock.value + i * 0.17f + g * 0.31f)).toFloat() * 3f
        return Offset(p.x * density, (p.y + bob) * density)
    }
}

/**
 * One bubble; [presence] (0..1, read in the draw phase) fades it out as its place goes under the HUD. A new one pops
 * up and fades in where it is ([BubbleGlide] starts it at its spot).
 */
@Composable
private fun Bubble(
    m: TownMarker, badge: String?, threat: Boolean, face: Villager?, onTap: () -> Unit, presence: () -> Float, modifier: Modifier,
    quiet: () -> Float = { 0f },
) {
    val pop = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { fade.animateTo(1f, tween(200)) }
        pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }
    val urgent = m.kind == TownMarker.Kind.EVENT && threat
    val wobble = if (urgent) rememberInfiniteTransition(label = "urgent").animateFloat(-7f, 7f, infiniteRepeatable(tween(240), androidx.compose.animation.core.RepeatMode.Reverse), label = "r").value else 0f
    val density = LocalDensity.current
    // fixed sizes: the bubble doesn't grow with the font scale, so its text mustn't either (line height included)
    fun fixed(size: Float) = with(density) { TextStyle(fontSize = size.dp.toSp(), lineHeight = (size * 1.2f).dp.toSp(), platformStyle = PlatformTextStyle(includeFontPadding = false)) }
    val what = if (m.id.startsWith("cluster:") || m.id.startsWith("more:")) bi("townBubbles.openPlace") else when (m.kind) {
        TownMarker.Kind.QUEST -> if (m.id.startsWith("quest:tutor-")) bi("townBubbles.requestFromTutor") else bi("common.request")
        TownMarker.Kind.HAPPENING -> bi("townBubbles.happeningNow")
        TownMarker.Kind.EVENT -> bi("townBubbles.event")
        TownMarker.Kind.TIP -> "💡 ${bi("tips.tip")}"
    }
    Box(
        modifier
            .size(TOUCH)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value; transformOrigin = TransformOrigin(0.5f, 1f); rotationZ = wobble; alpha = presence() * fade.value }
            .clickable(onClick = onTap)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = "${m.label}, $what"
                onClick { onTap(); true }
            },
    ) {
        // a quiet marker away from the view's centre: smaller and fainter, its tap target as big
        val small = Modifier.graphicsLayer {
            val k = quiet()
            val scale = 1f - (1f - QUIET_SCALE) * k
            scaleX = scale; scaleY = scale; alpha = 1f - (1f - QUIET_ALPHA) * k
            transformOrigin = TransformOrigin(0.5f, 1f)
        }
        if (face != null) {
            // someone far off: a small bubble with their face, a little lower (it points at a far place)
            Box(
                Modifier.align(Alignment.BottomCenter).then(small).size(FACE).shadow(3.dp, PixelShape).background(m.fill(threat), PixelShape).border(2.dp, BubbleInk, PixelShape),
                contentAlignment = Alignment.Center,
            ) { Portrait(face.art, modifier = Modifier.size(FACE - 6.dp), px = 32, animate = false, seed = face.id.hashCode()) }
        } else Box(
            Modifier.align(Alignment.Center).then(small).size(BUBBLE).shadow(4.dp, PixelShape).background(m.fill(threat), PixelShape)
                // a tip: a gold rim, gentle (it never wobbles or pulses)
                .border(if (m.kind == TownMarker.Kind.TIP) 3.dp else 2.dp, if (m.kind == TownMarker.Kind.TIP) TipRim else BubbleInk, PixelShape),
            contentAlignment = Alignment.Center,
        ) { Text(m.emoji, style = fixed(22f)) }
        if (m.kind == TownMarker.Kind.TIP) {
            Box(Modifier.align(Alignment.TopEnd).size(20.dp).background(Color.White, CircleShape).border(1.5.dp, TipRim, CircleShape), contentAlignment = Alignment.Center) {
                Text("💡", style = fixed(11f))
            }
        }
        if (m.kind == TownMarker.Kind.QUEST) {
            Box(Modifier.align(Alignment.TopEnd).graphicsLayer { alpha = 1f - quiet() }.size(18.dp).background(XpGold, CircleShape).border(1.5.dp, BubbleInk, CircleShape), contentAlignment = Alignment.Center) {
                Text("!", color = BubbleInk, fontWeight = FontWeight.Black, style = fixed(12f))
            }
        }
        if (badge != null) {
            // a number counts what a zoomed-out bubble stands for; an emoji says what a happening is about
            val count = badge.all { it.isDigit() }
            Box(
                Modifier.align(Alignment.BottomEnd).graphicsLayer { alpha = 1f - quiet() }.size(20.dp).background(if (count) BubbleInk else Color.White, CircleShape).border(1.5.dp, if (count) Color.White else BubbleInk, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(badge, color = if (count) Color.White else Color.Unspecified, fontWeight = if (count) FontWeight.Black else null, style = fixed(11f))
            }
        }
    }
}

/**
 * The short calls over people's heads while the village reacts to its event (companion/GAME.md "People react":
 * "Volkovi!", "Hitro noter!"): the last frame's [shouts] (who and what, [si.lanisce.lani.game.render.VillageRenderer.shouts]),
 * each a small speech bubble over its person at [anchorOf] (nominal canvas pixels, through [camera]), its tail down to
 * them, popping in; none that would reach under the HUD ([topInset], px). Not tappable: a tap goes to whoever is under
 * it; nor read out by TalkBack (the person's card says it). [tick]: the clock, read each frame.
 */
@Composable
internal fun TownShouts(
    shouts: () -> List<Pair<String, si.lanisce.lani.game.culture.Text>>, anchorOf: (String) -> CanvasPoint?, camera: () -> Camera, tick: () -> Long,
    topInset: Int = 0,
) {
    tick() // a new frame, perhaps someone else calling out
    val density = LocalDensity.current
    val gap = with(density) { 6.dp.roundToPx() }
    fun fixed(size: Float) = with(density) { TextStyle(fontSize = size.dp.toSp(), lineHeight = (size * 1.2f).dp.toSp(), platformStyle = PlatformTextStyle(includeFontPadding = false)) }
    for ((id, text) in shouts()) {
        val a = anchorOf(id) ?: continue
        key(id, text.target) {
            val pop = remember { Animatable(0.4f) }
            LaunchedEffect(Unit) {
                // QA (a debug build) times its screenshot of a call by this line (companion/bin/qa "reactions")
                if (si.lanisce.lani.app.QaHooks.enabled) android.util.Log.d("Shout", "$id ${text.target}")
                pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            }
            Box(
                Modifier
                    .layout { m, c ->
                        val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
                        layout(0, 0) {
                            val cam = camera()
                            val x = cam.toScreenX(a.x).roundToInt(); val y = cam.toScreenY(a.y).roundToInt()
                            // under the HUD it isn't seen: off the screen instead
                            if (y - p.height - gap >= topInset) p.place(x - p.width / 2, y - p.height - gap) else p.place(-10_000, -10_000)
                        }
                    }
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value; transformOrigin = TransformOrigin(0.5f, 1f) }
                    .clearAndSetSemantics { }
                    .zIndex(2f),
            ) {
                Box(
                    Modifier.shadow(3.dp, RoundedCornerShape(10.dp)).background(BubblePaper, RoundedCornerShape(10.dp)).border(2.dp, BubbleInk, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) { Text(text.target, color = BubbleInk, fontWeight = FontWeight.Black, style = fixed(14f)) }
            }
        }
    }
}
