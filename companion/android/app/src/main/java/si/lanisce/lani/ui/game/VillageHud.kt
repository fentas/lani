package si.lanisce.lani.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.sp
import si.lanisce.lani.data.HudPrefs
import si.lanisce.lani.game.AdvanceCheck
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Attributes
import si.lanisce.lani.game.Calendar
import si.lanisce.lani.game.FestivalDay
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.Teaser
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import kotlin.math.roundToInt

/**
 * How the village's HUD shows (companion/GAME.md, "The village's HUD"): one slim row ([COLLAPSED]), the row with its panel
 * slid down under it ([EXPANDED]), or a small pill in the corner ([MINIMIZED]), for looking at the village.
 */
enum class HudMode { COLLAPSED, EXPANDED, MINIMIZED }

/** The status chip of the HUD's row: the age and the way to the next one, or the festival that is on (see [VillageHudLogic.status]). */
data class HudStatus(val emoji: String, val text: String, val description: String, val tone: Tone) {
    enum class Tone {
        /** The age, on its way to the next one. */
        PLAIN,
        /** Ready to advance: it glows gold, as the age's chip always did. */
        READY,
        /** A feast today: gold. */
        FESTIVAL,
        /** A quiet day (Dan spomina na mrtve): plain, as its pill always was. */
        QUIET,
    }
}

/** Something on the HUD's row that wants a look: a small badge on its status chip, said with it ([description]). */
data class HudBadge(val kind: Kind, val emoji: String, val description: String) {
    enum class Kind { FIRE, CHEST, PROJECT, NEWS }
}

/** What the HUD shows and when; pure, so the tests see it without a screen. */
object VillageHudLogic {
    /**
     * How the HUD shows: [minimized] is the learner's choice on this phone, [expanded] the panel opened from the row; while
     * the plot chooser is on ([choosing]) the HUD minimizes by itself (the ★ and the free plots lie in the map's top half
     * too), unless the learner tapped its pill meanwhile ([peek]): then the row, for now.
     */
    fun mode(minimized: Boolean, expanded: Boolean, choosing: Boolean, peek: Boolean = false): HudMode = when {
        if (choosing) !peek else minimized -> HudMode.MINIMIZED
        expanded -> HudMode.EXPANDED
        else -> HudMode.COLLAPSED
    }

    /** How many of the next age's steps are done, and how many it has ("4/8"); null at the last age. */
    fun steps(check: AdvanceCheck): Pair<Int, Int>? =
        if (check.next == null) null else check.steps.count { it.done } to check.steps.size

    /**
     * The status chip: a festival that is on and not kept yet shows itself ("🍷 Danes", gold; a quiet day's "🕯️ Danes",
     * plain), else the next age with its steps done ("🏘️ 4/8": the way to Vas), "✨" when the village can advance (the age
     * itself is in the panel and on the map; four resources leave no room for its name on a phone).
     * [description] is what TalkBack reads: the age's chip's words, and the steps still to go.
     */
    fun status(s: GameState, check: AdvanceCheck, festival: FestivalDay?): HudStatus {
        if (festival != null && !festival.done) {
            val f = festival.festival
            return HudStatus(
                f.emoji, inTarget("common.today"), Calendar.todayText(festival),
                if (f.quiet) HudStatus.Tone.QUIET else HudStatus.Tone.FESTIVAL,
            )
        }
        val next = check.next ?: return HudStatus(s.age.emoji, s.age.sl, "${bi("villageScreen.age")}: ${s.age.label()}", HudStatus.Tone.PLAIN)
        val (done, all) = steps(check) ?: (0 to 0)
        val count = if (all > 0) "$done/$all" else "${(check.progress * 100).roundToInt()} %"
        return if (check.ok) HudStatus(next.emoji, "✨", ageDescription(s, check), HudStatus.Tone.READY)
        else HudStatus(next.emoji, count, ageDescription(s, check) + if (all > 0) ", ${stepsLeft(all - done)}" else "", HudStatus.Tone.PLAIN)
    }

