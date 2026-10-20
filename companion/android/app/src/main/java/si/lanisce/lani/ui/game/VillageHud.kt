package si.lanisce.lani.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import si.lanisce.lani.data.HudPrefs
import si.lanisce.lani.game.AdvanceCheck
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.AgeStep
import si.lanisce.lani.game.Attributes
import si.lanisce.lani.game.Calendar
import si.lanisce.lani.game.FestivalDay
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Projects
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.Surprises
import si.lanisce.lani.game.Teaser
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Localized
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.FlameOrange
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import java.time.LocalDate
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The village's HUD (companion/GAME.md, "The village's HUD"): a slim bar over the map (back, the four resources, the
 * settlement's ring), a row of small cards under it that the ring shows and hides (the goal, today and what's coming,
 * the settlement's numbers), and a card's section in a sheet from the bottom.
 */
enum class HudSection { GOAL, SOON, SETTLEMENT }


/** The ring's status: the age and the way to the next one, or the festival that is on (see [VillageHudLogic.status]). */
data class HudStatus(val emoji: String, val text: String, val description: String, val tone: Tone) {
    enum class Tone {
        /** The age, on its way to the next one. */
        PLAIN,
        /** Ready to advance: it glows gold. */
        READY,
        /** A feast today: gold. */
        FESTIVAL,
        /** A quiet day (Dan spomina na mrtve): plain. */
        QUIET,
    }
}

/** What a line of "Danes in kmalu" leads to, when it leads anywhere (today's: something to do now). */
sealed interface SoonAction {
    data class Festival(val id: String) : SoonAction
    data class Arrival(val id: String) : SoonAction
    data object Surprise : SoonAction
    data class Project(val id: String) : SoonAction
    /** An age's party (companion/GAME.md "Ages and their celebrations"). */
    data class Party(val age: si.lanisce.lani.game.Age) : SoonAction
}

/**
 * A line of "Danes in kmalu · Today and coming up": [day] from today (0 today; [evening]: tonight), [what] in each
 * language without its day (the timeline says when), [key] to know it again. [news]: today's, and tonight's story, a
 * dot on the card until the sheet showed it; the later ones are no news.
 */
data class SoonLine(
    val key: String, val day: Int, val evening: Boolean, val emoji: String, val what: Localized,
    val gold: Boolean = false, val news: Boolean = false, val action: SoonAction? = null,
)

/** Which cards wear a dot: something new in them, never a standing count. */
data class HudDots(val goal: Boolean, val soon: Boolean, val settlement: Boolean) {
    val any: Boolean get() = goal || soon || settlement
}

/** What the HUD shows and when, and its texts; pure, so the tests see it without a screen. */
object VillageHudLogic {
    private const val SEP = " · "

    /** The row of cards: the learner's choice, hidden by itself while a plot chooser is on unless the ring was tapped then ([peek]). */
    fun cards(shown: Boolean, choosing: Boolean, peek: Boolean = false): Boolean = if (choosing) peek else shown

    /** How many of the next age's steps are done, and how many it has ("7/9"); null at the last age. */
    fun steps(check: AdvanceCheck): Pair<Int, Int>? =
        if (check.next == null) null else check.steps.count { it.done } to check.steps.size

    /**
     * The ring: a festival that is on and not kept yet shows itself ("🍷 Danes", gold; a quiet day's "🕯️ Danes", plain),
     * else the next age with its steps done ("🏘️ 7/9"), "✨" when the village can advance. [description] is what
     * TalkBack reads: the age's words, and the steps still to go.
     */
    fun status(s: GameState, check: AdvanceCheck, festival: FestivalDay?): HudStatus {
        if (festival != null && !festival.done) {
            val f = festival.festival
            return HudStatus(f.emoji, inTarget("common.today"), Calendar.todayText(festival), if (f.quiet) HudStatus.Tone.QUIET else HudStatus.Tone.FESTIVAL)
        }
        val next = check.next ?: return HudStatus(s.age.emoji, s.age.sl, "${bi("villageScreen.age")}: ${s.age.label()}", HudStatus.Tone.PLAIN)
        val (done, all) = steps(check) ?: (0 to 0)
        val count = if (all > 0) "$done/$all" else "${(check.progress * 100).roundToInt()} %"
        return if (check.ok) HudStatus(next.emoji, "✨", ageDescription(s, check), HudStatus.Tone.READY)
        else HudStatus(next.emoji, count, ageDescription(s, check) + if (all > 0) ", ${stepsLeft(all - done)}" else "", HudStatus.Tone.PLAIN)
    }

