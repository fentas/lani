package si.lanisce.lani.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import si.lanisce.lani.game.Calendar
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.LogEntry
import si.lanisce.lani.game.Projects
import si.lanisce.lani.game.Surprises
import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.QuestSource
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.l10n.inBase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** The scroll's (and the book's) sections, in order. */
enum class ScrollSection(val emoji: String, private val key: String, val ribbon: Color) {
    TODAY("📅", "townScroll.todayInVillage", Color(0xFFB0362A)),
    GOAL("🎯", "townScroll.nextGoal", Color(0xFFC98B1E)),
    PLACES("🗺️", "townScroll.places", Color(0xFF3F7A3A)),
    PEOPLE("👥", "townScroll.villagers", Color(0xFF2F5C9A)),
    /** "📖 Slovnica · Grammar": the rules the learner has met (ui/game/GrammarPages.kt). */
    GRAMMAR("📖", "townScroll.grammar", Color(0xFF6A3D8F)),
    CHRONICLE("📜", "townScroll.chronicle", Color(0xFF6B4A2B));

    /** The heading in the learner's target language ("Danes v vasi"), and in the base below it. */
    val target: String get() = inTarget(key)
    val base: String get() = inBase(key)
    val label: String get() = bi(key)
}

/** What the rows of the scroll do. */
class ScrollActions(
    val onEvent: () -> Unit,
    val onQuest: (Quest) -> Unit,
    /** Opens a scene, at a happening ("scene/happening") or none. */
    val onScene: (SceneSpec, String?) -> Unit,
    /** "Show me": closes the scroll and flies the camera there. */
    val onShow: (TownPlace) -> Unit,
    /** A place of the village: flies there and opens its card. */
    val onPlace: (TownPlace) -> Unit,
    /** "Prebivalci · Villagers": the register of the village's people and the friendship with each. */
    val onVillagers: (() -> Unit)? = null,
    /** Celebrates the festival that is on (its id). */
    val onFestival: (String) -> Unit = {},
    /** Plays the day's surprise (the pedlar: his goods in the chest). */
    val onSurprise: () -> Unit = {},
    /** The village projects, at one of them (its id) or all. */
    val onProjects: (String?) -> Unit = {},
    /** "Prijatelji · Friends": the towns linked with this one (ui/towns). */
    val onFriends: (() -> Unit)? = null,
    /** Meets someone who joined the village (their id): their introduction (companion/VILLAGERS.md "Arrivals"). */
    val onArrival: (String) -> Unit = {},
    /** Talks with someone at a spot now (the charcoal burner by his pile): his talk (companion/VILLAGERS.md "At the charcoal pile"). */
    val onKeeper: (si.lanisce.lani.game.scene.ActiveKeeper) -> Unit = {},
    /** "📖 Branje · Reading": the reading corner (ui/reading), from the grammar chapter. */
    val onReading: (() -> Unit)? = null,
)

/** Paper, ink and bindings; a warm night version for the dark theme. */
internal data class Paper(
    val paper: Color, val edge: Color, val ink: Color, val muted: Color, val accent: Color, val rule: Color,
    val wood: Color, val woodDark: Color, val brass: Color, val leather: Color, val leatherDark: Color, val row: Color,
)

private val DayPaper = Paper(
    paper = Color(0xFFF4E6C4), edge = Color(0xFFDDBF8A), ink = Color(0xFF3A2A16), muted = Color(0xFF6B563A), accent = Color(0xFF9C2F1E),
    rule = Color(0xFFC9AB7A), wood = Color(0xFF8A5A2E), woodDark = Color(0xFF4A2A12), brass = Color(0xFFD9A636),
    leather = Color(0xFF7A3322), leatherDark = Color(0xFF45180E), row = Color(0x1A6B4A2B),
)
internal val NightPaper = Paper(
    paper = Color(0xFF30281E), edge = Color(0xFF4E3F2B), ink = Color(0xFFF1E3C4), muted = Color(0xFFC6B08A), accent = Color(0xFFF08A6A),
    rule = Color(0xFF65533A), wood = Color(0xFF6A4322), woodDark = Color(0xFF2E1909), brass = Color(0xFFB88A2A),
    leather = Color(0xFF55231A), leatherDark = Color(0xFF2A0F09), row = Color(0x22F1E3C4),
)

@Composable
internal fun paper(): Paper = if (isSystemInDarkTheme()) NightPaper else DayPaper

internal val Serif = FontFamily.Serif

/**
 * The community overview over the village: a parchment scroll that unrolls (up to Zaselek), or from Vas on a
 * leather book with a spine and page tabs, turning its pages. [start] is the section to show first.
 */