    /** "Doba · Age: Zaselek · Hamlet. Do Vas · To the village: 83 %" (the age's chip, the status chip). */
    fun ageDescription(s: GameState, check: AdvanceCheck): String {
        val next = check.next
        val percent = (check.progress * 100).roundToInt()
        return "${bi("villageScreen.age")}: ${s.age.label()}" + when {
            next == null -> ""
            check.ok -> ". ${bi("villageScreen.readyAdvance", "nextSl" to next.names)}"
            else -> ". ${bi("villageScreen.towardsAge", "nextSl" to next.names, "lowercase" to next.names.map { it.lowercase() }, "percent" to percent)}"
        }
    }

    /**
     * The badges on the row, what wants a look: the fire dying (red, as its chip pulsed), something new in the chest (its
     * red dot), a project step that can be taken today, and news not yet seen in the panel ([news]: today's lines, [seen]:
     * the ones the panel showed).
     */
    fun badges(s: GameState, chestNew: Boolean, stepToday: Boolean, news: List<String>, seen: Set<String>): List<HudBadge> = buildList {
        if (s.fire < 25) add(HudBadge(HudBadge.Kind.FIRE, "🔥", "${bi("villageScreen.fire")}: ${s.fire}, ${bi("villageScreen.dying")}"))
        if (chestNew) add(HudBadge(HudBadge.Kind.CHEST, "🧰", "${bi("villageScreen.chest")}: ${bi("villageScreen.somethingNew")}"))
        if (stepToday) add(HudBadge(HudBadge.Kind.PROJECT, "🏗️", bi("villageHud.stepToday")))
        if (unseen(news, seen)) add(HudBadge(HudBadge.Kind.NEWS, "📣", bi("villageHud.news")))
    }

    /** Whether a line of [news] wasn't shown in the panel yet ([seen]). */
    fun unseen(news: List<String>, seen: Set<String>): Boolean = news.any { it !in seen }

    /** What the row's status chip and the pill say to TalkBack: the status, then each badge. */
    fun description(status: HudStatus, badges: List<HudBadge>): String =
        (listOf(status.description) + badges.map { it.description }).joinToString("; ")
}

/**
 * The HUD's state on the village screen: the learner's choice on this phone ([prefs]: minimized or not), its panel ([open]),
 * and the pill tapped while a plot chooser is on (the row, for that chooser only).
 */
@Stable
class HudState(private val prefs: HudPrefs) {
    /** The panel is open. */
    var open by mutableStateOf(false)
    /** The chooser whose pill was tapped: the row while it lasts. */
    private var peekAt by mutableStateOf<Int?>(null)

    /** How the HUD shows now; [chooser]: the plot chooser that is on (a number for each time it opens), or null. */
    fun mode(chooser: Int?): HudMode = VillageHudLogic.mode(prefs.minimized, open, chooser != null, chooser != null && peekAt == chooser)

    /** The pill (true) or the row back: the learner's choice, kept on the phone; while [chooser] is on, for it only. */
    fun minimize(on: Boolean, chooser: Int?) {
        open = false
        if (chooser != null) peekAt = if (on) null else chooser else prefs.minimize(on)
    }
}

/** What the HUD shows of the village now (see [VillageHud]). */
data class HudView(
    val state: GameState,
    val attrs: Attributes,
    val check: AdvanceCheck,
    val goal: NextGoal,
    /** The goal's one-tap action (its card's first button), or null. */
    val goalAction: GoalAction?,
    val chestNew: Boolean,
    /** A project step can be taken today. */
    val stepToday: Boolean,
    /** The festival that is on and not kept yet. */
    val festival: FestivalDay?,
    /** What's coming ("Jutri · Tomorrow"), the panel's news. */
    val teasers: List<Teaser>,
    /** The news lines the panel showed last ([si.lanisce.lani.data.HudPrefs.newsSeen]). */
    val newsSeen: Set<String>,
) {
    val status: HudStatus get() = VillageHudLogic.status(state, check, festival)
    val badges: List<HudBadge> get() = VillageHudLogic.badges(state, chestNew, stepToday, teasers.map { it.line }, newsSeen)
}