    /** "Doba · Age: Zaselek · Hamlet. Do Vas · To the village: 83 %". */
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
     * [text] ("Les · Wood") split at its middle " · " into the target's half and the base's: a label made by [bi] has as
     * many in each half. One without (or with an even number): all of it the target's, no base.
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

    /** A "target · base" text as one [Localized]: its target half in the target language, its base half in the others. */
    fun localized(text: String): Localized {
        val (a, b) = halves(text)
        return Localized { lang -> if (lang == L10n.pair.target) a else b ?: a }
    }

    /** [key] in the target and in the base. */
    fun two(key: String, vararg args: Pair<String, Any?>): Pair<String, String> = inTarget(key, *args) to inBase(key, *args)

    private val WEEKDAYS = listOf(
        "townScroll.monday", "townScroll.tuesday", "townScroll.wednesday", "townScroll.thursday", "townScroll.friday",
        "townScroll.saturday", "townScroll.sunday",
    )

    /** When a line comes: "Danes · Today", "Nocoj · Tonight", "Jutri · Tomorrow", "Torek · Tuesday", "Čez 9 dni · In 9 days". */
    fun whenOf(day: Int, evening: Boolean, today: LocalDate): Pair<String, String> {
        val (a, b) = when {
            day <= 0 && evening -> two("villageHud.tonight")
            day <= 0 -> two("common.today")
            day == 1 -> two("tomorrow.on", "day" to "0")
            day < 7 -> two(WEEKDAYS[today.plusDays(day.toLong()).dayOfWeek.value - 1])
            else -> two("villageHud.inDays", "n" to day)
        }
        return a.replaceFirstChar { it.uppercase() } to b.replaceFirstChar { it.uppercase() }
    }

    /**
     * "Danes in kmalu · Today and coming up", in the order they come: today's (an age's party now or tonight ([party]: its
     * celebration, and whether it is later tonight), the festival that is on, who joined the village and waits to be met, the
     * day's surprise at the road, a project step to take), then what's coming
     * ([Teaser]s: tonight's story, tomorrow's step, the pedlar's day …). A villager's request or a happening isn't here:
     * their bubbles are on the map.
     */
    fun soon(
        village: VillageToday, arrivals: List<TodayItem.Arrival>, today: LocalDate, party: Pair<si.lanisce.lani.game.Celebration, Boolean>? = null,
    ): List<SoonLine> = buildList {
        // an age's party: now, or later tonight (its evening: the timeline says "tonight")
        party?.let { (c, later) ->
            add(SoonLine("party:${c.age.name}:$today", 0, later, c.emoji, c.nameText, gold = true, news = true, action = SoonAction.Party(c.age)))
        }
        village.festival?.takeIf { !it.done }?.let { d ->
            val f = d.festival
            add(SoonLine("festival:${f.id}:$today", 0, false, f.emoji, f.nameText, gold = !f.quiet, news = true, action = SoonAction.Festival(f.id)))
        }
        for (a in arrivals) add(SoonLine("arrival:${a.id}", 0, false, a.emoji, localized(a.title), news = true, action = SoonAction.Arrival(a.id)))
        village.surprise?.takeIf { !it.done }?.let { sp ->
            val emoji = Surprises.emoji(sp.kind)
            add(SoonLine("surprise:${sp.kind}:$today", 0, false, emoji, localized(Surprises.title(sp).removePrefix("$emoji ")), news = true, action = SoonAction.Surprise))
        }
        for (o in village.projects.filter { it.available }) {
            add(SoonLine("project:${o.spec.id}:${o.done}", 0, false, o.spec.emoji, si.lanisce.lani.game.Tomorrow.stepOf(o.spec, o.done + 1, o.steps), news = true, action = SoonAction.Project(o.spec.id)))
        }
        for (t in village.teasers) add(teaser(t))
    }.sortedBy { it.day * 2 + if (it.evening) 1 else 0 }

    private fun teaser(t: Teaser): SoonLine {
        val day = t.day ?: 1
        val tonight = day <= 0 && t.evening
        return SoonLine("teaser:${t.kind}:$day:${t.text}", day, t.evening, t.emoji, t.what ?: localized(t.text), news = tonight)
    }

    /** A key for each of the goal's open steps (or the goal itself), so a new one shows a dot. */
    fun stepKeys(goal: NextGoal, check: AdvanceCheck): List<String> = when (goal) {
        is NextGoal.Grow, is NextGoal.Learn, is NextGoal.Friends, is NextGoal.Polish ->
            check.steps.filter { !it.done }.map { "${check.next?.name}:${it.kind}:${it.res?.name ?: it.emoji}" }
        NextGoal.FeedFire -> listOf("fire")
        is NextGoal.Advance -> listOf("advance:${goal.to.name}")
        is NextGoal.Build -> listOf("build:${goal.option.type.name}")
        is NextGoal.Gather -> listOf("gather:${goal.res.name}")
    }

