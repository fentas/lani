package si.lanisce.lani.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.game.AdvanceCheck
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.BuildOption
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingWords
import si.lanisce.lani.game.Catalog
import si.lanisce.lani.game.CourseGate
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.GrowOption
import si.lanisce.lani.game.Rebuild
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.UpgradeOption
import si.lanisce.lani.game.UpgradeWords
import si.lanisce.lani.game.at
import si.lanisce.lani.game.costText
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.TownMarker
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.LaniSheet
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.FlameOrange
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold

/**
 * What can be built and upgraded in [s] (with the cast: a workplace whose person has nowhere to live waits), each upgrade
 * with the words it asks for ([AppViewModel.upgradeWords]): made again when the village, its people, the learner's words
 * or the scenes and packs those come from change.
 */
@Composable
fun rememberBuildOptions(vm: AppViewModel, s: GameState): List<BuildOption> =
    remember(s, vm.villagers.all, vm.content.dashboard, vm.scenes.packs, vm.scenes.all) {
        GameEngine.buildOptions(s, vm.villagers.all) { id -> vm.upgradeWords(s, id) }
    }

/** How far [s] is from its next age, with the learner's words as they stand. */
@Composable
fun rememberAdvanceCheck(vm: AppViewModel, s: GameState): AdvanceCheck =
    remember(s, vm.content.wordsKnown, vm.content.targetLevel, vm.content.wordsRusty) {
        GameEngine.advanceCheck(s, vm.content.wordsKnown, vm.content.targetLevel, vm.content.wordsRusty)
    }

/**
 * The next step of a building's upgrade: what the card's ONE upgrade button says and does (companion/GAME.md, "The
 * building's card"), in the order the upgrade waits for them.
 */
sealed interface UpgradeStep {
    /** At its top level: no button, "⭐ Najvišja stopnja · Top level". */
    data object Top : UpgradeStep

    /** Damaged: the repair comes first (at the card's foot). */
    data object Damaged : UpgradeStep

    /** The next level comes with [age]: "🔒 Najprej: 🛖 Zaselek ›", the ages' sheet. */
    data class Later(val age: Age) : UpgradeStep

    /** The words' packs are still coming to the phone: the count shows "…" rather than jump. */
    data object Loading : UpgradeStep

    /** [missing] of its words still to learn (or polish): "🎯 Nauči se 14 besed · Learn 14 words", their run. */
    data class Words(val missing: Int) : UpgradeStep

    /** The school's course level its next level waits for ([gate]): the school. */
    data class Course(val gate: CourseGate) : UpgradeStep

    /** [missing] more of [res], what the price lacks most: "🪵 Naberi še 30 🪵 · Gather 30 more 🪵". */
    data class Gather(val res: Res, val missing: Int) : UpgradeStep

    /** Everything is there: "⬆️ Nadgradi · Upgrade". */
    data object Ready : UpgradeStep
}

/** What a building's card shows and does, pure: the tests read it without a screen. */
object BuildingCardLogic {
    /**
     * The next step of building [b]'s upgrade ([up], [GameEngine.upgradeOption]): its top level, the damage, the age, the
     * words (once the phone has their packs, [wordsReady]), the school's course, the price, in that order.
     */
    fun step(s: GameState, b: Building, up: UpgradeOption?, wordsReady: Boolean): UpgradeStep {
        if (up == null) return UpgradeStep.Top
        if (b.damaged) return UpgradeStep.Damaged
        val age = Catalog.upgradeAge(up.toLevel)
        if (s.age < age) return UpgradeStep.Later(age)
        if (!wordsReady) return UpgradeStep.Loading
        // as many as its run takes now (at most 8 new and 12 to review): "Nauči se 8 besed" while the chip says 0 / 14
        up.words?.takeIf { !it.done }?.let { return UpgradeStep.Words(BuildingWords.run(it).size.coerceAtLeast(1)) }
        up.course?.takeIf { !it.done }?.let { return UpgradeStep.Course(it) }
        val short = up.cost.mapValues { (r, n) -> n - s.res(r) }.filterValues { it > 0 }.maxByOrNull { it.value }
        return if (short != null) UpgradeStep.Gather(short.key, short.value) else UpgradeStep.Ready
    }

    /** "Stopnja · Level 2 · +2 👥": its level and what it gives now (a damaged one gives nothing: "⚠️ Poškodovano"). */
    fun sub(b: Building): String =
        "${bi("common.level")} ${b.level}" + if (b.damaged) "" else Catalog.effectText(Catalog.spec(b.type).effect.at(b.level)).takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty()