@Composable
fun TownScroll(
    visible: Boolean,
    book: Boolean,
    start: ScrollSection,
    overview: TownOverview,
    state: GameState,
    actions: ScrollActions,
    onClose: () -> Unit,
    /** The grammar chapter: the pages met, and what opening one does. */
    grammar: GrammarShelf? = null,
    goal: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        progress.animateTo(if (visible) 1f else 0f, tween(if (visible) 760 else 420, easing = FastOutSlowInEasing))
    }
    if (!visible && progress.value == 0f) return
    BackHandler(enabled = visible, onBack = onClose)
    val pal = paper()
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = min(1f, progress.value * 1.4f) }
                .background(Color(0xB3120B05))
                .pointerInput(Unit) { detectTapGestures { onClose() } }
                .clearAndSetSemantics { contentDescription = Labels.CLOSE; onClick { onClose(); true } },
        )
        val content = remember(overview, state, actions, grammar) { Contents(overview, state, actions, grammar) }
        if (book) Book(progress.value, start, pal, content, goal, onClose)
        else Scroll(progress.value, start, pal, content, goal, onClose)
    }
}

/** The data every page draws from. */
private class Contents(val overview: TownOverview, val state: GameState, val actions: ScrollActions, val grammar: GrammarShelf?)

// --- the scroll ---------------------------------------------------------------------------------

@Composable
private fun Scroll(p: Float, start: ScrollSection, pal: Paper, c: Contents, goal: @Composable () -> Unit, onClose: () -> Unit) {
    val list = rememberLazyListState()
    val headers = remember { HashMap<ScrollSection, Int>() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(start) { headers[start]?.let { if (it > 0) list.scrollToItem(it) } }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
        val rod = 30.dp
        val full = with(density) { (maxHeight - rod * 2).roundToPx().coerceAtLeast(1) }
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().widthIn(max = 560.dp)) {
            ScrollRoll(pal, top = true, Modifier.height(rod))
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                    .clipToBounds()
                    .layout { m, cons ->
                        // laid out at full height, shown as far as it has unrolled: the text doesn't reflow while it opens
                        val pl = m.measure(cons.copy(minHeight = full, maxHeight = full))
                        layout(pl.width, (full * p).roundToInt()) { pl.place(0, 0) }
                    }
                    .parchment(pal)
                    .pointerInput(Unit) { detectTapGestures { } }, // taps on the paper don't close it
            ) {
                Column(Modifier.fillMaxSize()) {
                    ScrollHeader(c.state, pal, onClose) { s -> headers[s]?.let { scope.launch { list.animateScrollToItem(it) } } }
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth(),
                        state = list,
                        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val k = Counted(this)
                        for (s in ScrollSection.entries) {
                            headers[s] = k.n
                            k.item("h:${s.name}") { SectionTitle(s, pal, Modifier.padding(top = if (s == ScrollSection.TODAY) 4.dp else 18.dp)) }
                            section(k, s, c, pal, goal) { id -> c.grammar?.open?.invoke(id) }
                        }
                    }
                }
                // the roll's shadow on the paper
                Box(Modifier.fillMaxWidth().height(10.dp).background(Brush.verticalGradient(listOf(pal.woodDark.copy(alpha = 0.35f), Color.Transparent))))
            }
            ScrollRoll(pal, top = false, Modifier.height(rod))
        }
    }
}

/** A LazyListScope that counts its items, so the index chips can jump to a section's title. */
internal class Counted(val scope: LazyListScope) {
    var n = 0
    fun item(key: String, content: @Composable LazyItemScope.() -> Unit) {
        scope.item(key = key) { content() }
        n++
    }
}

/** A rolled end of the scroll: paper wound on a wooden rod with brass knobs. */
@Composable
private fun ScrollRoll(pal: Paper, top: Boolean, modifier: Modifier) {
    Canvas(modifier.fillMaxWidth()) {
        val h = size.height; val w = size.width
        val knob = h * 0.62f
        // the rod, sticking out on both sides
        drawRoundRect(
            Brush.verticalGradient(listOf(pal.wood, lerp(pal.wood, Color.White, 0.25f), pal.woodDark), startY = h * 0.3f, endY = h * 0.7f),
            topLeft = Offset(knob * 0.5f, h * 0.32f), size = Size(w - knob, h * 0.36f), cornerRadius = CornerRadius(h * 0.18f),
        )
        // brass knobs
        for (x in listOf(knob * 0.55f, w - knob * 0.55f)) {
            drawCircle(Brush.radialGradient(listOf(lerp(pal.brass, Color.White, 0.45f), pal.brass, lerp(pal.brass, Color.Black, 0.45f)), center = Offset(x - knob * 0.12f, h / 2 - knob * 0.12f), radius = knob * 0.6f), radius = knob / 2, center = Offset(x, h / 2))
        }
        // the wound paper: a cylinder, darker at its rims
        val px = knob * 1.05f
        drawRoundRect(
            Brush.verticalGradient(listOf(pal.edge, pal.paper, lerp(pal.paper, Color.White, 0.2f), pal.paper, lerp(pal.edge, Color.Black, 0.25f))),
            topLeft = Offset(px, h * 0.08f), size = Size(w - 2 * px, h * 0.84f), cornerRadius = CornerRadius(h * 0.3f),
        )
        // the paper's end curling off the roll towards the sheet
        val lipY = if (top) h * 0.8f else h * 0.08f
        drawRect(pal.edge.copy(alpha = 0.8f), topLeft = Offset(px + 6f, lipY), size = Size(w - 2 * px - 12f, h * 0.12f))
    }
}