    /** The goal's steps still open, when the goal is the way to the next age (else none: its one action). */
    fun openSteps(goal: NextGoal, check: AdvanceCheck): List<AgeStep> = when (goal) {
        is NextGoal.Grow, is NextGoal.Learn, is NextGoal.Friends, is NextGoal.Polish -> check.steps.filter { !it.done }
        else -> emptyList()
    }

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

    /** The cards' dots: a goal's step not seen before, today's news or tonight's story not seen in the sheet, the chest's news. */
    fun dots(view: HudView): HudDots = HudDots(
        goal = stepKeys(view.goal, view.check).any { it !in view.stepsSeen },
        soon = view.soon.any { it.news && it.key !in view.soonSeen },
        settlement = view.chestNew,
    )

    /** What the ring says to TalkBack: the status, then which cards have something new. */
    fun description(status: HudStatus, dots: HudDots): String {
        val new = buildList {
            if (dots.goal) add(bi("villageHud.nextGoal"))
            if (dots.soon) add(bi("villageHud.soon"))
            if (dots.settlement) add(bi("villageScreen.chest"))
        }
        return status.description + if (new.isEmpty()) "" else "; ${bi("villageHud.new")}: ${new.joinToString(", ")}"
    }
}

/**
 * The HUD's state on the village screen: the row of cards (the learner's choice on this phone, [prefs]), the section in
 * the sheet ([section]), and the ring tapped while a plot chooser is on (the row, for that chooser only).
 */
@Stable
class HudState(private val prefs: HudPrefs) {
    /** The card whose section is in the sheet, or null: none open. */
    var section by mutableStateOf<HudSection?>(null)
    /** The chooser whose ring was tapped: the row while it lasts. */
    private var peekAt by mutableStateOf<Int?>(null)

    /** Whether the row of cards shows now; [chooser]: the plot chooser that is on (a number for each time it opens), or null. */
    fun cards(chooser: Int?): Boolean = VillageHudLogic.cards(prefs.cardsShown, chooser != null, chooser != null && peekAt == chooser)