    /** The step's button label, or null for none ([UpgradeStep.Top], [UpgradeStep.Damaged]). */
    fun label(step: UpgradeStep): String? = when (step) {
        UpgradeStep.Top, UpgradeStep.Damaged -> null
        is UpgradeStep.Later -> "🔒 ${bi("buildingCard.firstAge", "age" to step.age.names)} ${step.age.emoji} ›"
        UpgradeStep.Loading -> "🔤 ${bi("buildingCard.wordsChip")} …"
        is UpgradeStep.Words -> "🎯 ${bi("buildingCard.learnWords", "n" to step.missing)}"
        is UpgradeStep.Course -> "📚 ${bi("school.unitsToGo", "n" to step.gate.left, "level" to step.gate.level)} ›"
        is UpgradeStep.Gather -> "${step.res.emoji} ${bi("buildingCard.gatherMore", "n" to step.missing, "res" to step.res.emoji)}"
        UpgradeStep.Ready -> "⬆️ ${bi("villageSheets.upgrade")}"
    }
}

/** What a building's card leads on to; null where it isn't offered (in the building's scene: no going in, moving or replacing). */
class BuildingCardActions(
    val onClose: () -> Unit,
    /** Into one of its scenes. */
    val onScene: ((SceneSpec) -> Unit)? = null,
    /** Someone here now, a request waiting here. */
    val onMarker: (TownMarker) -> Unit = {},
    /** The upgrade or the growing is done (the card closes; the village shows it going up). */
    val onUpgraded: () -> Unit,
    /** Its words' run starts (the card closes; it opens again after the run). */
    val onPractise: () -> Unit,
    /** The ages' sheet: what the next age needs. */
    val onAges: () -> Unit,
    /** Gather what the price lacks. */
    val onGather: (Res) -> Unit,
    /** The school, for the course level its next level waits for. */
    val onSchool: () -> Unit = {},
    /** "↔ Premakni · Move": the plot chooser. */
    val onMove: (() -> Unit)? = null,
    /** "🔁 Nadomesti · Replace": what goes up in its place. */
    val onReplace: (() -> Unit)? = null,
    /** "🔨 Podri · Take down", once confirmed (the card asks first, saying what is lost and what comes back). */
    val onTakeDown: (() -> Unit)? = null,
    /** Growing up is offered here (not in the building's own scene, whose building it would change). */
    val grows: Boolean = true,
)

/**
 * Building [b]'s card (companion/GAME.md, "The building's card"), wherever it opens: over the village (a tap on it, its
 * words' run ended, the school's "Stavba"), and from inside its scene ([sheet]: over the scene, without "Vstopi", moving or
 * replacing). One card, the next step first:
 *
 * 1. its name, its level and what it gives now, ✕;
 * 2. "🚪 Vstopi · Go in" into each of its scenes ([scenes]; none where the culture has no scene for it, and nothing takes
 *    its place), the school's course, who is here now ([here]) and who lives there (a house's household);
 * 3. the upgrade: what the next level gives and needs (the price's chips, "🔤 5 / 14" words, "…" while their packs come),
 *    and ONE button for its next step ([UpgradeStep]); right under it growing up, for a tent or a hut
 *    ([Rebuild.growOption]); folded: which words, and the neighbours' moba;
 * 4. at its foot: the repair when damaged, "↔ Premakni (brezplačno) · Move (free)", folded "🔁 Nadomesti ali podri".
 *
 * The packs of its words are fetched as it opens; until they are on the phone the words' count shows "…" ([UpgradeStep.Loading]).
 */