/** Parchment: warm paper, darker burnt edges, a few fibres, a double rule inside the border. */
internal fun Modifier.parchment(pal: Paper): Modifier = drawBehind {
    drawRect(pal.paper)
    val edge = 18.dp.toPx()
    drawRect(Brush.horizontalGradient(listOf(pal.edge, pal.paper), endX = edge), size = Size(edge, size.height))
    drawRect(Brush.horizontalGradient(listOf(pal.paper, pal.edge), startX = size.width - edge, endX = size.width), topLeft = Offset(size.width - edge, 0f), size = Size(edge, size.height))
    val r = Random(7)
    repeat((size.height / 9f).toInt().coerceAtMost(260)) {
        val y = r.nextFloat() * size.height; val x = r.nextFloat() * size.width
        drawLine(pal.rule.copy(alpha = 0.18f + r.nextFloat() * 0.12f), Offset(x, y), Offset(x + 20f + r.nextFloat() * 70f, y + r.nextFloat() * 2f - 1f), strokeWidth = 1.2f)
    }
    val inset = 7.dp.toPx()
    drawRect(pal.rule, topLeft = Offset(inset, 0f), size = Size(size.width - 2 * inset, size.height), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
}

@Composable
private fun ScrollHeader(state: GameState, pal: Paper, onClose: () -> Unit, onJump: (ScrollSection) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${state.age.emoji} ${inTarget("gameIntro.myVillage")}", fontFamily = Serif, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = pal.ink, modifier = Modifier.semantics { heading() })
                Text(bi("townScroll.villageScroll"), fontFamily = Serif, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyMedium, color = pal.muted)
            }
            CloseSeal(pal, onClose)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (s in ScrollSection.entries) {
                Surface(
                    onClick = { onJump(s) }, shape = RoundedCornerShape(50), color = s.ribbon.copy(alpha = 0.16f),
                    modifier = Modifier.weight(1f).heightIn(min = 40.dp).clearAndSetSemantics { role = Role.Button; contentDescription = s.label; onClick { onJump(s); true } },
                ) { Box(contentAlignment = Alignment.Center) { Text(s.emoji, style = MaterialTheme.typography.titleMedium) } }
            }
        }
    }
}

/** A red wax seal: closes the scroll or the book. */
@Composable
internal fun CloseSeal(pal: Paper, onClose: () -> Unit) {
    Surface(onClick = onClose, shape = CircleShape, color = pal.accent, shadowElevation = 3.dp, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) { EmojiLabel("✕", Labels.CLOSE, color = Color.White, style = MaterialTheme.typography.titleMedium) }
    }
}

// --- the book -----------------------------------------------------------------------------------

/**
 * A leaf of the book: a [section], or (in the grammar chapter) the page of one [rule]. A rule's page lies after its
 * chapter's list, so turning to it turns forwards and back to the list turns backwards.
 */
private data class Leaf(val section: ScrollSection, val rule: String? = null) {
    val order: Int get() = section.ordinal * 2 + if (rule != null) 1 else 0
}