    /** The ring: the row shown or hidden, the learner's choice kept on the phone; while [chooser] is on, for it only. */
    fun toggleCards(chooser: Int?) {
        section = null
        if (chooser != null) peekAt = if (peekAt == chooser) null else chooser else prefs.showCards(!prefs.cardsShown)
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
    /** The festival that is on and not kept yet. */
    val festival: FestivalDay?,
    /** "Danes in kmalu · Today and coming up" ([VillageHudLogic.soon]). */
    val soon: List<SoonLine>,
    /** The keys of the lines the sheet showed last ([HudPrefs.soonSeen]). */
    val soonSeen: Set<String> = emptySet(),
    /** The goal's steps seen ([HudPrefs.stepsSeen]). */
    val stepsSeen: Set<String> = emptySet(),
) {
    val status: HudStatus get() = VillageHudLogic.status(state, check, festival)
    val dots: HudDots get() = VillageHudLogic.dots(this)
}

/** What the HUD's bar, cards and sheet do. */
class HudActions(
    val back: () -> Unit,
    val resource: (Res) -> Unit,
    /** The ages sheet: every step of the next age. */
    val age: () -> Unit,
    val info: () -> Unit,
    val fire: () -> Unit,
    val chest: () -> Unit,
    /** The projects sheet, at one of them (its id) or all. */
    val projects: (String?) -> Unit,
    val progress: () -> Unit,
    /** The goal's line in its sheet: the ages sheet, every step of the next age. */
    val goal: () -> Unit,
    val festival: (String) -> Unit,
    /** Meets someone who joined the village (their id). */
    val arrival: (String) -> Unit,
    /** The day's surprise at the road (the pedlar: his stall in the chest). */
    val surprise: () -> Unit,
    /** An open step of the next age worked on (gather, build, learn, polish, help). */
    val step: (AgeStep) -> Unit,
    /** The ring: the row of cards shown or hidden. */
    val cards: () -> Unit,
    /** A card's section in the sheet, or null: the sheet closed. */
    val sheet: (HudSection?) -> Unit,
    /** An age's party: now, its dialog. */
    val party: (si.lanisce.lani.game.Age) -> Unit = {},
)

/**
 * The village's HUD over the map (companion/GAME.md, "The village's HUD"): the bar, the row of cards when [cards], and
 * [section]'s sheet from the bottom. [onTop] gets the heights the bar (the status bar's included) and the row of cards
 * cover at the top, for the camera and the bubbles (the row's as it is when shown, whether it shows or not); [onInside] the rects a tap may land on without closing the sheet ("top",
 * "sheet"; null: gone). The recenter button hangs under them while the camera is [moved]. [reduced]: the system's
 * "remove animations": nothing slides or pulses.
 */
@Composable
fun VillageHud(
    view: HudView, cards: Boolean, section: HudSection?, actions: HudActions, moved: Boolean, onRecenter: () -> Unit, reduced: Boolean,
    modifier: Modifier = Modifier, onTop: (bar: Int, row: Int) -> Unit = { _, _ -> }, onInside: (String, Rect?) -> Unit = { _, _ -> },
) {
    val dots = view.dots
    // what leads on closes the sheet first
    val leads = remember(actions) {
        fun then(f: () -> Unit): () -> Unit = { actions.sheet(null); f() }
        HudActions(
            back = then(actions.back), resource = { r -> actions.sheet(null); actions.resource(r) }, age = then(actions.age), info = then(actions.info),
            fire = then(actions.fire), chest = then(actions.chest), projects = { id -> actions.sheet(null); actions.projects(id) },
            progress = then(actions.progress), goal = then(actions.goal), festival = { id -> actions.sheet(null); actions.festival(id) },
            arrival = { id -> actions.sheet(null); actions.arrival(id) }, surprise = then(actions.surprise),
            step = { st -> actions.sheet(null); actions.step(st) }, cards = actions.cards, sheet = actions.sheet,
        )
    }
    var barPx by remember { mutableStateOf(0) }
    var rowPx by remember { mutableStateOf(0) }
    LaunchedEffect(barPx, rowPx) { if (barPx > 0) onTop(barPx, rowPx) }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().onGloballyPositioned { onInside("top", it.boundsInWindow()) }) {
                Box(Modifier.onSizeChanged { barPx = it.height }) { Bar(view, leads, cards, ringDot = !cards && dots.any, dots = dots, reduced = reduced) }
                AnimatedVisibility(
                    cards,
                    enter = if (reduced) EnterTransition.None else expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = if (reduced) ExitTransition.None else shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                ) {
                    Box(Modifier.onSizeChanged { rowPx = it.height }) { CardRow(view, dots, leads) }
                }
            }
            if (moved) Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                Spacer(Modifier.weight(1f))
                RecenterButton(onRecenter)
            }
        }
        // the sheet keeps its section while it slides away
        var shown by remember { mutableStateOf(section ?: HudSection.GOAL) }
        if (section != null) shown = section
        HudSheet(section != null, reduced, onClose = { actions.sheet(null) }, onBounds = { onInside("sheet", it) }) {
            when (shown) {
                HudSection.GOAL -> GoalSection(view, leads)
                HudSection.SOON -> SoonSection(view, leads)
                HudSection.SETTLEMENT -> SettlementSection(view, leads)
            }
        }
    }
}

// --- the bar ----------------------------------------------------------------------------------

/** The bar: back, the four resources (their bars to the cap; a tap: its sheet, with Trade), the settlement's ring. */
@Composable
private fun Bar(view: HudView, actions: HudActions, cards: Boolean, ringDot: Boolean, dots: HudDots, reduced: Boolean) {
    val s = view.state
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HudButton("←", Labels.BACK, actions.back)
        for (r in Res.entries) ResourceChip(r, s.res(r), view.attrs.caps[r], onClick = { actions.resource(r) }, modifier = Modifier.weight(1f))
        SettlementRing(view, cards, ringDot, dots, reduced, actions.cards)
    }
}

/**
 * The settlement's ring: the next age's emoji in a ring that fills with its steps, "7/9" under it (the one place it
 * shows), gold when the village can advance (glowing) or a feast is on. A tap, or a long press, shows or hides the row
 * of cards; [dot]: the row is hidden and something in it is new.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettlementRing(view: HudView, cards: Boolean, dot: Boolean, dots: HudDots, reduced: Boolean, onToggle: () -> Unit) {
    val status = view.status
    val haptic = LocalHapticFeedback.current
    val gold = status.tone == HudStatus.Tone.READY || status.tone == HudStatus.Tone.FESTIVAL
    val glow = status.tone == HudStatus.Tone.READY && !reduced
    val pulse = if (glow) rememberInfiniteTransition(label = "ready").animateFloat(0.55f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a").value else 1f
    val toggle = bi(if (cards) "villageHud.hideCards" else "villageHud.showCards")
    val said = VillageHudLogic.description(status, dots)
    val long = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onToggle() }
    Box(Modifier.size(width = 54.dp, height = 58.dp)) {
        Box(
            Modifier.align(Alignment.TopCenter).size(48.dp).clip(CircleShape).background(if (gold) XpGold.copy(alpha = 0.92f * pulse) else HudPlate)
                .combinedClickable(onClick = onToggle, onLongClick = long)
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = said
                    onClick(toggle) { onToggle(); true }
                    onLongClick(toggle) { long(); true }
                },
            contentAlignment = Alignment.Center,
        ) {
            val progress = view.check.progress
            Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                val w = 3.5.dp.toPx()
                drawArc(Color.White.copy(alpha = 0.18f), -90f, 360f, false, style = Stroke(w))
                if (!gold) drawArc(XpGold, -90f, 360f * progress.coerceIn(0.03f, 1f), false, style = Stroke(w, cap = StrokeCap.Round))
            }
            Text(status.emoji, fontSize = 19.sp, lineHeight = 20.sp, modifier = Modifier.offset(y = (-2).dp))
        }
        Surface(color = Ink, shape = RoundedCornerShape(50), modifier = Modifier.align(Alignment.BottomCenter).clearAndSetSemantics { }) {
            Text(
                status.text, color = if (gold) XpGold else Color.White, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        if (dot) NewDot(Modifier.align(Alignment.TopEnd).offset(x = (-1).dp, y = 1.dp))
    }
}

/** Dark ink: the ring's count, text on gold. */
private val Ink = Color(0xFF2B2118)

