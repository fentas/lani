package si.lanisce.lani.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.AgeStep
import si.lanisce.lani.game.Projects
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.Teaser
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import java.time.LocalDate
import kotlin.math.min

/**
 * Three design variants of the village's HUD, for Jan to compare on a phone (QA's hook "hud:variant:<A|B|C>" in a debug
 * build; the current HUD stays the default). Each keeps one slim bar (back, the four resources, the settlement's ring
 * with its steps) and shows the rest its own way:
 *
 * - [A]: a bottom sheet from the ring, half the screen (dragged up: most of it), in sections: the settlement and its
 *   numbers as labelled tiles, the next goal with an action for each open step, and what's coming as a short timeline.
 * - [B]: a row of small cards under the bar (the goal, what's coming, the numbers), two lines each, swiped sideways; a
 *   card opens its section in the sheet.
 * - [C]: a narrow panel at the right edge (the settlement, the goal, what's coming), folding into a thin tab; an item
 *   opens its section beside it.
 */
enum class HudVariant {
    A, B, C;

    companion object {
        fun of(name: String?): HudVariant? = entries.firstOrNull { it.name == name }
    }
}

/** The sections the variants open. */
enum class HudSection { SETTLEMENT, GOAL, SOON }

/** The variants' texts: a "target · base" line in its two halves, the timeline's "when"; pure, for the tests. */
object HudText {
    private const val SEP = " · "

    /**
     * [text] ("Jutri: Most čez potok · Tomorrow: a bridge over the stream") split at its middle " · " into the target's
     * half and the base's: a label made by [bi] has as many in each half. One without (or with an even number): all of
     * it the target's, no base.
     */
    fun halves(text: String): Pair<String, String?> {
        val at = buildList {
            var i = text.indexOf(SEP)
            while (i >= 0) { add(i); i = text.indexOf(SEP, i + SEP.length) }
        }
        if (at.size % 2 == 0) return text to null
        val i = at[at.size / 2]
        return text.substring(0, i) to text.substring(i + SEP.length)
    }

    /** A teaser's half without its "when" ("Jutri: ", "Tonight by the fire: "): the timeline says when. */
    fun withoutWhen(half: String): String {
        val c = half.indexOf(": ")
        return if (c in 1..40) half.substring(c + 2).replaceFirstChar { it.uppercase() } else half
    }

    /** [key] in the target and in the base. */
    fun two(key: String, vararg args: Pair<String, Any?>): Pair<String, String> = inTarget(key, *args) to inBase(key, *args)

    private val WEEKDAYS = listOf(
        "townScroll.monday", "townScroll.tuesday", "townScroll.wednesday", "townScroll.thursday", "townScroll.friday",
        "townScroll.saturday", "townScroll.sunday",
    )

    /** When [t] comes, for the timeline: "Nocoj · Tonight", "Danes · Today", "Jutri · Tomorrow", "Torek · Tuesday", "Čez 9 dni · In 9 days". */
    fun whenOf(t: Teaser, today: LocalDate): Pair<String, String> {
        val d = t.day ?: 1
        val (a, b) = when {
            d <= 0 && t.evening -> two("hudVariants.tonight")
            d <= 0 -> two("common.today")
            d == 1 -> two("tomorrow.on", "day" to "0")
            d < 7 -> two(WEEKDAYS[today.plusDays(d.toLong()).dayOfWeek.value - 1])
            else -> two("hudVariants.inDays", "n" to d)
        }
        return a.replaceFirstChar { it.uppercase() } to b.replaceFirstChar { it.uppercase() }
    }

    /** The teasers by when they come (tonight's after today's), the most telling first within a day. */
    fun timeline(teasers: List<Teaser>): List<Teaser> = teasers.sortedBy { (it.day ?: 1) * 2 + if (it.evening) 1 else 0 }

    /** What a step's action says ("🪵 Nabiraj", "Gather"), or null when it has none (done, or a repair first). */
    fun actionOf(step: AgeStep): Pair<String, String>? = when (stepAction(step)) {
        // the emoji once, before the target's word (German's "{stepEmoji} Sammeln" too)
        StepAction.GATHER -> two("villageSheets.gather", "stepEmoji" to step.emoji).let { (a, b) -> a to b.removePrefix(step.emoji).trim() }
        StepAction.BUILD -> two("common.build").let { (a, b) -> "🔨 $a" to b }
        StepAction.LEARN -> two("common.learn").let { (a, b) -> "📚 $a" to b }
        StepAction.POLISH -> two("villageSheets.polish").let { (a, b) -> "🔩 $a" to b }
        StepAction.HELP -> two("villageSheets.help").let { (a, b) -> "💬 $a" to b }
        null -> null
    }