@Composable
private fun Book(p: Float, start: ScrollSection, pal: Paper, c: Contents, goal: @Composable () -> Unit, onClose: () -> Unit) {
    var target by remember { mutableStateOf(Leaf(start)) }
    LaunchedEffect(start) { target = Leaf(start) }
    // the page underneath, and the one turning over it (forwards: the old page leaves; backwards: the new one lands)
    var under by remember { mutableStateOf(Leaf(start)) }
    var turning by remember { mutableStateOf<Leaf?>(null) }
    val turn = remember { Animatable(0f) }
    val open: (Leaf) -> Unit = { target = it }
    // back from a rule's page leads to its chapter first
    BackHandler(enabled = target.rule != null) { target = Leaf(target.section) }
    LaunchedEffect(target) {
        if (target == under) return@LaunchedEffect
        if (target.order >= under.order) {
            turning = under; under = target
            turn.snapTo(0f); turn.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        } else {
            turning = target
            turn.snapTo(1f); turn.animateTo(0f, tween(560, easing = FastOutSlowInEasing))
            under = target
        }
        turning = null
    }
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 8.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.fillMaxSize().widthIn(max = 600.dp).graphicsLayer {
                val k = 0.9f + 0.1f * p
                scaleX = k; scaleY = k; alpha = min(1f, p * 2f)
            },
        ) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                // the cover, a little bigger than the pages; the spine on the left
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 14.dp, bottomEnd = 14.dp))
                        .background(Brush.horizontalGradient(listOf(pal.leatherDark, pal.leather, pal.leather, lerp(pal.leather, pal.leatherDark, 0.4f))))
                        .drawBehind {
                            // the spine: darker, with raised gold bands
                            val spine = 20.dp.toPx()
                            drawRect(Brush.horizontalGradient(listOf(pal.leatherDark, lerp(pal.leather, Color.Black, 0.2f), pal.leatherDark), endX = spine), size = Size(spine, size.height))
                            for (k in 1..4) {
                                val y = size.height * k / 5f
                                drawRect(pal.brass, topLeft = Offset(2.dp.toPx(), y - 3.dp.toPx()), size = Size(spine - 4.dp.toPx(), 2.dp.toPx()))
                                drawRect(pal.brass.copy(alpha = 0.6f), topLeft = Offset(2.dp.toPx(), y + 2.dp.toPx()), size = Size(spine - 4.dp.toPx(), 1.dp.toPx()))
                            }
                        },
                )
                // the page block: page edges on the right and the bottom, the pages themselves
                Box(Modifier.fillMaxSize().padding(start = 22.dp, top = 8.dp, end = 8.dp, bottom = 10.dp)) {
                    PageEdges(pal)
                    Box(Modifier.fillMaxSize().padding(end = 5.dp, bottom = 5.dp)) {
                        Page(under, pal, c, goal, onClose, open, shade = if (turning != null && under != turning) (1f - turn.value) * 0.28f else 0f)
                        turning?.let { t ->
                            Page(
                                t, pal, c, goal, onClose, open, shade = turn.value * 0.2f,
                                // a flat fold towards the spine (no 3D: perspective layers crash some GPU emulations)
                                modifier = Modifier.graphicsLayer {
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    scaleX = 1f - turn.value
                                },
                            )
                        }
                    }
                }
                // the cover swinging open
                if (p < 1f) {
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            // the cover folds open towards the spine, flat like the pages
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            scaleX = 1f - p
                            alpha = if (p > 0.85f) (1f - p) / 0.15f else 1f
                        }.clip(RoundedCornerShape(8.dp, 14.dp, 14.dp, 8.dp))
                            .background(Brush.linearGradient(listOf(pal.leather, pal.leatherDark)))
                            .border(3.dp, pal.brass.copy(alpha = 0.7f), RoundedCornerShape(8.dp, 14.dp, 14.dp, 8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(c.state.age.emoji, style = MaterialTheme.typography.displayMedium)
                            Text(inTarget("townScroll.villageBook"), fontFamily = Serif, fontWeight = FontWeight.Bold, color = pal.brass, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
            // page tabs, sticking out of the pages
            Column(Modifier.padding(top = 56.dp).offset(x = (-10).dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (s in ScrollSection.entries) Tab(s, s == target.section, pal) { target = Leaf(s) }
            }
        }
    }
}

/** Stacked page edges peeking out at the right and the bottom of the page block. */
@Composable
private fun PageEdges(pal: Paper) {
    Canvas(Modifier.fillMaxSize()) {
        for (k in 5 downTo 1) {
            val o = k * 1.1f.dp.toPx()
            drawRoundRect(if (k % 2 == 0) pal.edge else pal.paper, topLeft = Offset(o, o), size = Size(size.width - o, size.height - o), cornerRadius = CornerRadius(4.dp.toPx()))
        }
    }
}

@Composable
private fun Tab(s: ScrollSection, active: Boolean, pal: Paper, onClick: () -> Unit) {
    val w: Dp = if (active) 48.dp else 40.dp
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp),
        color = if (active) s.ribbon else lerp(s.ribbon, pal.leatherDark, 0.35f),
        shadowElevation = if (active) 4.dp else 1.dp,
        modifier = Modifier.width(w).height(52.dp).semantics(mergeDescendants = true) {
            role = Role.Tab
            selected = active
            contentDescription = s.label
        },
    ) {
        Box(Modifier.padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
            Text(s.emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

@Composable
private fun Page(
    leaf: Leaf, pal: Paper, c: Contents, goal: @Composable () -> Unit, onClose: () -> Unit, open: (Leaf) -> Unit, shade: Float,
    modifier: Modifier = Modifier,
) {
    val s = leaf.section
    Box(
        modifier.fillMaxSize().clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)).parchment(pal)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        // the gutter's shadow along the spine
        Box(Modifier.fillMaxHeight().width(22.dp).background(Brush.horizontalGradient(listOf(pal.woodDark.copy(alpha = 0.35f), Color.Transparent))))
        val list: LazyListState = key(leaf) { rememberLazyListState() }
        val rule = leaf.rule?.let { c.grammar?.view?.invoke(it) }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 26.dp, end = 10.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (rule != null) RuleTitle(rule, pal, onBack = { open(Leaf(s)) }) else SectionTitle(s, pal)
                }
                CloseSeal(pal, onClose)
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = list,
                contentPadding = PaddingValues(start = 26.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val k = Counted(this)
                val shelf = c.grammar
                if (rule != null && shelf != null) rulePage(k, rule, pal, shelf) { id -> open(Leaf(ScrollSection.GRAMMAR, id)) }
                else section(k, s, c, pal, goal) { id -> open(Leaf(ScrollSection.GRAMMAR, id)) }
            }
            Text(
                "— ${s.ordinal + 1} —", fontFamily = Serif, color = pal.muted, style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        if (shade > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = shade)))
    }
}