@Composable
fun BuildingCard(
    vm: AppViewModel, s: GameState, b: Building, check: AdvanceCheck, actions: BuildingCardActions,
    scenes: List<SceneSpec> = emptyList(), here: List<TownMarker> = emptyList(), sheet: Boolean = false, modifier: Modifier = Modifier,
    maxHeight: Dp = Dp.Unspecified,
) {
    val haptic = LocalHapticFeedback.current
    // the packs of its words: on the phone already, or fetched now (offline what's cached stays); the count waits for them
    val packs = remember(b.id, b.type, b.level, vm.scenes.all) { BuildingWords.packsOf(s, b, vm.scenes.all) }
    var fetched by remember(b.id) { mutableStateOf(false) }
    LaunchedEffect(b.id) {
        runCatching { vm.scenes.ensurePacks(packs) }
        fetched = true
    }
    val ready = fetched || packs.all { it in vm.scenes.packs }
    val words = vm.upgradeWords(s, b.id)
    val course = vm.school.gate(s, b.id)
    val up = remember(s, b, words, course) { GameEngine.upgradeOption(s, b.id, words, course) }
    val view = BuildingView(
        s, b, up, BuildingCardLogic.step(s, b, up, ready), check,
        grow = if (actions.grows) Rebuild.growOption(s, b.id) else null,
        repairMoba = if (b.damaged) GameEngine.repairMoba(s, b.id) else null,
        household = household(scenes),
        takeDown = actions.onTakeDown?.let { TakeDownView.of(s, b) },
    )
    val run = BuildingRun(
        upgrade = { moba ->
            if (vm.game.upgrade(b.id, moba)) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                actions.onUpgraded()
            }
        },
        practise = { actions.onPractise(); vm.practiseUpgrade(b.id) },
        repair = { moba -> if (vm.game.repair(b.id, moba)) haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
        grow = {
            if (vm.game.grow(b.id) != null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                actions.onUpgraded()
            }
        },
    )
    val body: @Composable () -> Unit = {
        BuildingCardBody(view, actions, run, scenes, here, close = !sheet) {
            // the classroom under the linden, the school's course (ui/school)
            si.lanisce.lani.ui.school.SchoolOnCard(vm, s, b, onOpen = actions.onClose)
        }
    }
    if (sheet) LaniSheet(onDismissRequest = actions.onClose) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) { body() }
    } else Surface(
        modifier = modifier.widthIn(max = 600.dp).fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 10.dp,
    ) {
        Column(
            Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
        ) { body() }
    }
}

/** What the card shows of building [b] now ([BuildingCard]). */
internal data class BuildingView(
    val s: GameState,
    val b: Building,
    val up: UpgradeOption?,
    val step: UpgradeStep,
    val check: AdvanceCheck,
    val grow: GrowOption? = null,
    val repairMoba: si.lanisce.lani.game.Moba? = null,
    /** " · Vida, Nejc": who lives in it (a house's room's household), else "". */
    val household: String = "",
    /** What taking it down means, when it can be ([TakeDownView.of]). */
    val takeDown: TakeDownView? = null,
)

/**
 * What taking a building down means, for its confirmation: what comes back ([back], half its materials), the beds left
 * for the people who live here ([beds], [people]); [short]: too few, it can't be taken down until there is room.
 */
internal data class TakeDownView(val back: Map<Res, Int>, val beds: Int, val people: Int, val short: Boolean) {
    companion object {
        fun of(s: GameState, b: Building): TakeDownView {
            val people = maxOf(s.villagers, s.residents.size)
            val beds = GameEngine.attributes(s.copy(buildings = s.buildings.filter { it.id != b.id })).populationCap
            return TakeDownView(Rebuild.salvage(b), beds, people, Rebuild.bedsShort(s, b) != null)
        }
    }
}

/** What the card's buttons do with the village ([BuildingCard]). */
internal class BuildingRun(
    val upgrade: (withMoba: Boolean) -> Unit,
    val practise: () -> Unit,
    val repair: (withMoba: Boolean) -> Unit,
    val grow: () -> Unit,
)

/** The card's test tag (the Compose tests find it by it). */
internal const val BUILDING_CARD_TAG = "building-card"