    /** The goal's steps still open, when the goal is the way to the next age (else none: its one action). */
    fun openSteps(view: HudView): List<AgeStep> = when (view.goal) {
        is NextGoal.Grow, is NextGoal.Learn, is NextGoal.Friends, is NextGoal.Polish -> view.check.steps.filter { !it.done }
        else -> emptyList()
    }
}

/**
 * The HUD as [variant] draws it, over the whole village screen (the map stays free to touch where nothing of it lies):
 * [open] says whether its sheet, its section or its detail is out; [onBar] gets the height the bar covers at the top
 * (the card row's included), [onInside] the rects a tap may land on without closing what's open.
 */
@Composable
internal fun VariantHud(
    variant: HudVariant, view: HudView, open: Boolean, actions: HudActions, moved: Boolean, onRecenter: () -> Unit, reduced: Boolean,
    onBar: (Int) -> Unit, onInside: (String, Rect?) -> Unit,
) {
    var section by remember { mutableStateOf(HudSection.SETTLEMENT) }
    val show: (HudSection) -> Unit = { s -> section = s; actions.expand(true) }
    // what leads on closes what's open first, as the current panel does
    val leads = HudActions(
        back = { actions.expand(false); actions.back() }, resource = { r -> actions.expand(false); actions.resource(r) },
        age = { actions.expand(false); actions.age() }, info = { actions.expand(false); actions.info() },
        fire = { actions.expand(false); actions.fire() }, chest = { actions.expand(false); actions.chest() },
        projects = { actions.expand(false); actions.projects() }, progress = { actions.expand(false); actions.progress() },
        goal = { actions.expand(false); actions.goal() }, festival = { id -> actions.expand(false); actions.festival(id) },
        news = { actions.expand(false); actions.news() }, expand = actions.expand, minimize = actions.minimize,
        step = { st -> actions.expand(false); actions.step(st) },
    )
    Box(Modifier.fillMaxSize()) {
        when (variant) {
            HudVariant.A -> {
                Column(Modifier.fillMaxWidth().onSizeChanged { onBar(it.height) }.onGloballyPositioned { onInside("bar", it.boundsInWindow()) }) {
                    SlimBar(view, leads, ringDot = view.badges.isNotEmpty() && !open, reduced = reduced, onRing = { if (open) actions.expand(false) else show(HudSection.SETTLEMENT) })
                }
                Recenter(moved, start = false, onRecenter)
                HudSheet(open, reduced, onClose = { actions.expand(false) }, onBounds = { onInside("sheet", it) }, wrap = false) {
                    SettlementSection(view, leads)
                    GoalSection(view, leads)
                    SoonSection(view, leads)
                }
            }
            HudVariant.B -> {
                Column(Modifier.fillMaxWidth().onSizeChanged { onBar(it.height) }.onGloballyPositioned { onInside("bar", it.boundsInWindow()) }) {
                    SlimBar(view, leads, ringDot = false, reduced = reduced, onRing = { show(HudSection.SETTLEMENT) })
                    CardRow(view, leads, onCard = show)
                }
                Recenter(moved, start = false, onRecenter, below = 64.dp)
                HudSheet(open, reduced, onClose = { actions.expand(false) }, onBounds = { onInside("sheet", it) }, wrap = true) {
                    when (section) {
                        HudSection.SETTLEMENT -> SettlementSection(view, leads)
                        HudSection.GOAL -> GoalSection(view, leads)
                        HudSection.SOON -> SoonSection(view, leads)
                    }
                }
            }
            HudVariant.C -> {
                var folded by remember { mutableStateOf(false) }
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth().onSizeChanged { onBar(it.height) }.onGloballyPositioned { onInside("bar", it.boundsInWindow()) }) {
                        SlimBar(view, leads, ringDot = false, reduced = reduced, onRing = { folded = false; show(HudSection.SETTLEMENT) })
                    }
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp).onGloballyPositioned { onInside("side", it.boundsInWindow()) }) {
                            if (folded) SideTab(view, onUnfold = { folded = false })
                            else SidePanel(view, if (open) section else null, onItem = { s -> if (open && section == s) actions.expand(false) else show(s) }, onFold = { actions.expand(false); folded = true })
                        }
                        if (moved) Box(Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 6.dp)) { RecenterButton(onRecenter) }
                        SideDetail(open, reduced, panelWidth = if (folded) 24.dp else SIDE_WIDTH, onBounds = { onInside("sheet", it) }) {
                            when (section) {
                                HudSection.SETTLEMENT -> SettlementSection(view, leads)
                                HudSection.GOAL -> GoalSection(view, leads)
                                HudSection.SOON -> SoonSection(view, leads)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Recenter(moved: Boolean, start: Boolean, onRecenter: () -> Unit, below: Dp = 0.dp) {
    // under the bar (a fixed room: the bar's 70 dp under the status bar, and B's cards)
    if (!moved) return
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 72.dp + below, start = 10.dp, end = 10.dp), contentAlignment = if (start) Alignment.TopStart else Alignment.TopEnd) {
        RecenterButton(onRecenter)
    }
}