// --- sections -----------------------------------------------------------------------------------

@Composable
private fun SectionTitle(s: ScrollSection, pal: Paper, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(s.target, fontFamily = Serif, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = pal.ink)
                Text(s.base, fontFamily = Serif, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall, color = pal.muted)
            }
        }
        Flourish(pal)
    }
}

/** A thin rule with a fleuron in the middle. */
@Composable
internal fun Flourish(pal: Paper) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(pal.rule))
        Text("  ❦  ", color = pal.accent.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.clearAndSetSemantics { })
        Box(Modifier.weight(1f).height(1.dp).background(pal.rule))
    }
}

/** Section [s]'s rows; [onRule] opens a page of the grammar chapter (the book turns to it, the scroll shows it over itself). */
private fun section(k: Counted, s: ScrollSection, c: Contents, pal: Paper, goal: @Composable () -> Unit, onRule: (String) -> Unit) {
    when (s) {
        ScrollSection.TODAY -> today(k, c, pal)
        ScrollSection.GOAL -> k.item("goal") { goal() }
        ScrollSection.PLACES -> places(k, c, pal)
        ScrollSection.PEOPLE -> people(k, c, pal)
        ScrollSection.GRAMMAR -> {
            // the reading corner, next to the rules: what there is to read at the learner's level
            c.actions.onReading?.let { open ->
                k.item("reading") { PaperRow(emoji = "📖", title = bi("readings.title"), sub = bi("readings.inBook"), pal = pal, onClick = open, onShow = null) }
            }
            grammarChapter(k, c.grammar, pal, onRule)
        }
        ScrollSection.CHRONICLE -> chronicle(k, c, pal)
    }
}

private val weekdays = listOf(
    "townScroll.monday", "townScroll.tuesday", "townScroll.wednesday", "townScroll.thursday", "townScroll.friday",
    "townScroll.saturday", "townScroll.sunday",
)

