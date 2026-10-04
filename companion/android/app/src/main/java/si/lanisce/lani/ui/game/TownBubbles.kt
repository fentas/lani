package si.lanisce.lani.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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

/** How a marker's bubble looks. */
private fun TownMarker.fill(threat: Boolean): Color = when {
    kind == TownMarker.Kind.EVENT && threat -> TriglavRed
    kind == TownMarker.Kind.EVENT -> XpGold
    kind == TownMarker.Kind.HAPPENING && !id.startsWith("more:") -> BubbleMint
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
 * way (see [layoutBubbles]); one whose spot changes glides there ([BubbleGlide]), its tap target with it. Positions
 * follow [camera] and the clock's [tick] in the layout and draw phases, so panning, zooming and the villagers' steps
 * never recompose them.
 *
 * @param threat the village's event is a threat (wolves, a bear, a storm): its bubble is red and pulses
 * @param badge a small second emoji for a marker (what a happening is about, e.g. 🍲), or null
 * @param topInset screen pixels the HUD covers: bubbles stay below it
 * @param followId the villager a marker belongs to, when they're drawn on the map
 * @param anchorOf where that villager is right now (nominal canvas px, above their head), or null when they aren't out
 * @param tick the renderer's clock, read in the layout and draw phases so the bubbles keep up with the people
 * @param face the villager a marker's bubble shows the face of, small, instead of its emoji: someone away from the
 *   clearing, not on the map (in the mountains at the horizon, at the vineyard out in the woods)
 * @param fireArea the fire's tap area (nominal canvas px, [si.lanisce.lani.game.render.TownAnchors.fireArea]): the
 *   bubbles keep off it and off the fire's screen reader target (see [layoutBubbles]), so a tap on the fire is the
 *   fire's, whoever stands beside it with a bubble
 * @param fireBody the middle of the fire's body, where its screen reader's target is centred
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
    followId: (TownMarker) -> String? = { null },
    anchorOf: (String) -> CanvasPoint? = { null },
    tick: () -> Long = { 0L },
    face: (TownMarker) -> Villager? = { null },
    fireArea: FloatArray? = null,
    fireBody: CanvasPoint? = null,
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
        val layout = remember(groups, anchors, density, viewW, topInset, fireArea, fireBody) {
            BubbleLayout(groups, anchors, anchorOf, density.density, viewW, topInset.toFloat(), fireArea, fireBody, glide)
        }

        // pointers first, so the bubbles cover their roots
        Canvas(Modifier.fillMaxSize()) {
            tick()
            val cam = camera()
            val stroke = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round)
            for ((g, group) in groups.withIndex()) {
                val a = layout.anchor(g, cam) ?: continue
                val presence = layout.presence(g, cam)
                if (presence <= 0f) continue
                for (i in group.second.indices) {
                    val c = layout.center(g, i, cam, clock, now())
                    val half = 5.dp.toPx()
                    val tip = Offset(a.x, a.y - 3.dp.toPx())
                    val root = Offset(c.x, c.y + BUBBLE.toPx() / 2f - 4.dp.toPx())
                    val path = Path().apply {
                        moveTo(root.x - half, root.y); lineTo(tip.x, tip.y); lineTo(root.x + half, root.y); close()
                    }
                    drawPath(path, group.second[i].fill(threat), alpha = presence)
                    drawPath(path, BubbleInk, style = stroke, alpha = presence)
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
                    onTap = { if (layout.presence(g, camera()) >= 0.5f) onTap(m) },
                    presence = { layout.presence(g, camera()) },
                    // where it's drawn, gliding or not: a tap hits what's seen
                    modifier = Modifier.offset {
                        tick()
                        val c = layout.center(g, i, camera(), clock, now())
                        IntOffset((c.x - TOUCH.toPx() / 2f).roundToInt(), (c.y - TOUCH.toPx() / 2f).roundToInt())
                    },
                )
            }
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
    private val topInset: Float,
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
    private fun nominal(g: Int): CanvasPoint? {
        val key = groups[g].first
        if (key is AnchorKey.Person) anchorOf(key.id)?.let { return it }
        return anchors[key.place] ?: fire
    }

    fun anchor(g: Int, cam: Camera): Offset? {
        val p = nominal(g) ?: return null
        return Offset(cam.toScreenX(p.x), cam.toScreenY(p.y))
    }

    /** How much the group's bubbles show (they fade as their place scrolls far past the top). */
    fun presence(g: Int, cam: Camera): Float {
        val a = anchor(g, cam) ?: return 0f
        return bubblePresence(a.y / density, minY, APART)
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
            was = { g, i -> glide.offset(ids[g][i]) },
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

    fun center(g: Int, i: Int, cam: Camera, clock: State<Float>, nanos: Long): Offset {
        val p = shown(cam, nanos)[g].getOrNull(i) ?: return Offset(-1000f, -1000f)
        val bob = sin(2 * PI * (clock.value + i * 0.17f + g * 0.31f)).toFloat() * 3f
        return Offset(p.x * density, (p.y + bob) * density)
    }
}

/**
 * One bubble; [presence] (0..1, read in the draw phase) fades it out as its place scrolls far past the top. A new one pops
 * up and fades in where it is ([BubbleGlide] starts it at its spot).
 */
@Composable
private fun Bubble(m: TownMarker, badge: String?, threat: Boolean, face: Villager?, onTap: () -> Unit, presence: () -> Float, modifier: Modifier) {
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
        if (face != null) {
            // someone far off: a small bubble with their face, a little lower (it points at a far place)
            Box(
                Modifier.align(Alignment.BottomCenter).size(FACE).shadow(3.dp, PixelShape).background(m.fill(threat), PixelShape).border(2.dp, BubbleInk, PixelShape),
                contentAlignment = Alignment.Center,
            ) { Portrait(face.art, modifier = Modifier.size(FACE - 6.dp), px = 32, animate = false, seed = face.id.hashCode()) }
        } else Box(
            Modifier.align(Alignment.Center).size(BUBBLE).shadow(4.dp, PixelShape).background(m.fill(threat), PixelShape).border(2.dp, BubbleInk, PixelShape),
            contentAlignment = Alignment.Center,
        ) { Text(m.emoji, style = fixed(22f)) }
        if (m.kind == TownMarker.Kind.QUEST) {
            Box(Modifier.align(Alignment.TopEnd).size(18.dp).background(XpGold, CircleShape).border(1.5.dp, BubbleInk, CircleShape), contentAlignment = Alignment.Center) {
                Text("!", color = BubbleInk, fontWeight = FontWeight.Black, style = fixed(12f))
            }
        }
        if (badge != null) {
            // a number counts what a zoomed-out bubble stands for; an emoji says what a happening is about
            val count = badge.all { it.isDigit() }
            Box(
                Modifier.align(Alignment.BottomEnd).size(20.dp).background(if (count) BubbleInk else Color.White, CircleShape).border(1.5.dp, if (count) Color.White else BubbleInk, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(badge, color = if (count) Color.White else Color.Unspecified, fontWeight = if (count) FontWeight.Black else null, style = fixed(11f))
            }
        }
    }
}