// --- the bar ----------------------------------------------------------------------------------

/** The slim bar of every variant: back, the four resources (their bars to the cap), the settlement's ring. */
@Composable
private fun SlimBar(view: HudView, actions: HudActions, ringDot: Boolean, reduced: Boolean, onRing: () -> Unit) {
    val s = view.state
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HudButton("←", Labels.BACK, actions.back)
        for (r in Res.entries) ResourceChip(r, s.res(r), view.attrs.caps[r], onClick = { actions.resource(r) }, modifier = Modifier.weight(1f))
        SettlementRing(view, ringDot, onRing, onLongClick = { actions.minimize(true) })
    }
}

/**
 * The settlement's ring: the next age's emoji in a ring that fills with its steps, "7/10" under it (the one place it
 * shows), gold when the village can advance or a feast is on; [dot]: something inside wants a look. A tap opens the
 * settlement, a long press minimizes the HUD to its pill.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettlementRing(view: HudView, dot: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val status = view.status
    val haptic = LocalHapticFeedback.current
    val gold = status.tone == HudStatus.Tone.READY || status.tone == HudStatus.Tone.FESTIVAL
    val progress = view.check.progress
    val said = VillageHudLogic.description(status, view.badges)
    Box(Modifier.size(width = 54.dp, height = 58.dp)) {
        Box(
            Modifier.align(Alignment.TopCenter).size(48.dp).clip(CircleShape).background(if (gold) XpGold.copy(alpha = 0.92f) else HudPlate)
                .combinedClickable(onClick = onClick, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() })
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = said
                    onClick(bi("villageHud.more")) { onClick(); true }
                    onLongClick(bi("villageHud.hide")) { onLongClick(); true }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                val w = 3.5.dp.toPx()
                drawArc(Color.White.copy(alpha = 0.18f), -90f, 360f, false, style = Stroke(w))
                if (!gold) drawArc(XpGold, -90f, 360f * progress.coerceIn(0.03f, 1f), false, style = Stroke(w, cap = StrokeCap.Round))
            }
            Text(status.emoji, fontSize = 19.sp, lineHeight = 20.sp, modifier = Modifier.offset(y = (-2).dp))
        }
        Surface(color = Color(0xFF2B2118), shape = RoundedCornerShape(50), modifier = Modifier.align(Alignment.BottomCenter).clearAndSetSemantics { }) {
            Text(
                status.text, color = if (gold) XpGold else Color.White, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        if (dot) NewDot(Modifier.align(Alignment.TopEnd).offset(x = (-1).dp, y = 1.dp))
    }
}

// --- the sections -----------------------------------------------------------------------------

/** A section's small header: "NASELJE · Settlement", and what it may hold at its end. */
@Composable
private fun SectionHeader(key: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(inTarget(key).uppercase(), color = XpGold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
        Text(" · ${inBase(key)}", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Two lines, the target's over the base's, each cut with "…". */
@Composable
private fun TwoLines(target: String, base: String?, modifier: Modifier = Modifier, strong: Boolean = true, lines: Int = 1) {
    Column(modifier) {
        Text(
            target, color = Color.White, style = if (strong) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal, maxLines = lines, overflow = TextOverflow.Ellipsis,
        )
        if (!base.isNullOrBlank()) Text(base, color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall, maxLines = lines, overflow = TextOverflow.Ellipsis)
    }
}

/** The settlement: its age and the way to the next (a tap: the ages), and its numbers, each with its name under it. */
@Composable
private fun SettlementSection(view: HudView, actions: HudActions) {
    val s = view.state
    val check = view.check
    val next = check.next
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("hudVariants.settlement") {
            Surface(onClick = actions.progress, color = PanelChip, shape = RoundedCornerShape(50), modifier = Modifier.hudSemantics(Labels.PROGRESS, actions.progress)) {
                Text("📊 ${inTarget("common.progress")} ›", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
        Surface(
            onClick = actions.age, color = PanelChip, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().hudSemantics(VillageHudLogic.ageDescription(s, check), actions.age),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.age.emoji, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(10.dp))
                    TwoLines(
                        if (next != null) "${s.age.sl} → ${next.emoji} ${next.sl}" else s.age.sl,
                        if (next != null) "${s.age.en} → ${next.en}" else s.age.en,
                        Modifier.weight(1f),
                    )
                    Text("›", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium)
                }
                if (next != null) {
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(check.progress.coerceIn(0.03f, 1f)).clip(CircleShape).background(XpGold))
                    }
                }
            }
        }
        val open = if (s.age >= Age.ZASELEK) Projects.open(s) else emptyList()
        val done = open.count { Projects.finished(s, it) }
        val goods = s.chest.goods.values.sum() + s.chest.tools.size
        val tiles = listOfNotNull(
            Tile("👥", "${s.villagers}/${view.attrs.populationCap}", HudText.two("hudVariants.people"), "${bi("villageScreen.villagers")}: ${s.villagers} / ${view.attrs.populationCap}", onClick = actions.info),
            Tile(moraleEmoji(s.morale), "${s.morale}", HudText.two("hudVariants.mood"), "${bi("villageScreen.morale")}: ${s.morale}", onClick = actions.info),
            Tile("🔥", "${s.fire}", HudText.two("villageScreen.fire"), "${bi("villageScreen.fire")}: ${s.fire}", warn = s.fire < 25, onClick = actions.fire),
            Tile("🤝", "${s.help}", HudText.two("common.help"), "${bi("common.help")}: ${s.help}", onClick = actions.info),
            Tile("🧰", "$goods", HudText.two("villageScreen.chest"), bi("villageScreen.chest") + if (view.chestNew) ", ${bi("villageScreen.somethingNew")}" else "", dot = view.chestNew, onClick = actions.chest),
            if (s.age >= Age.ZASELEK) Tile(
                "🏗️", "$done/${open.size}", HudText.two("hudVariants.projects"),
                "${bi("villageScreen.villageProjects")}: $done / ${open.size}" + if (view.stepToday) ", ${bi("villageHud.stepToday")}" else "",
                dot = view.stepToday, onClick = actions.projects,
            ) else null,
        )
        for (row in tiles.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in row) StatTile(t, Modifier.weight(1f))
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private class Tile(
    val emoji: String, val value: String, val label: Pair<String, String>, val description: String,
    val warn: Boolean = false, val dot: Boolean = false, val onClick: () -> Unit,
)

/** A number with its name under it, in the target and the base: "88", "Razpoloženje", "Mood". */
@Composable
private fun StatTile(t: Tile, modifier: Modifier) {
    Box(modifier) {
        Surface(
            onClick = t.onClick, color = if (t.warn) TriglavRed.copy(alpha = 0.8f) else PanelChip, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).hudSemantics(t.description, t.onClick),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.emoji, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(5.dp))
                    Text(t.value, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Text(t.label.first, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.label.second, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (t.dot) NewDot(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
    }
}

/** The next goal: its line (a tap: the scroll's goal), then each open step with its own action, or the goal's one action. */
@Composable
private fun GoalSection(view: HudView, actions: HudActions) {
    val steps = HudText.openSteps(view)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("hudVariants.nextGoal")
        val (t, b) = HudText.halves(view.goal.short())
        Surface(onClick = actions.goal, color = Color.Transparent, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🎯", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
                TwoLines(t, b, Modifier.weight(1f))
                Text("›", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium)
            }
        }
        if (steps.isNotEmpty()) for (st in steps) StepLine(st) { actions.step(st) }
        else view.goalAction?.let { a ->
            val (at, ab) = HudText.halves(a.label)
            ActionPill(at, ab, a.color, wide = true) { actions.expand(false); a.run() }
        }
    }
}

/** An open step of the next age: what, how much of how much, a thin bar, and its action ("🪵 Nabiraj · Gather"). */
@Composable
private fun StepLine(step: AgeStep, onAction: () -> Unit) {
    val (t, b) = HudText.halves(step.label)
    Surface(color = PanelChip, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(step.emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(30.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (step.kind != AgeStep.Kind.RUSTY) Text("${min(step.have, step.need)}/${step.need}", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                }
                if (b != null) Text(b, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(step.share.coerceIn(0.03f, 1f)).clip(CircleShape).background(XpGold))
                }
            }
            HudText.actionOf(step)?.let { (at, ab) ->
                Spacer(Modifier.width(10.dp))
                ActionPill(at, ab, AlpineGreen, onClick = onAction)
            }
        }
    }
}

/** A small action: the target's word over the base's. */
@Composable
private fun ActionPill(target: String, base: String?, color: Color, wide: Boolean = false, onClick: () -> Unit) {
    val ink = if (color == XpGold) Color(0xFF2B2118) else Color.White
    Surface(onClick = onClick, color = color, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 44.dp).then(if (wide) Modifier.fillMaxWidth() else Modifier.widthIn(min = 84.dp, max = 116.dp))) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(target, color = ink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (!base.isNullOrBlank()) Text(base, color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

/** What's coming as a short timeline: when, an emoji, a line in each language; a tap shows all of it. */
@Composable
private fun SoonSection(view: HudView, actions: HudActions) {
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("hudVariants.comingUp") {
            if (VillageHudLogic.unseen(view.teasers.map { it.line }, view.newsSeen)) NewDot()
        }
        view.festival?.let { f ->
            val (t, b) = HudText.halves(si.lanisce.lani.game.Calendar.todayText(f).replace(" ${f.festival.emoji}", ""))
            SoonRow(HudText.two("common.today"), f.festival.emoji, HudText.withoutWhen(t), b?.let(HudText::withoutWhen), gold = !f.festival.quiet) { actions.festival(f.festival.id) }
        }
        for (t in HudText.timeline(view.teasers)) {
            val (a, b) = HudText.halves(t.text)
            SoonRow(HudText.whenOf(t, today), t.emoji, HudText.withoutWhen(a), b?.let(HudText::withoutWhen))
        }
    }
}

@Composable
private fun SoonRow(at: Pair<String, String>, emoji: String, target: String, base: String?, gold: Boolean = false, onOpen: (() -> Unit)? = null) {
    var full by remember(target) { mutableStateOf(false) }
    Surface(
        onClick = { if (onOpen != null && full) onOpen() else full = !full },
        color = if (gold) XpGold.copy(alpha = 0.25f) else PanelChip, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = if (full) Alignment.Top else Alignment.CenterVertically) {
            Column(Modifier.width(76.dp)) {
                Text(at.first, color = XpGold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(at.second, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(30.dp))
            TwoLines(target, base, Modifier.weight(1f), strong = false, lines = if (full) 8 else 1)
        }
    }
}

// --- A: the bottom sheet ------------------------------------------------------------------------

/**
 * A sheet from the screen's bottom, not modal (the map above it stays to touch: a tap there closes it, a drag pans):
 * half the screen ([wrap]: its content's height, up to half), dragged by its handle up to most of it or down to close.
 */
@Composable
private fun HudSheet(open: Boolean, reduced: Boolean, onClose: () -> Unit, onBounds: (Rect?) -> Unit, wrap: Boolean, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxHeight
        var tall by remember { mutableStateOf(false) }
        LaunchedEffect(open) { if (!open) { tall = false; onBounds(null) } }
        val density = LocalDensity.current
        val drag = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val base by animateDpAsState(if (tall) screen * 0.86f else screen * 0.5f, if (reduced) snap() else tween(220), label = "sheet")
        AnimatedVisibility(
            open, Modifier.align(Alignment.BottomCenter),
            enter = if (reduced) EnterTransition.None else slideInVertically { it } + fadeIn(),
            exit = if (reduced) ExitTransition.None else slideOutVertically { it } + fadeOut(),
        ) {
            val h = (base - with(density) { drag.value.toDp() }).coerceIn(160.dp, screen * 0.92f)
            Surface(
                color = SheetPlate, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier.fillMaxWidth().then(if (wrap && !tall) Modifier.heightIn(max = h) else Modifier.height(h))
                    .onGloballyPositioned { onBounds(it.boundsInWindow()) },
            ) {
                Column {
                    // the handle: up for more, down to close; its ends: hide the bar, close
                    Row(
                        Modifier.fillMaxWidth().height(40.dp).draggable(
                            rememberDraggableState { d -> scope.launch { drag.snapTo(drag.value + d) } }, Orientation.Vertical,
                            onDragStopped = { v ->
                                val d = drag.value
                                val far = with(density) { 90.dp.toPx() }
                                when {
                                    d > far || v > 2500f -> if (tall && d < far * 3) tall = false else onClose()
                                    d < -far || v < -2500f -> tall = true
                                }
                                drag.animateTo(0f)
                            },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.width(48.dp))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(width = 40.dp, height = 4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
                        }
                        Surface(onClick = onClose, color = Color.Transparent, shape = CircleShape, modifier = Modifier.size(40.dp).padding(end = 4.dp).hudSemantics(Labels.CLOSE, onClose)) {
                            Box(contentAlignment = Alignment.Center) { Text("✕", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.titleSmall) }
                        }
                    }
                    Column(
                        Modifier.weight(1f, fill = !(wrap && !tall)).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).navigationBarsPadding().padding(bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) { content() }
                }
            }
        }
    }
}

// --- B: the card row ----------------------------------------------------------------------------

/** B: the goal, what's coming and the numbers as small cards in a row under the bar, swiped sideways; a tap opens one. */
@Composable
private fun CardRow(view: HudView, actions: HudActions, onCard: (HudSection) -> Unit) {
    val list = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth * 0.8f
        LazyRow(
            state = list, flingBehavior = rememberSnapFlingBehavior(list), contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        ) {
            item { GoalCard(view, actions, Modifier.width(w)) { onCard(HudSection.GOAL) } }
            item { SoonCard(view, Modifier.width(w)) { onCard(HudSection.SOON) } }
            item { StatsCard(view, Modifier.width(w)) { onCard(HudSection.SETTLEMENT) } }
        }
    }
}

private val CardPlate = Color(0xE0141B2A)

/** The sheet's and the detail's plate: opaque, so neither the map nor the bottom bar shows through. */
private val SheetPlate = Color(0xFF111824)

@Composable
private fun SmallCard(modifier: Modifier, dot: Boolean = false, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Box(modifier) {
        Surface(onClick = onClick, color = CardPlate, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(58.dp)) {
            Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) { content() }
        }
        if (dot) NewDot(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
    }
}

@Composable
private fun GoalCard(view: HudView, actions: HudActions, modifier: Modifier, onClick: () -> Unit) {
    val (t, b) = HudText.halves(view.goal.short())
    val first = HudText.openSteps(view).firstOrNull { HudText.actionOf(it) != null }
    SmallCard(modifier, onClick = onClick) {
        Text("🎯", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        TwoLines(t, b, Modifier.weight(1f))
        if (first != null) HudText.actionOf(first)?.let { (at, _) ->
            Spacer(Modifier.width(6.dp))
            Surface(onClick = { actions.step(first) }, color = AlpineGreen, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 40.dp)) {
                Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(at, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SoonCard(view: HudView, modifier: Modifier, onClick: () -> Unit) {
    val first = HudText.timeline(view.teasers).firstOrNull()
    val more = view.teasers.size - 1
    SmallCard(modifier, dot = VillageHudLogic.unseen(view.teasers.map { it.line }, view.newsSeen), onClick = onClick) {
        if (first == null) {
            TwoLines(inTarget("hudVariants.comingUp"), inBase("hudVariants.comingUp"), Modifier.weight(1f))
            return@SmallCard
        }
        val at = HudText.whenOf(first, LocalDate.now())
        val (a, b) = HudText.halves(first.text)
        Column(Modifier.width(62.dp)) {
            Text(at.first, color = XpGold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(at.second, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(first.emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp))
        TwoLines(HudText.withoutWhen(a), b?.let(HudText::withoutWhen), Modifier.weight(1f), strong = false)
        if (more > 0) {
            Spacer(Modifier.width(6.dp))
            Surface(color = PanelChip, shape = RoundedCornerShape(50)) {
                Text("+$more", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun StatsCard(view: HudView, modifier: Modifier, onClick: () -> Unit) {
    val s = view.state
    SmallCard(modifier, dot = view.chestNew || view.stepToday, onClick = onClick) {
        for ((value, key) in listOf(
            "👥 ${s.villagers}/${view.attrs.populationCap}" to "hudVariants.people",
            "${moraleEmoji(s.morale)} ${s.morale}" to "hudVariants.mood",
            "🔥 ${s.fire}" to "villageScreen.fire",
            "🤝 ${s.help}" to "common.help",
        )) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, color = if (key == "villageScreen.fire" && s.fire < 25) TriglavRed else Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(inTarget(key), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("›", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium)
    }
}

// --- C: the side panel --------------------------------------------------------------------------

private val SIDE_WIDTH = 78.dp

/** C: the settlement, the goal and what's coming down the right edge, each an emoji with its name; folds into a tab. */
@Composable
private fun SidePanel(view: HudView, selected: HudSection?, onItem: (HudSection) -> Unit, onFold: () -> Unit) {
    val steps = HudText.openSteps(view).size
    Surface(color = CardPlate, shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp), modifier = Modifier.width(SIDE_WIDTH)) {
        Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SideItem("📊", HudText.two("hudVariants.settlement"), selected == HudSection.SETTLEMENT, dot = view.chestNew || view.stepToday) { onItem(HudSection.SETTLEMENT) }
            SideItem("🎯", HudText.two("hudVariants.goal"), selected == HudSection.GOAL, count = steps.takeIf { it > 0 }) { onItem(HudSection.GOAL) }
            SideItem(
                "🗓️", HudText.two("hudVariants.comingUp"), selected == HudSection.SOON, count = view.teasers.size.takeIf { it > 0 },
                dot = VillageHudLogic.unseen(view.teasers.map { it.line }, view.newsSeen),
            ) { onItem(HudSection.SOON) }
            Surface(onClick = onFold, color = Color.Transparent, shape = CircleShape, modifier = Modifier.size(40.dp).hudSemantics(bi("hudVariants.fold"), onFold)) {
                Box(contentAlignment = Alignment.Center) { Text("›", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
}

@Composable
private fun SideItem(emoji: String, label: Pair<String, String>, selected: Boolean, count: Int? = null, dot: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick, color = if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp).fillMaxWidth().hudSemantics("${label.first} · ${label.second}" + (count?.let { ": $it" } ?: ""), onClick),
    ) {
        Column(Modifier.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Text(emoji, style = MaterialTheme.typography.titleLarge)
                if (count != null) Surface(color = TriglavRed.takeIf { dot } ?: XpGold, shape = CircleShape, modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-4).dp)) {
                    Text("$count", color = Color(0xFF2B2118).takeIf { !dot } ?: Color.White, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp))
                } else if (dot) NewDot(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-2).dp))
            }
            Text(label.first, color = Color.White, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(label.second, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

/** C folded: a thin tab at the edge ("‹", a dot when something wants a look). */
@Composable
private fun SideTab(view: HudView, onUnfold: () -> Unit) {
    Box {
        Surface(
            onClick = onUnfold, color = CardPlate, shape = RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp),
            modifier = Modifier.size(width = 24.dp, height = 72.dp).hudSemantics(bi("hudVariants.unfold"), onUnfold),
        ) {
            Box(contentAlignment = Alignment.Center) { Text("‹", color = Color.White, style = MaterialTheme.typography.titleLarge) }
        }
        if (view.badges.isNotEmpty()) NewDot(Modifier.align(Alignment.TopStart).offset(x = (-3).dp, y = (-3).dp))
    }
}

/** C's detail: a card beside the panel, under the bar, as tall as it needs (up to most of the screen's half). */
@Composable
private fun SideDetail(open: Boolean, reduced: Boolean, panelWidth: Dp, onBounds: (Rect?) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    LaunchedEffect(open) { if (!open) onBounds(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            open, Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = panelWidth + 6.dp, start = 8.dp),
            enter = if (reduced) EnterTransition.None else slideInHorizontally { it / 2 } + fadeIn(),
            exit = if (reduced) ExitTransition.None else slideOutHorizontally { it / 2 } + fadeOut(),
        ) {
            Surface(
                color = SheetPlate, shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.62f).onGloballyPositioned { onBounds(it.boundsInWindow()) },
            ) {
                // no ✕: its item again, a tap beside it or back closes it
                Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
            }
        }
    }
}