/** What the HUD's chips and buttons do. */
class HudActions(
    val back: () -> Unit,
    val resource: (Res) -> Unit,
    val age: () -> Unit,
    val info: () -> Unit,
    val fire: () -> Unit,
    val chest: () -> Unit,
    val projects: () -> Unit,
    val progress: () -> Unit,
    /** The goal's line: the scroll at "Naslednji cilj". */
    val goal: () -> Unit,
    val festival: (String) -> Unit,
    /** A news line: the scroll's today list. */
    val news: () -> Unit,
    /** The panel opened (true) or closed. */
    val expand: (Boolean) -> Unit,
    /** The HUD minimized to its pill (true), or the row back. */
    val minimize: (Boolean) -> Unit,
)

/**
 * The village's HUD over the top of the map (companion/GAME.md, "The village's HUD"), as [mode] says: the slim row (back,
 * the four resources, the status chip with its badges and the chevron), the panel under it with everything else, or the
 * pill in the corner. [onRow] and [onPill] get the height each covers (the status bar's included) for the camera and the
 * bubbles; the recenter button sits under the row (beside the pill) while the camera is [moved]. [reduced]: the system's
 * "remove animations": nothing slides or pulses.
 */
@Composable
fun VillageHud(
    view: HudView, mode: HudMode, actions: HudActions, moved: Boolean, onRecenter: () -> Unit, reduced: Boolean,
    modifier: Modifier = Modifier, onRow: (Int) -> Unit = {}, onPill: (Int) -> Unit = {},
) {
    val status = view.status
    val badges = view.badges
    Box(modifier.fillMaxWidth()) {
        if (mode == HudMode.MINIMIZED) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp).onSizeChanged { onPill(it.height) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HudPill(status, badges, onClick = { actions.minimize(false) })
                Spacer(Modifier.weight(1f))
                if (moved) RecenterButton(onRecenter)
            }
            return@Box
        }
        val expanded = mode == HudMode.EXPANDED
        Column(Modifier.fillMaxWidth()) {
            HudRow(view, status, badges, expanded, actions, reduced, Modifier.onSizeChanged { onRow(it.height) })
            AnimatedVisibility(
                expanded,
                enter = if (reduced) EnterTransition.None else expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = if (reduced) ExitTransition.None else shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                HudPanel(view, actions, reduced)
            }
            if (!expanded && moved) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                    Spacer(Modifier.weight(1f))
                    RecenterButton(onRecenter)
                }
            }
        }
    }
}

/** The slim row: back, the four resources (each opens its sheet, with Trade), the status chip and its chevron. */
@Composable
private fun HudRow(view: HudView, status: HudStatus, badges: List<HudBadge>, expanded: Boolean, actions: HudActions, reduced: Boolean, modifier: Modifier) {
    val s = view.state
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // each closes the panel as it opens what it leads to, as the panel's own do
        fun then(f: () -> Unit): () -> Unit = { if (expanded) actions.expand(false); f() }
        HudButton("←", Labels.BACK, then(actions.back))
        for (r in Res.entries) ResourceChip(r, s.res(r), view.attrs.caps[r], onClick = then { actions.resource(r) }, modifier = Modifier.weight(1f))
        StatusChip(status, badges, expanded, reduced, onToggle = { actions.expand(!expanded) }, onMinimize = { actions.minimize(true) })
    }
}