/** The card's content ([BuildingCard]); [close]: a ✕ in its header (not in the scene's sheet, which is dragged away). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BuildingCardBody(
    v: BuildingView, actions: BuildingCardActions, run: BuildingRun, scenes: List<SceneSpec>, here: List<TownMarker>, close: Boolean,
    extra: @Composable () -> Unit = {},
) {
    val b = v.b
    val s = v.s
    Column(Modifier.fillMaxWidth().testTag(BUILDING_CARD_TAG), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. its name, its level and what it gives now
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBadge(b.type.emoji, MaterialTheme.colorScheme.primaryContainer, size = 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(b.type.label(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(BuildingCardLogic.sub(b), style = MaterialTheme.typography.bodySmall, color = if (b.damaged) MaterialTheme.colorScheme.onSurfaceVariant else AlpineGreen)
                if (b.damaged) Text("⚠️ ${bi("villageSheets.damaged")}", style = MaterialTheme.typography.bodySmall, color = TriglavRed, fontWeight = FontWeight.SemiBold)
            }
            if (close) Surface(onClick = actions.onClose, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { EmojiLabel("✕", Labels.CLOSE) }
            }
        }
        // 2. going in, who's there and who lives there
        actions.onScene?.let { go ->
            for (sc in scenes) FilledTonalButton(onClick = { go(sc) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.small) {
                Text("🚪 ${bi("placeCard.go")}: ${sc.emoji} ${si.lanisce.lani.game.Hearths.sceneTitle(sc)} ›", fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        extra()
        if (v.household.isNotEmpty()) Text(
            "🏠 ${bi("buildingCard.livesHere", "names" to v.household.removePrefix(" · "))}",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HereNow(here, actions.onMarker, rows = 2)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        // 3. the upgrade: one button for its next step
        val up = v.up
        when {
            v.step == UpgradeStep.Top -> Text("⭐ ${bi("villageSheets.topLevel")}", style = MaterialTheme.typography.bodyMedium, color = XpGold, fontWeight = FontWeight.SemiBold)
            b.damaged -> Surface(color = TriglavRed.copy(alpha = 0.10f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "⚠️ ${bi("villageSheets.givesNoEffectUntil")}", style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            // growing up right under the upgrade's button, before what is folded: a hut's "Prezidaj" is one of its main ways
            up != null -> UpgradeSection(v, up, actions, run) { v.grow?.let { g -> GrowRow(s, b, g, run.grow) } }
        }
        // 4. growing up: a tent into a hut, a hut into a house
        if (up == null || b.damaged || v.step == UpgradeStep.Top) v.grow?.let { g -> GrowRow(s, b, g, run.grow) }
        // 5. at its foot: the repair, moving, replacing
        if (b.damaged) {
            val cost = GameEngine.repairCost(s, b.id)
            CostChips(cost) { s.res(it) }
            val canPay = cost.all { (r, n) -> s.res(r) >= n }
            BigButton(if (canPay) "🛠️ ${bi("villageSheets.repair")}" else "🛠️ ${bi("villageSheets.notEnoughYet")}", onClick = { run.repair(false) }, enabled = canPay, color = TriglavRed)
            v.repairMoba?.let { m ->
                TextButton(onClick = { run.repair(true) }, enabled = m.affordable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("🤝 ${bi("villageSheets.repairMobaHelp", "help" to m.help)}")
                }
            }
        }
        actions.onMove?.let { move ->
            OutlinedButton(onClick = move, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.small) {
                Text("↔ ${bi("buildingCard.moveFree")}", maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        // replacing and taking down: rare, and they can't be undone, so they are folded
        if (actions.onReplace != null || v.takeDown != null) Fold("🔁 ${bi("buildingCard.replaceOrTakeDown")}", "") {
            var asking by remember { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.onReplace?.let { replace ->
                    OutlinedButton(onClick = replace, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.small) {
                        Text("🔁 ${bi("buildingCard.replace")} ›", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                v.takeDown?.let { t ->
                    OutlinedButton(onClick = { asking = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.small) {
                        Text("🔨 ${bi("buildingCard.takeDown")}", color = TriglavRed, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (asking) TakeDownDialog(b, t, onConfirm = { asking = false; actions.onTakeDown?.invoke() }, onDismiss = { asking = false })
                }
            }
        }
    }
}

/**
 * The next level: "⬆️ Za stopnjo 3: +4 👥 · For level 3: +4 👥", what it needs as chips (the price, red where short; the
 * words "🔤 5 / 14", "…" while they come; the course), ONE button for the next step, and folded: which words, the moba.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpgradeSection(v: BuildingView, up: UpgradeOption, actions: BuildingCardActions, run: BuildingRun, then: @Composable () -> Unit = {}) {
    val s = v.s
    val step = v.step
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "⬆️ ${bi("buildingCard.forLevel", "level" to up.toLevel)}: ${up.effect}",
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CostChips(up.cost) { s.res(it) }
            // the words: "…" until their packs are on the phone, so the count doesn't jump (2 of 2, then 0 of 14)
            val w = up.words
            when {
                step == UpgradeStep.Loading -> NeedChip("🔤 …", false, bi("buildingCard.wordsChip"))
                w != null -> NeedChip("🔤 ${minOf(w.have, w.need)} / ${w.need}", w.done, bi("buildingCard.wordsChip"))
            }
            up.course?.let { g -> NeedChip("📚 ${g.level}", g.done, bi("school.gateTitle", "toLevel" to g.toLevel, "level" to g.level)) }
        }
        val label = BuildingCardLogic.label(step)
        if (label != null) {
            val (color, onClick) = when (step) {
                is UpgradeStep.Later -> MaterialTheme.colorScheme.secondary to actions.onAges
                UpgradeStep.Loading -> AlpineGreen to {}
                is UpgradeStep.Words -> AlpineGreen to run.practise
                is UpgradeStep.Course -> AlpineGreen to actions.onSchool
                is UpgradeStep.Gather -> AlpineGreen to { actions.onGather(step.res) }
                else -> XpGold to { run.upgrade(false) }
            }
            BigButton(label, onClick = onClick, enabled = step != UpgradeStep.Loading, color = color)
        }
        then()
        // folded: which words exactly (and their run), the neighbours' help
        up.words?.takeIf { !it.done && step != UpgradeStep.Loading }?.let { w -> Fold(bi("buildingCard.whichWords"), wordsPeek(w)) { UpgradeWordsList(w) } }
        up.moba?.let { m ->
            Fold("🤝 ${bi("villageSheets.neighboursHelp")}", "−${m.covers.entries.joinToString(" ") { (r, n) -> "$n ${r.emoji}" }} · ${m.help} 🤝") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(ChestLogic.mobaText(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // the neighbours bring timber and stone, never the words
                    BigButton("🤝 ${bi("villageSheets.upgradeMobaHelp", "help" to m.help)}", onClick = { run.upgrade(true) }, enabled = up.mobaAvailable, color = XpGold)
                }
            }
        }
    }
}

/** A requirement as a chip: green with ✓ when met. */
@Composable
private fun NeedChip(text: String, done: Boolean, description: String) {
    Surface(
        color = (if (done) AlpineGreen else XpGold).copy(alpha = 0.16f), shape = RoundedCornerShape(50),
        modifier = Modifier.semantics { contentDescription = "$description: $text" },
    ) {
        Text(
            text + if (done) " ✓" else "", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** "svetilka, spalna vreča … +12": the first missing words, as the fold's peek. */
internal fun wordsPeek(w: UpgradeWords): String {
    val missing = w.missing
    val shown = missing.take(2).joinToString(", ") { it.word }
    return if (missing.size > 2) "$shown … +${missing.size - 2}" else shown
}

/**
 * A folded part of the card: "▸ [title] ([peek])", a tap opens it ("▾"), again closes it. TalkBack reads it as a button
 * that is folded or open.
 */
@Composable
internal fun Fold(title: String, peek: String, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column {
        Surface(
            onClick = { open = !open }, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).semantics {
                role = Role.Button
                stateDescription = bi(if (open) "buildingCard.open" else "buildingCard.folded")
            },
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (open) "▾" else "▸", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(20.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (peek.isNotEmpty()) Text(
                    "  $peek", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
            }
        }
        AnimatedVisibility(open) { Box(Modifier.padding(top = 8.dp)) { content() } }
    }
}

/**
 * Growing up: "🛖 Postane: Koča · Becomes: Hut" for a tent, "🏠 Prezidaj v: Hiša · Rebuild as: House" for a hut, what it
 * gives then (its level kept), its price (what the bigger one costs more); a tap grows it (or, short of something, says
 * what).
 */
@Composable
private fun GrowRow(s: GameState, b: Building, g: GrowOption, onGrow: () -> Unit) {
    val from = b.type.name.lowercase()
    Surface(color = AlpineGreen.copy(alpha = 0.08f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${g.to.emoji} ${bi("buildingCard.grow", "from" to from, "building" to g.to.names)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            // its level kept, what it gives then
            Text("${bi("common.level")} ${b.level} · ${g.effect}", style = MaterialTheme.typography.bodySmall, color = AlpineGreen)
            CostChips(g.cost) { s.res(it) }
            OutlinedButton(onClick = onGrow, enabled = g.available, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.small) {
                Text("${g.to.emoji} ${bi("buildingCard.growNow", "from" to from)}")
            }
            g.reason?.let { Text("🔒 $it", style = MaterialTheme.typography.labelSmall, color = FlameOrange) }
        }
    }
}

/**
 * "🔨 Podreš: Koča? · Take down: Hut?": what is lost (the building and its level), what comes back (half its materials), and
 * that everyone who lives here stays, with the beds left for them; too few beds: why it can't be yet, nothing to confirm.
 */
@Composable
private fun TakeDownDialog(b: Building, t: TakeDownView, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔨 ${bi("takeDown.title", "building" to b.type.names)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(bi("takeDown.lost", "level" to b.level), style = MaterialTheme.typography.bodyMedium)
                if (t.back.isNotEmpty()) Text("↩️ ${bi("takeDown.back", "res" to costText(t.back))}", style = MaterialTheme.typography.bodyMedium)
                if (t.short) Text("🛏️ ${bi("rebuild.noBeds", "beds" to t.beds, "people" to t.people)}", style = MaterialTheme.typography.bodyMedium, color = TriglavRed)
                else if (Catalog.spec(b.type).effect.pop > 0) Text("🛏️ ${bi("takeDown.people", "beds" to t.beds, "people" to t.people)}", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            if (!t.short) TextButton(onClick = onConfirm) { Text("🔨 ${bi("buildingCard.takeDown")}", color = TriglavRed) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(bi("takeDown.keep")) } },
    )
}