// --- the cards --------------------------------------------------------------------------------

/** The row of cards under the bar: the goal, today and what's coming, the settlement; swiped sideways, a tap opens one. */
@Composable
private fun CardRow(view: HudView, dots: HudDots, actions: HudActions) {
    val list = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth * 0.84f
        LazyRow(
            state = list, flingBehavior = rememberSnapFlingBehavior(list), contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).testTag(CARDS_TAG),
        ) {
            item { GoalCard(view, dots.goal, actions, Modifier.width(w)) }
            item { SoonCard(view, dots.soon, actions, Modifier.width(w)) }
            item { SettlementCard(view, dots.settlement, actions, Modifier.width(maxWidth - 16.dp)) }
        }
    }
}

/** The row of cards, for the tests to scroll it. */
internal const val CARDS_TAG = "hud-cards"

/** The cards' plate: the bar's, a little stronger, so two lines read over any sky. */
private val CardPlate = Color(0xE0141B2A)

@Composable
private fun SmallCard(modifier: Modifier, description: String, dot: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Box(modifier) {
        Surface(
            onClick = onClick, color = CardPlate, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(60.dp).semantics { contentDescription = description },
        ) {
            Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) { content() }
        }
        if (dot) NewDot(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
    }
}

/** The goal's card: the goal, the next step with how far, and that step's own action ("🪵 Nabiraj · Gather"). */
@Composable
private fun GoalCard(view: HudView, dot: Boolean, actions: HudActions, modifier: Modifier) {
    val (t, b) = VillageHudLogic.halves(view.goal.short())
    val first = VillageHudLogic.openSteps(view.goal, view.check).firstOrNull { VillageHudLogic.actionOf(it) != null }
    val line2 = first?.let { st -> "${st.label}${if (st.kind != AgeStep.Kind.RUSTY) "  ${min(st.have, st.need)}/${st.need}" else ""}" } ?: b
    SmallCard(modifier, "${bi("villageHud.nextGoal")}: ${view.goal.short()}", dot, onClick = { actions.sheet(HudSection.GOAL) }) {
        Text("🎯", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        TwoLines(t, line2, Modifier.weight(1f))
        val act = first?.let { VillageHudLogic.actionOf(it) }
        if (first != null && act != null) {
            Spacer(Modifier.width(6.dp))
            ActionPill(act.first, act.second, AlpineGreen) { actions.step(first) }
        } else view.goalAction?.let { a ->
            Spacer(Modifier.width(6.dp))
            val (at, ab) = VillageHudLogic.halves(a.label)
            ActionPill(at, ab, a.color) { actions.sheet(null); a.run() }
        }
    }
}

/** Today and what's coming: the next line, "+n" more. */
@Composable
private fun SoonCard(view: HudView, dot: Boolean, actions: HudActions, modifier: Modifier) {
    val first = view.soon.firstOrNull()
    val pair = L10n.pair
    SmallCard(modifier, bi("villageHud.soon") + (first?.let { ": ${it.what.of(pair.target)}" } ?: ""), dot, onClick = { actions.sheet(HudSection.SOON) }) {
        if (first == null) {
            Text("🗓️", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            TwoLines(inTarget("villageHud.soon"), inBase("villageHud.soon"), Modifier.weight(1f))
            return@SmallCard
        }
        WhenColumn(VillageHudLogic.whenOf(first.day, first.evening, LocalDate.now()), Modifier.width(62.dp))
        Text(first.emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp))
        TwoLines(first.what.of(pair.target), first.what.of(pair.base), Modifier.weight(1f), strong = false)
        val more = view.soon.size - 1
        if (more > 0) {
            Spacer(Modifier.width(6.dp))
            Surface(color = PanelChip, shape = RoundedCornerShape(50)) {
                Text("+$more", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
            }
        }
    }
}

/** The settlement: the way to the next age, and its six numbers, each with its name under it (the target's, in brief). */
@Composable
private fun SettlementCard(view: HudView, dot: Boolean, actions: HudActions, modifier: Modifier) {
    val s = view.state
    val next = view.check.next
    SmallCard(modifier, "${bi("villageHud.settlement")}: ${s.age.label()}", dot, onClick = { actions.sheet(HudSection.SETTLEMENT) }) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (next != null) "${s.age.emoji} ${s.age.sl} → ${next.emoji} ${next.sl}" else "${s.age.emoji} ${s.age.sl}",
                    color = XpGold, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                )
                if (next != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(view.check.progress.coerceIn(0.03f, 1f)).clip(CircleShape).background(XpGold))
                    }
                }
            }
            Row {
                for (t in tiles(view, actions)) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${t.emoji}${t.value}", color = if (t.warn) TriglavRed else Color.White, fontSize = 12.sp, lineHeight = 15.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1,
                        )
                        Text(t.label.first, color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// --- the sheet's sections ---------------------------------------------------------------------

/** A section's header: "NASLEDNJI CILJ · Next goal", and what it may hold at its end. */
@Composable
private fun SectionHeader(key: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        // TalkBack reads the heading as the app's labels read, "Naselje · Settlement"; what's at its end is its own
        Row(Modifier.weight(1f).clearAndSetSemantics { heading(); contentDescription = bi(key) }, verticalAlignment = Alignment.CenterVertically) {
            Text(inTarget(key).uppercase(), color = XpGold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
            Text(" · ${inBase(key)}", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

/** Two lines, the target's over the base's, each cut with "…" ([lines] each). */
@Composable
private fun TwoLines(target: String, base: String?, modifier: Modifier = Modifier, strong: Boolean = true, lines: Int = 1) {
    Column(modifier) {
        Text(
            target, color = Color.White, style = if (strong) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal, maxLines = lines, overflow = TextOverflow.Ellipsis,
        )
        if (!base.isNullOrBlank() && base != target) Text(base, color = Color.White.copy(alpha = 0.62f), style = MaterialTheme.typography.bodySmall, maxLines = lines, overflow = TextOverflow.Ellipsis)
    }
}

/** When a line comes: the target's word in gold over the base's. */
@Composable
private fun WhenColumn(at: Pair<String, String>, modifier: Modifier) {
    Column(modifier) {
        Text(at.first, color = XpGold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(at.second, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The next goal: its line (a tap: the ages sheet), then each open step with its own action, or the goal's one action. */
@Composable
private fun GoalSection(view: HudView, actions: HudActions) {
    val steps = VillageHudLogic.openSteps(view.goal, view.check)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("villageHud.nextGoal")
        val (t, b) = VillageHudLogic.halves(view.goal.short())
        Surface(onClick = actions.goal, color = Color.Transparent, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🎯", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
                TwoLines(t, b, Modifier.weight(1f))
                Text("›", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium)
            }
        }
        if (steps.isNotEmpty()) for (st in steps) StepLine(st) { actions.step(st) }
        else view.goalAction?.let { a ->
            val (at, ab) = VillageHudLogic.halves(a.label)
            ActionPill(at, ab, a.color, wide = true) { actions.sheet(null); a.run() }
        }
    }
}

/** An open step of the next age: what, how much of how much, a thin bar, and its action ("🪵 Nabiraj · Gather"). */
@Composable
private fun StepLine(step: AgeStep, onAction: () -> Unit) {
    val (t, b) = VillageHudLogic.halves(step.label)
    Surface(color = PanelChip, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(step.emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(30.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t + if (step.broken) " 🔧" else "", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (step.kind != AgeStep.Kind.RUSTY) Text("${min(step.have, step.need)}/${step.need}", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                }
                if (b != null) Text(b, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(step.share.coerceIn(0.03f, 1f)).clip(CircleShape).background(XpGold))
                }
            }
            VillageHudLogic.actionOf(step)?.let { (at, ab) ->
                Spacer(Modifier.width(10.dp))
                ActionPill(at, ab, AlpineGreen, onClick = onAction)
            }
        }
    }
}

/** A small action: the target's word over the base's. */
@Composable
private fun ActionPill(target: String, base: String?, color: Color, wide: Boolean = false, onClick: () -> Unit) {
    val ink = if (color == XpGold) Ink else Color.White
    Surface(
        onClick = onClick, color = color, shape = RoundedCornerShape(50),
        modifier = Modifier.heightIn(min = 44.dp).then(if (wide) Modifier.fillMaxWidth() else Modifier.widthIn(min = 84.dp, max = 124.dp)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(target, color = ink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (!base.isNullOrBlank() && base != target) Text(base, color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

/** Today and what's coming as a short timeline: when, an emoji, a line in each language; today's lead on, the others show all of themselves on a tap. */
@Composable
private fun SoonSection(view: HudView, actions: HudActions) {
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("villageHud.soon")
        if (view.soon.isEmpty()) Text(bi("townScroll.quietDayVillageLook"), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
        for (line in view.soon) {
            val open: (() -> Unit)? = when (val a = line.action) {
                is SoonAction.Festival -> ({ actions.festival(a.id) })
                is SoonAction.Arrival -> ({ actions.arrival(a.id) })
                SoonAction.Surprise -> actions.surprise
                is SoonAction.Project -> ({ actions.projects(a.id) })
                is SoonAction.Party -> ({ actions.party(a.age) })
                null -> null
            }
            SoonRow(line, VillageHudLogic.whenOf(line.day, line.evening, today), new = line.news && line.key !in view.soonSeen, onOpen = open)
        }
    }
}

@Composable
private fun SoonRow(line: SoonLine, at: Pair<String, String>, new: Boolean, onOpen: (() -> Unit)?) {
    var full by remember(line.key) { mutableStateOf(false) }
    val pair = L10n.pair
    Box {
        Surface(
            onClick = { if (onOpen != null) onOpen() else full = !full },
            color = if (line.gold) XpGold.copy(alpha = 0.25f) else PanelChip, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = if (full) Alignment.Top else Alignment.CenterVertically) {
                WhenColumn(at, Modifier.width(78.dp))
                Text(line.emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(30.dp))
                TwoLines(line.what.of(pair.target), line.what.of(pair.base), Modifier.weight(1f), strong = false, lines = if (full) 8 else 1)
                if (onOpen != null) Text("›", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (new) NewDot(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
    }
}

/** The settlement: its age and the way to the next (a tap: the ages), and its numbers, each with its name under it. */
@Composable
private fun SettlementSection(view: HudView, actions: HudActions) {
    val s = view.state
    val check = view.check
    val next = check.next
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("villageHud.settlement") {
            Surface(onClick = actions.progress, color = PanelChip, shape = RoundedCornerShape(50), modifier = Modifier.hudSemantics(Labels.PROGRESS, actions.progress)) {
                Text("📊 ${inTarget("common.progress")} ›", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
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
                if (next != null) Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(check.progress.coerceIn(0.03f, 1f)).clip(CircleShape).background(XpGold))
                }
            }
        }
        for (row in tiles(view, actions).chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in row) StatTile(t, Modifier.weight(1f))
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** One of the settlement's numbers: its emoji and value, its name in each language, what TalkBack reads, where it leads. */
private class Tile(
    val emoji: String, val value: String, val label: Pair<String, String>, val description: String,
    val warn: Boolean = false, val dot: Boolean = false, val onClick: () -> Unit,
    /** A thin bar under the value, 0..1 (the hearths lit of all); null: none. */
    val bar: Float? = null,
)

/** The settlement's six numbers: people, mood, the hearths lit of all (companion/GAME.md "Hearths"), help, the chest, the projects (from Zaselek). */
private fun tiles(view: HudView, actions: HudActions): List<Tile> {
    val s = view.state
    val open = if (s.age >= Age.ZASELEK) Projects.open(s) else emptyList()
    val done = open.count { Projects.finished(s, it) }
    val goods = s.chest.goods.values.sum() + s.chest.tools.size
    return listOfNotNull(
        Tile("👥", "${s.villagers}/${view.attrs.populationCap}", VillageHudLogic.two("villageHud.people"), "${bi("villageScreen.villagers")}: ${s.villagers} / ${view.attrs.populationCap}", onClick = actions.info),
        Tile(moraleEmoji(s.morale), "${s.morale}", VillageHudLogic.two("villageHud.mood"), "${bi("villageScreen.morale")}: ${s.morale}", onClick = actions.info),
        HearthsView.of(s, java.time.LocalDate.now()).let { h ->
            Tile(
                "🔥", h.tile, VillageHudLogic.two("hearths.title"), "${bi("hearths.title")}: ${h.sub}",
                warn = h.short, onClick = actions.fire, bar = h.share,
            )
        },
        Tile("🤝", "${s.help}", VillageHudLogic.two("common.help"), "${bi("common.help")}: ${s.help}", onClick = actions.info),
        Tile(
            "🧰", "$goods", VillageHudLogic.two("villageScreen.chest"),
            bi("villageScreen.chest") + ": $goods" + if (view.chestNew) ", ${bi("villageScreen.somethingNew")}" else "", dot = view.chestNew, onClick = actions.chest,
        ),
        if (s.age >= Age.ZASELEK) Tile(
            "🏗️", "$done/${open.size}", VillageHudLogic.two("villageHud.projects"),
            "${bi("villageScreen.villageProjects")}: ${bi("villageScreen.finished", "done" to done, "total" to open.size)}", onClick = { actions.projects(null) },
        ) else null,
    )
}

/** A number with its name under it, in the target and the base: "86", "Razpoloženje", "Mood". */
@Composable
private fun StatTile(t: Tile, modifier: Modifier) {
    val pulse = if (t.warn) rememberInfiniteTransition(label = "warn").animateFloat(0.6f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a").value else 1f
    Box(modifier) {
        Surface(
            onClick = t.onClick, color = if (t.warn) TriglavRed.copy(alpha = 0.8f * pulse) else PanelChip, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).hudSemantics(t.description, t.onClick),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.emoji, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(5.dp))
                    Text(t.value, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                t.bar?.let { f ->
                    // the hearths lit of all: a thin bar under the number
                    Box(Modifier.padding(vertical = 2.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f))) {
                        Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).height(3.dp).background(if (t.warn) Color.White else FlameOrange))
                    }
                }
                Text(t.label.first, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.label.second, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (t.dot) NewDot(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
    }
}

// --- the sheet --------------------------------------------------------------------------------

/** The sheet's plate: opaque, so neither the map nor the bottom bar shows through. */
private val SheetPlate = Color(0xFF111824)

/** A chip or a tile on the sheet: lighter than the sheet, so it stands out on it. */
internal val PanelChip = Color(0x24FFFFFF)

/**
 * A card's section in a sheet from the screen's bottom, as tall as it needs (most of the screen at most), not modal: the
 * map above it stays to touch (a tap there closes it, a drag pans). Its handle dragged down closes it, so do its ✕ and
 * back. Its rect goes to [onBounds] while it's out.
 */
@Composable
private fun HudSheet(open: Boolean, reduced: Boolean, onClose: () -> Unit, onBounds: (Rect?) -> Unit, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val most = maxHeight * 0.86f
        LaunchedEffect(open) { if (!open) onBounds(null) }
        val density = LocalDensity.current
        val drag = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        AnimatedVisibility(
            open, Modifier.align(Alignment.BottomCenter),
            enter = if (reduced) EnterTransition.None else slideInVertically { it } + fadeIn(),
            exit = if (reduced) ExitTransition.None else slideOutVertically { it } + fadeOut(),
        ) {
            Surface(
                color = SheetPlate, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = most).offset { androidx.compose.ui.unit.IntOffset(0, drag.value.coerceAtLeast(0f).roundToInt()) }
                    .onGloballyPositioned { onBounds(it.boundsInWindow()) },
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().height(40.dp).draggable(
                            rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } }, Orientation.Vertical,
                            onDragStopped = { v ->
                                if (drag.value > with(density) { 80.dp.toPx() } || v > 2000f) onClose()
                                drag.animateTo(0f)
                            },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.width(48.dp))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(width = 40.dp, height = 4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
                        }
                        Surface(onClick = onClose, color = Color.Transparent, shape = CircleShape, modifier = Modifier.size(44.dp).padding(end = 4.dp).hudSemantics(Labels.CLOSE, onClose)) {
                            Box(contentAlignment = Alignment.Center) { Text("✕", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.titleSmall) }
                        }
                    }
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).navigationBarsPadding().padding(bottom = 14.dp),
                    ) { content() }
                }
            }
        }
    }
}

// --- shared -----------------------------------------------------------------------------------

/**
 * While [active] says so (the sheet is out), a press outside [inside] (this layout's px; the bar, the cards and the
 * sheet) closes it ([onOutside]): a tap there does nothing else (its up is taken), a drag goes on to the map, which pans.
 * Seen before the children ([PointerEventPass.Initial]), so whatever is under the finger never misses a drag. The lambdas
 * are kept from the first composition and called at each touch: they read what changes (state), they aren't replaced.
 */
fun Modifier.outsideCloses(active: () -> Boolean, inside: (Offset) -> Boolean, onOutside: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!active() || inside(down.position)) return@awaitEachGesture
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

/** For [VillageHud]'s callers: the latest [value] read where it changes after composition (a gesture's handler). */
@Composable
internal fun <T> latest(value: T): () -> T {
    val v by rememberUpdatedState(value)
    return { v }
}