/**
 * The status chip: the status ([HudStatus]) and the chevron in one pill; a tap on either opens the panel (closes it, open),
 * a long press minimizes the HUD. The badges sit on its top edge; TalkBack reads them with the status, and finds "Skrij
 * vrstico · Hide the bar" among the actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatusChip(status: HudStatus, badges: List<HudBadge>, expanded: Boolean, reduced: Boolean, onToggle: () -> Unit, onMinimize: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val minimize = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onMinimize() }
    val glow = status.tone == HudStatus.Tone.READY && !reduced
    val pulse = if (glow) rememberInfiniteTransition(label = "ready").animateFloat(0.55f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a").value else 1f
    val gold = status.tone == HudStatus.Tone.READY || status.tone == HudStatus.Tone.FESTIVAL
    val ink = if (gold) Color(0xFF2B2118) else Color.White
    val hide = CustomAccessibilityAction(bi("villageHud.hide")) { minimize(); true }
    val toggleLabel = bi(if (expanded) "villageHud.less" else "villageHud.more")
    Box {
        Surface(color = if (gold) XpGold.copy(alpha = 0.92f * pulse) else HudPlate, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.heightIn(min = 48.dp).widthIn(max = 120.dp)
                        .combinedClickable(onClick = onToggle, onLongClick = minimize)
                        .clearAndSetSemantics {
                            role = Role.Button
                            contentDescription = VillageHudLogic.description(status, badges)
                            onClick(toggleLabel) { onToggle(); true }
                            onLongClick(bi("villageHud.hide")) { minimize(); true }
                            customActions = listOf(hide)
                        }
                        .padding(start = 10.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(status.emoji, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    Spacer(Modifier.width(3.dp))
                    Text(status.text, color = ink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(
                    Modifier.size(width = 30.dp, height = 48.dp)
                        .combinedClickable(onClick = onToggle, onLongClick = minimize)
                        .clearAndSetSemantics {
                            role = Role.Button
                            contentDescription = toggleLabel
                            onLongClick(bi("villageHud.hide")) { minimize(); true }
                            customActions = listOf(hide)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Chevron(up = expanded, color = ink, reduced = reduced)
                }
            }
        }
        if (badges.isNotEmpty()) {
            Row(Modifier.align(Alignment.TopStart).offset(x = (-2).dp, y = (-7).dp), horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                for (b in badges) BadgeDot(b)
            }
        }
    }
}

/** One badge: its emoji small in a ring, red for the fire, with a red dot for the chest's news. */
@Composable
private fun BadgeDot(b: HudBadge) {
    Box {
        Box(
            Modifier.size(20.dp).clip(CircleShape).background(if (b.kind == HudBadge.Kind.FIRE) TriglavRed else Color.White)
                .border(1.5.dp, Color(0xFF2B2118), CircleShape).clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) { Text(b.emoji, fontSize = 10.sp, lineHeight = 11.sp, maxLines = 1) }
        if (b.kind == HudBadge.Kind.CHEST) NewDot(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp).size(8.dp))
    }
}