private fun today(k: Counted, c: Contents, pal: Paper) {
    val o = c.overview
    val a = c.actions
    k.item("date") {
        val d = LocalDate.now()
        val day = weekdays[d.dayOfWeek.value - 1]
        val (sl, en) = inTarget(day) to inBase(day)
        // tonight's moon: the real one's phase at ten in the evening ("Nocoj: ščip · Tonight: full moon"), as its card has it
        val moon = remember(d) {
            si.lanisce.lani.game.sky.Lunar.phase(si.lanisce.lani.game.sky.Astro.jd(d.atTime(22, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${sl.replaceFirstChar { it.uppercase() }}, ${d.dayOfMonth}. ${d.monthValue}. · $en  ${o.time.emoji()} ${o.time.label()}",
                fontFamily = Serif, fontStyle = FontStyle.Italic, color = pal.muted, style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${moon.emoji} ${bi("townScroll.moonTonight", "phase" to moon.key)}",
                fontFamily = Serif, fontStyle = FontStyle.Italic, color = pal.muted, style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    val event = o.today.filterIsInstance<TodayItem.Event>().firstOrNull()
    val now = o.today.filterIsInstance<TodayItem.Happening>().filter { it.now }
    val later = o.today.filterIsInstance<TodayItem.Happening>().filter { !it.now }
    val quests = o.today.filterIsInstance<TodayItem.Task>()
    val arrivals = o.today.filterIsInstance<TodayItem.Arrival>()
    val keepers = o.today.filterIsInstance<TodayItem.Keeper>()
    if (event != null) k.item("event") {
        val e = event.event
        val nowMs = rememberNow()
        val threat = e.kind.threat()
        Column(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                .background(Brush.horizontalGradient(if (threat) listOf(Color(0xFF8E0D22), Color(0xFFD0332A)) else listOf(Color(0xFFB36B00), Color(0xFFE0A21E))))
                .clickable(onClick = a.onEvent).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.kind.emoji, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.kind.headline(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("⏳ ${timeLeft(e.deadline, nowMs)} · ${event.place.where()}", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                }
                Surface(onClick = { a.onShow(event.place) }, shape = CircleShape, color = Color.Transparent, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { EmojiLabel("📍", bi("townScroll.showVillage")) }
                }
            }
            Surface(onClick = a.onEvent, shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.25f), modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(e.kind.action(), color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
                }
            }
        }
    }
    // who joined the village and waits to be met: their introduction, before anything else they could do
    if (arrivals.isNotEmpty()) {
        k.item("arrivalsTitle") { SubTitle("👋 ${bi("arrivals.toMeet")}", pal) }
        for (m in arrivals) k.item("arrival:${m.id}") {
            PaperRow(
                emoji = m.emoji, badge = "!", title = m.title, sub = m.place.where(),
                extra = "👋 ${bi("arrivals.meet")}",
                pal = pal, onClick = { a.onArrival(m.id) }, onShow = { a.onShow(m.place) },
            )
        }
    }
    village(k, c, pal)
    if (now.isNotEmpty() || keepers.isNotEmpty()) {
        k.item("nowTitle") { SubTitle(bi("townScroll.now", "timeEmoji" to (o.time.emoji())), pal) }
        for (h in now) k.item("now:${h.on.key}") {
            PaperRow(
                emoji = h.on.person?.emoji ?: h.on.happening.marker, badge = h.on.happening.marker.takeIf { it != h.on.person?.emoji },
                title = h.on.happening.title,
                sub = listOfNotNull(h.on.person?.name, h.place.where()).joinToString(" · ") +
                    // the learner's own words its dialog tests today
                    (if (h.words > 0) "\n📇 ${bi("dialogWords.inHappening", "n" to h.words)}" else ""),
                extra = rewardText(h.on.happening.reward)?.let { "💬 ${bi("townScroll.chat")}: $it" },
                pal = pal, onClick = { a.onScene(h.on.scene, h.on.key) }, onShow = { a.onShow(h.place) },
            )
        }
        // who sits at a spot now (the burner by his pile): his talk, and the way to him in the woods
        for (kp in keepers) k.item("now:${kp.keeper.key}") {
            PaperRow(
                emoji = kp.keeper.keeper.emoji, title = kp.keeper.title.bi(),
                sub = listOf(kp.keeper.keeper.name, kp.place.where()).joinToString(" · "),
                extra = rewardText(kp.keeper.keeper.reward)?.let { "💬 ${bi("townScroll.chat")}: $it" },
                pal = pal, onClick = { a.onKeeper(kp.keeper) }, onShow = { a.onShow(kp.place) },
            )
        }
    }
    if (later.isNotEmpty()) {
        k.item("laterTitle") { SubTitle("🕰️ ${bi("townScroll.laterToday")}", pal) }
        for (h in later) k.item("later:${h.on.key}") {
            PaperRow(
                emoji = h.time.emoji(), title = h.on.happening.title,
                sub = "${h.time.label()} · ${h.place.where()}", pal = pal, dim = true, onClick = null, onShow = { a.onShow(h.place) },
            )
        }
    }
    if (quests.isNotEmpty()) {
        k.item("questsTitle") { SubTitle("📋 ${bi("townScroll.villagersRequests")}", pal) }
        for (q in quests) k.item("quest:${q.quest.id}") {
            val nowMs = rememberNow()
            val tutor = q.quest.source == QuestSource.TUTOR
            PaperRow(
                emoji = q.quest.emoji, badge = "!", title = q.quest.title,
                // how close Jan is: the best try and the pass mark
                sub = listOfNotNull(
                    listOfNotNull(q.quest.giver, q.place.where(), q.quest.expiresAt.takeIf { it > 0 }?.let { "⏳ ${timeLeft(it, nowMs)}" }).joinToString(" · "),
                    progressText(q.quest),
                ).joinToString("\n"),
                extra = listOfNotNull(if (tutor) "🧑‍🏫 ${bi("townScroll.fromTutor")}" else null, formatRes(q.quest.reward).takeIf { it.isNotEmpty() }).joinToString("  "),
                pal = pal, onClick = { a.onQuest(q.quest) }, onShow = { a.onShow(q.place) },
            )
        }
    }
    // "📜 Kasneje · Later": the requests waiting (two of each villager are up), in the order they come up, and why; each opens
    if (o.later.isNotEmpty()) {
        k.item("questsLaterTitle") { SubTitle(laterTitle(), pal) }
        for (w in o.later) k.item("later-quest:${w.quest.id}") {
            val place = TownMarkers.questPlace(w.quest.giver, w.quest.source, c.state)
            PaperRow(
                emoji = w.quest.emoji, title = w.quest.title,
                sub = listOfNotNull(listOf(w.quest.giver, whyText(w.why)).joinToString(" · "), progressText(w.quest)).joinToString("\n"),
                pal = pal, dim = true, onClick = { a.onQuest(w.quest) }, onShow = { a.onShow(place) },
            )
        }
    }
    val v = o.village
    if (event == null && now.isEmpty() && quests.isEmpty() && arrivals.isEmpty() && keepers.isEmpty() && v.count == 0) k.item("quiet") {
        Text(
            bi("townScroll.quietDayVillageLook"),
            fontFamily = Serif, fontStyle = FontStyle.Italic, color = pal.muted, style = MaterialTheme.typography.bodyMedium,
        )
    }
    // "Jutri · Tomorrow": always something to come back for
    if (v.teasers.isNotEmpty()) {
        k.item("tomorrowTitle") { SubTitle("🌙 ${bi("townScroll.tomorrowSoon")}", pal) }
        for ((i, t) in v.teasers.take(3).withIndex()) k.item("tomorrow:$i") {
            PaperRow(emoji = t.emoji, title = t.text, sub = null, pal = pal, dim = true, onClick = null, onShow = null)
        }
    }
}

/**
 * The village's own day in the scroll: the festival that is on (or counting down), the day's surprise at the road,
 * and the projects with a step to take today.
 */
private fun village(k: Counted, c: Contents, pal: Paper) {
    val v = c.overview.village
    val a = c.actions
    v.festival?.let { d ->
        k.item("festival") {
            val f = d.festival
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .background(Brush.horizontalGradient(listOf(Color(0xFFB36B00), Color(0xFFE0A21E))))
                    .then(if (!d.done) Modifier.clickable { a.onFestival(f.id) } else Modifier).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.emoji, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Calendar.todayText(d).replace(" ${f.emoji}", ""), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text(f.about, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Surface(
                    onClick = { if (!d.done) a.onFestival(f.id) }, enabled = !d.done, shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (d.done) "✅ ${bi("townScroll.celebrated")}" else "🎉 ${bi("common.celebrate")}",
                            color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    } ?: v.countdown?.let { (f, days) ->
        k.item("countdown") { PaperRow(emoji = f.emoji, title = Calendar.countdown(f, days).replace(" ${f.emoji}", ""), sub = f.about, pal = pal, onClick = null, onShow = null) }
    }
    v.surprise?.let { sp ->
        k.item("surprise") {
            val pedlar = sp.kind == Surprises.PEDLAR
            PaperRow(
                emoji = Surprises.emoji(sp.kind), badge = if (!sp.done) "!" else null,
                title = Surprises.title(sp).removePrefix("${Surprises.emoji(sp.kind)} "),
                sub = if (sp.done) "✅ ${bi("townScroll.done")}" else v.surpriseText,
                extra = if (pedlar) "🛒 ${bi("townScroll.seeWhatHeSells")}" else if (!sp.done) "🎁 ${bi("townScroll.todaysSurprise")}" else null,
                pal = pal, onClick = if (!sp.done) a.onSurprise else null, onShow = { a.onShow(TownPlace.Spot("road")) },
            )
        }
    }
    if (v.projects.isNotEmpty()) {
        k.item("projectsTitle") { SubTitle("🏗️ ${bi("common.villageProjects")}", pal) }
        for (o in v.projects.take(3)) k.item("project:${o.spec.id}") {
            PaperRow(
                emoji = o.spec.emoji, badge = if (o.available) "!" else null,
                title = "${o.spec.name}  ${o.done}/${o.steps}",
                sub = listOfNotNull(o.next?.task, o.reason).joinToString("\n"),
                extra = if (o.available) Projects.costLine(o) else null,
                pal = pal, onClick = { a.onProjects(o.spec.id) }, onShow = null,
            )
        }
        k.item("projectsAll") {
            PaperRow(emoji = "🏗️", title = bi("townScroll.allProjects"), sub = null, pal = pal, onClick = { a.onProjects(null) }, onShow = null)
        }
    }
}

/** "+15 🌾 +5 📜" from a happening's reward (resource names as in the scene format). */
private fun rewardText(reward: Map<String, Int>): String? {
    val m = reward.mapNotNull { (k, v) -> Res.entries.firstOrNull { it.name.equals(k, ignoreCase = true) }?.let { it to v } }.toMap()
    return formatRes(m).takeIf { it.isNotEmpty() }
}

private fun places(k: Counted, c: Contents, pal: Paper) {
    val a = c.actions
    for (e in c.overview.places) k.item("place:${e.place.key()}") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PaperRow(
                emoji = e.place.emoji(),
                title = e.place.label() + if (e.count > 1) "  ×${e.count}" else "",
                sub = if (e.here.isEmpty()) e.place.where() else e.place.where() + " · " + e.here.joinToString(" ") { it.emoji },
                pal = pal, onClick = { a.onPlace(e.place) }, onShow = { a.onShow(e.place) },
            )
            for (sc in e.scenes) {
                Surface(
                    onClick = { a.onScene(sc, null) }, shape = RoundedCornerShape(50), color = pal.accent.copy(alpha = 0.14f),
                    modifier = Modifier.padding(start = 48.dp).heightIn(min = 44.dp),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🚪 ${bi("placeCard.go")}: ${sc.emoji} ${sc.title} ›", color = pal.ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    if (c.overview.locked.isNotEmpty()) {
        k.item("lockedTitle") { SubTitle("🔒 ${bi("townScroll.stillLocked")}", pal) }
        for (l in c.overview.locked) k.item("locked:${l.scene.id}") {
            PaperRow(emoji = l.scene.emoji, badge = "🔒", title = l.scene.title, sub = l.needs.joinToString(" · "), pal = pal, dim = true, onClick = null, onShow = null)
        }
    }
}

private fun people(k: Counted, c: Contents, pal: Paper) {
    val s = c.state
    val a = c.actions
    a.onVillagers?.let { open ->
        k.item("register") {
            PaperRow(
                emoji = "📖", title = bi("townScroll.everyoneVillage"),
                sub = bi("townScroll.friendshipsMemoriesTalks"),
                pal = pal, onClick = open, onShow = null,
            )
        }
    }
    a.onFriends?.let { open ->
        k.item("friends") {
            PaperRow(emoji = "🤝", title = bi("towns.friends"), sub = bi("towns.friendsAbout"), pal = pal, onClick = open, onShow = null)
        }
    }
    k.item("population") {
        Text(
            "👥 ${bi("townScroll.villagersMorale", "villagers" to s.villagers, "morale" to (moraleEmoji(s.morale)))}",
            fontFamily = Serif, color = pal.muted, style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (c.overview.residents.isEmpty()) k.item("noResidents") {
        Text(
            bi("townScroll.whenVillagersNeedSomething"),
            fontFamily = Serif, fontStyle = FontStyle.Italic, color = pal.muted, style = MaterialTheme.typography.bodyMedium,
        )
    }
    for (r in c.overview.residents) k.item("resident:${r.name}") {
        val first = r.doing.firstOrNull()
        PaperRow(
            emoji = r.emoji, badge = if (r.doing.any { it is Doing.Asks }) "!" else null,
            title = r.name,
            sub = r.doing.joinToString("\n") { d ->
                when (d) {
                    is Doing.Asks -> "📋 ${d.quest.title}"
                    is Doing.Busy -> "💬 ${d.on.happening.title} · ${d.place.where()}"
                    is Doing.Lives -> "🏠 ${d.scene.title}" // the scene's title already says where
                }
            },
            pal = pal,
            onClick = when (first) {
                is Doing.Asks -> ({ a.onQuest(first.quest) })
                is Doing.Busy -> ({ a.onScene(first.on.scene, first.on.key) })
                is Doing.Lives -> ({ a.onScene(first.scene, null) })
                null -> null
            },
            onShow = first?.let { d -> { a.onShow(d.place) } },
        )
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("d. M. HH:mm")

private fun chronicle(k: Counted, c: Contents, pal: Paper) {
    val log = c.overview.chronicle
    if (log.isEmpty()) k.item("noLog") {
        Text(bi("townScroll.storyJustBeginning"), fontFamily = Serif, fontStyle = FontStyle.Italic, color = pal.muted)
    }
    for ((i, e) in log.withIndex()) k.item("log:$i:${e.at}") { ChronicleLine(e, pal) }
}

@Composable
private fun ChronicleLine(e: LogEntry, pal: Paper) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(e.emoji, Modifier.width(32.dp), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f)) {
            Text(e.text, fontFamily = Serif, color = pal.ink, style = MaterialTheme.typography.bodyMedium)
            Text(dateFmt.format(Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault())), color = pal.muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun SubTitle(text: String, pal: Paper) {
    Text(
        text, fontFamily = Serif, fontWeight = FontWeight.Bold, color = pal.accent, style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 6.dp).semantics { heading() },
    )
}

/**
 * One line of the scroll: an emoji medallion (with a small badge), a title and details in ink, acting on a tap;
 * the 📍 button shows the place in the village.
 */
@Composable
internal fun PaperRow(
    emoji: String,
    title: String,
    sub: String?,
    pal: Paper,
    onClick: (() -> Unit)?,
    onShow: (() -> Unit)?,
    badge: String? = null,
    extra: String? = null,
    dim: Boolean = false,
) {
    val alpha = if (dim) 0.7f else 1f
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(pal.row)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 52.dp).padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Box(Modifier.size(40.dp).clip(CircleShape).background(pal.paper).border(1.5.dp, pal.rule, CircleShape), contentAlignment = Alignment.Center) {
                Text(emoji, style = MaterialTheme.typography.titleMedium)
            }
            if (badge != null) {
                Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-2).dp).size(18.dp).clip(CircleShape).background(if (badge == "!") Color(0xFFFFB300) else pal.paper).border(1.dp, pal.ink, CircleShape), contentAlignment = Alignment.Center) {
                    Text(badge, color = Color(0xFF2B2118), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).graphicsLayer { this.alpha = alpha }) {
            Text(title, color = pal.ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            sub?.takeIf { it.isNotBlank() }?.let { Text(it, color = pal.muted, style = MaterialTheme.typography.bodySmall) }
            extra?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color(0xFF2E7D4F).let { g -> if (pal === NightPaper) Color(0xFF7BD6A0) else g }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium) }
        }
        if (onShow != null) {
            Surface(onClick = onShow, shape = CircleShape, color = Color.Transparent, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { EmojiLabel("📍", bi("townScroll.showVillage")) }
            }
        } else if (onClick != null) {
            Text("›", color = pal.muted, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 10.dp))
        }
    }
}