/** Minimized: "⌄ 🛖" in the corner (a red dot when a badge wants a look); a tap brings the row back. */
@Composable
private fun HudPill(status: HudStatus, badges: List<HudBadge>, onClick: () -> Unit) {
    val description = "${bi("villageHud.show")}. ${VillageHudLogic.description(status, badges)}"
    Box {
        Surface(
            onClick = onClick, color = HudPlate, shape = RoundedCornerShape(50),
            modifier = Modifier.heightIn(min = 48.dp).hudSemantics(description, onClick),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Chevron(up = false, color = Color.White, reduced = true)
                Spacer(Modifier.width(6.dp))
                Text(status.emoji, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
        if (badges.isNotEmpty()) NewDot(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
    }
}

/**
 * The panel under the row: the age and its way, the village's numbers (villagers, morale, the fire, the chest, the
 * projects, progress), the goal with its checklist and one-tap action, today's festival and news, and "Skrij vrstico ·
 * Hide the bar". Everything in it closes it as it opens what it leads to.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HudPanel(view: HudView, actions: HudActions, reduced: Boolean) {
    val s = view.state
    val attrs = view.attrs
    fun then(f: () -> Unit): () -> Unit = { actions.expand(false); f() }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        // it scrolls rather than covering the bar at the thumb
        val most = if (maxHeight.isFinite) (maxHeight - 96.dp).coerceAtLeast(240.dp) else 600.dp
        Surface(color = HudPanelPlate, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().heightIn(max = most)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AgeChip(s, view.check, reduced, then(actions.age))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    StatChip("👥", "${s.villagers}/${attrs.populationCap}", "${bi("villageScreen.villagers")}: ${s.villagers} / ${attrs.populationCap}", onClick = then(actions.info))
                    StatChip(moraleEmoji(s.morale), "${s.morale}", "${bi("villageScreen.morale")}: ${s.morale}", onClick = then(actions.info))
                    StatChip(
                        "🔥", "${s.fire}", "${bi("villageScreen.fire")}: ${s.fire}" + if (s.fire < 25) ", ${bi("villageScreen.dying")}" else "",
                        warn = s.fire < 25, reduced = reduced, onClick = then(actions.fire),
                    )
                    StatChip(
                        "🧰", "🤝 ${s.help}",
                        "${bi("villageScreen.chest")}. ${bi("common.help")}: ${s.help}" + if (view.chestNew) ", ${bi("villageScreen.somethingNew")}" else "",
                        dot = view.chestNew, onClick = then(actions.chest),
                    )
                    if (s.age >= Age.ZASELEK) {
                        val open = si.lanisce.lani.game.Projects.open(s)
                        val done = open.count { si.lanisce.lani.game.Projects.finished(s, it) }
                        StatChip(
                            "🏗️", "$done/${open.size}",
                            "${bi("villageScreen.villageProjects")}: ${bi("villageScreen.finished", "done" to done, "total" to open.size)}" +
                                if (view.stepToday) ", ${bi("villageHud.stepToday")}" else "",
                            dot = view.stepToday, onClick = then(actions.projects),
                        )
                    }
                    Surface(onClick = then(actions.progress), color = PanelChip, shape = CircleShape, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) { EmojiLabel("📊", Labels.PROGRESS, style = MaterialTheme.typography.titleSmall) }
                    }
                }
                GoalBlock(view.goal, view.goalAction, onGoal = then(actions.goal), onAction = { a -> actions.expand(false); a.run() })
                // the festival that is on (a tap celebrates), then what's coming (a tap opens the scroll's today)
                view.festival?.let { f ->
                    TeaserPill(f.festival.emoji, Calendar.todayText(f).replace(" ${f.festival.emoji}", ""), gold = !f.festival.quiet, onClick = then { actions.festival(f.festival.id) })
                }
                for (t in view.teasers.take(3)) TeaserPill(t.emoji, t.text, onClick = then(actions.news))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Surface(onClick = { actions.minimize(true) }, color = PanelChip, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 40.dp)) {
                        Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text("⌃  ${bi("villageHud.hide")}", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The goal in the panel: its line ("🎯 Vas: še 4 koraki ›", a tap opens the scroll at "Naslednji cilj"), what is still
 * missing, and its one-tap action (the goal card's first button).
 */
@Composable
private fun GoalBlock(goal: NextGoal, action: GoalAction?, onGoal: () -> Unit, onAction: (GoalAction) -> Unit) {
    val missing = when (goal) {
        is NextGoal.Grow -> goal.missing
        is NextGoal.Learn -> goal.missing
        is NextGoal.Friends -> goal.missing
        is NextGoal.Polish -> goal.missing
        else -> emptyList()
    }
    Surface(onClick = onGoal, color = Color.White.copy(alpha = 0.07f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("🎯 ${goal.short()} ›", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            for (m in missing.take(4)) Text("☐ $m", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (missing.size > 4) Text("+${missing.size - 4}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            if (action != null) {
                Spacer(Modifier.height(2.dp))
                Surface(onClick = { onAction(action) }, color = action.color, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 40.dp)) {
                    Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        // dark ink on gold ("✨ Napreduj"), white on the others
                        Text(action.label, color = if (action.color == XpGold) Color(0xFF2B2118) else Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    }
                }
            }
        }
    }
}

/** The panel's plate: darker than a chip over the map, so the map doesn't show through much. */
private val HudPanelPlate = Color(0xF20F1520)

/** A chip on the panel: lighter than the panel, so it stands out on it. */
private val PanelChip = Color(0x24FFFFFF)

/** The chevron of the status chip and the pill: down (more to show), up (the panel open); it turns, unless [reduced]. */
@Composable
private fun Chevron(up: Boolean, color: Color, reduced: Boolean) {
    val turn by animateFloatAsState(if (up) 180f else 0f, if (reduced) snap() else tween(200), label = "chevron")
    Canvas(Modifier.size(14.dp).rotate(turn).clearAndSetSemantics { }) {
        val w = size.width; val h = size.height
        val path = Path().apply { moveTo(w * 0.15f, h * 0.35f); lineTo(w * 0.5f, h * 0.7f); lineTo(w * 0.85f, h * 0.35f) }
        drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * While [active] says so (the panel is open), a press outside [inside] (this layout's px; the row and the panel) closes it
 * ([onOutside]): a tap there does nothing else (its up is taken), a drag goes on to the map, which pans. Seen before the
 * children ([PointerEventPass.Initial]), so whatever is under the finger never misses a drag. The lambdas are kept from
 * the first composition and called at each touch: they read what changes (state), they aren't replaced.
 */
fun Modifier.outsideCloses(active: () -> Boolean, inside: () -> Rect?, onOutside: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!active() || inside()?.contains(down.position) == true) return@awaitEachGesture
        onOutside()
        val slop = viewConfiguration.touchSlop
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            if ((change.position - down.position).getDistance() > slop) break // a drag: the map's
            if (!change.pressed) { change.consume(); break } // a tap's up: taken, so nothing under it opens
        }
    }
}

/** A round emoji button over the scene: 48 dp to touch, [description] for TalkBack. */
@Composable
internal fun HudButton(label: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, color = HudPlate, shape = CircleShape, modifier = modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) { EmojiLabel(label, description, color = Color.White, style = MaterialTheme.typography.titleMedium) }
    }
}

fun moraleEmoji(m: Int) = when {
    m >= 70 -> "😄"
    m >= 40 -> "🙂"
    else -> "😟"
}

/**
 * A HUD stat; TalkBack reads [description] ("Ogenj · Fire: 40") instead of the emoji and the bare number. [dot]: a red
 * dot on it, something new there. [warn] pulses it red (held still when [reduced]).
 */
@Composable
private fun StatChip(emoji: String, value: String, description: String, warn: Boolean = false, dot: Boolean = false, reduced: Boolean = false, onClick: () -> Unit) {
    val pulse = if (warn && !reduced) {
        rememberInfiniteTransition(label = "warn").animateFloat(0.6f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a").value
    } else 1f
    Box {
        Surface(
            onClick = onClick,
            color = if (warn) TriglavRed.copy(alpha = 0.8f * pulse) else PanelChip,
            shape = RoundedCornerShape(50),
            modifier = Modifier.heightIn(min = 40.dp).hudSemantics(description, onClick),
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(4.dp))
                Text(value, color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (dot) NewDot(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
    }
}

/**
 * The age with the way to the next one: a small bar filling up and the next age's emoji; it glows gold when the village
 * can advance (held still when [reduced]). Opens the ages sheet with every step.
 */
@Composable
private fun AgeChip(s: GameState, check: AdvanceCheck, reduced: Boolean, onClick: () -> Unit) {
    val next = check.next
    val pulse = if (check.ok && !reduced) {
        rememberInfiniteTransition(label = "ready").animateFloat(0.55f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a").value
    } else 1f
    Surface(
        onClick = onClick,
        color = if (check.ok) XpGold.copy(alpha = 0.9f * pulse) else PanelChip,
        shape = RoundedCornerShape(50),
        modifier = Modifier.heightIn(min = 40.dp).hudSemantics(VillageHudLogic.ageDescription(s, check), onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.age.emoji)
            Spacer(Modifier.width(6.dp))
            Text(s.age.sl, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (next != null) {
                Spacer(Modifier.width(8.dp))
                if (check.ok) Text("✨ ${next.emoji}", color = Color.White, fontWeight = FontWeight.Bold)
                else {
                    Box(Modifier.width(38.dp).height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(check.progress.coerceIn(0.06f, 1f)).clip(CircleShape).background(XpGold))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(next.emoji, modifier = Modifier.alpha(0.8f))
                    VillageHudLogic.steps(check)?.takeIf { it.second > 0 }?.let { (done, all) ->
                        Spacer(Modifier.width(6.dp))
                        Text("$done/$all", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** A line of the panel's today: the festival that is on ([gold]), or what's coming ("Jutri · Tomorrow"). */
@Composable
private fun TeaserPill(emoji: String, text: String, gold: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (gold) XpGold.copy(alpha = 0.92f) else PanelChip, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 40.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(6.dp))
            Text(
                text, color = if (gold) Color(0xFF2B2118) else Color.White, style = MaterialTheme.typography.labelMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** For [VillageHud]'s callers: the latest [value] read where it changes after composition (a gesture's handler). */
@Composable
internal fun <T> latest(value: T): () -> T {
    val v by rememberUpdatedState(value)
    return { v }
}
