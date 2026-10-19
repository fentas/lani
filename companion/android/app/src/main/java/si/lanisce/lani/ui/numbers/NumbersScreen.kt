package si.lanisce.lani.ui.numbers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.Screen
import si.lanisce.lani.app.ExerciseSource
import si.lanisce.lani.app.NumbersDone
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.NumberMode
import si.lanisce.lani.data.NumberRange
import si.lanisce.lani.data.NumbersPractice
import si.lanisce.lani.data.ScreenClock
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.ExerciseRun
import si.lanisce.lani.ui.Finish
import si.lanisce.lani.ui.GradientHeader
import si.lanisce.lani.ui.Markdown
import si.lanisce.lani.ui.game.RewardSummary
import si.lanisce.lani.ui.minutesSince
import si.lanisce.lani.ui.stage.Run
import si.lanisce.lani.ui.stage.rememberStage
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.XpGold
import java.time.LocalDate

/**
 * "🔢 Števila · Numbers" (companion/README.md, "Numbers"): closed, what opens it (the unit stevila's intro) with the way
 * there one tap away ("📖 Odpri uvod · Open the intro"; before the class, the school); open, "▶️ Začni · Start", the numbers
 * missed due again, the ranges (the newest with how near the next is: 8 of the last 10 right opens it), the ways of
 * asking and what opens those not on yet, the rule's tip, and that its Slovene is machine-written.
 */
@Composable
fun NumbersScreen(vm: AppViewModel, at: Screen.Numbers) {
    val leave = { vm.leaveNumbers(at.fromUnit, at.fromVillage) }
    BackHandler(onBack = leave)
    val s = vm.game.state
    val lock = NumbersPractice.lock(s)
    val unit = vm.school.unit(NumbersPractice.UNIT)
    val unitTitle = unit?.titleShown(L10n.pair) ?: NumbersPractice.UNIT
    val p = s?.numbers
    val introDone = lock == null
    val open = NumbersPractice.ranges(p, introDone)
    val passed = NumbersPractice.passed(p, introDone)
    val due = NumbersPractice.due(p, LocalDate.now()).size
    val back = if (at.fromUnit && unit != null) "← $unitTitle" else "← ${bi("common.home")}"
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { GradientHeader("🔢 ${bi("numbers.title")}", bi("numbers.about"), back, onBack = leave) }
        if (lock != null) {
            item { Locked(vm, lock, unitNamed(unit, NumbersPractice.UNIT)) }
        } else {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton("▶️ ${bi("numbers.start")}", onClick = { vm.startNumbers(at.fromUnit, at.fromVillage) }, color = AlpineGreen)
                    if (due > 0) Text("🔁 ${bi("numbers.dueAgain", "n" to due)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            item { SectionTitle("🪜 ${bi("numbers.ranges")}") }
            items(NumberRange.entries, key = { it.id }) { r -> RangeRow(r, p, open, passed) }
            item { SectionTitle("🎛️ ${bi("numbers.modes")}") }
            item { Modes(vm, passed.isNotEmpty()) }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Markdown("💡 ${bi("numbers.tip")}")
                Text("🤖 ${bi("school.machineWritten")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A unit's title in each half of a label (its own language's: "»Ena ovca, dve ovci, pet ovc« · “Numbers: one sheep …”"), else its [id]. */
private fun unitNamed(unit: si.lanisce.lani.data.CourseUnit?, id: String) =
    si.lanisce.lani.l10n.Localized { l -> unit?.let { si.lanisce.lani.data.Grammar.text(it.title, l.code) } ?: id }

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp).semantics { heading() })
}

/**
 * Closed: why (the unit's intro; before the hamlet, the class), and the one tap there: the intro, or the school (which says
 * what begins the class and leads there). Under it, what the module asks, greyed.
 */
@Composable
private fun Locked(vm: AppViewModel, lock: NumbersPractice.Lock, unitTitle: si.lanisce.lani.l10n.Localized) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔒", style = MaterialTheme.typography.displaySmall, modifier = Modifier.clearAndSetSemantics {})
            Text(
                bi(if (lock == NumbersPractice.Lock.CLASS) "numbers.lockedClass" else "numbers.lockedIntro", "unit" to unitTitle),
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            )
            when (lock) {
                NumbersPractice.Lock.INTRO -> BigButton("📖 ${bi("numbers.openIntro")}", onClick = { vm.startIntro(NumbersPractice.UNIT) }, color = XpGold)
                NumbersPractice.Lock.CLASS -> BigButton("🏫 ${bi("numbers.toSchool")}", onClick = { vm.openSchool(fromVillage = false) }, color = XpGold)
            }
        }
    }
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (m in listOf(NumberMode.HEAR, NumberMode.READ, NumberMode.WRITE, NumberMode.SAY, NumberMode.DATE, NumberMode.TIME_HEAR)) {
            Text("${m.emoji}  ${bi(m.title)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

/** A range of the ladder: ✅ open, ▶️ the newest (its last answers, how near the next is), 🔒 later (after which). */
@Composable
private fun RangeRow(r: NumberRange, p: si.lanisce.lani.data.NumbersProgress?, open: List<NumberRange>, passed: List<NumberRange>) {
    val isOpen = r in open
    val newest = open.lastOrNull() == r
    val (right, of) = NumbersPractice.score(p, r)
    val line = when {
        !isOpen -> bi("numbers.rangeLocked", "before" to (NumberRange.entries.getOrNull(r.ordinal - 1)?.label ?: ""))
        newest && r.next != null -> listOfNotNull(
            if (of > 0) bi("numbers.rangeScore", "right" to right, "of" to of) else null,
            bi("numbers.rangeOpens", "pass" to NumbersPractice.PASS, "window" to NumbersPractice.WINDOW, "next" to r.next!!.label),
        ).joinToString(" · ")
        newest -> if (r in passed) bi("numbers.rangeLast", "pass" to NumbersPractice.PASS, "window" to NumbersPractice.WINDOW) else listOfNotNull(
            if (of > 0) bi("numbers.rangeScore", "right" to right, "of" to of) else null,
            bi("numbers.allOpen"),
        ).joinToString(" · ")
        of > 0 -> bi("numbers.rangeScore", "right" to right, "of" to of)
        else -> bi("numbers.rangeHave")
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                newest -> XpGold.copy(alpha = 0.14f)
                isOpen -> AlpineGreen.copy(alpha = 0.10f)
                else -> MaterialTheme.colorScheme.surface
            },
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).semantics(mergeDescendants = true) {},
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (!isOpen) "🔒" else if (newest) "▶️" else "✅", style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics {})
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    r.label, style = MaterialTheme.typography.titleMedium, fontWeight = if (newest) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isOpen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (newest && r.next != null) LinearProgressIndicator(
                    progress = { (right.toFloat() / NumbersPractice.PASS).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                    color = XpGold, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
                )
            }
        }
    }
}

/** The ways of asking: each on (✓), or what turns it on, with the way there where it is a unit's intro (the clock's). */
@Composable
private fun Modes(vm: AppViewModel, hasRange: Boolean) {
    val on = vm.numbers.modes()
    val clockUnit = vm.school.unit(NumbersPractice.TIME_UNIT)
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (m in NumberMode.entries) {
            val isOn = m in on && (m != NumberMode.SAY || hasRange)
            val needs = when {
                isOn -> null
                m == NumberMode.HEAR -> bi("numbers.needs.voice")
                m == NumberMode.SAY -> bi("numbers.needs.listen")
                m == NumberMode.TIME_HEAR && NumbersPractice.TIME_PAGE in vm.numbers.introduced() -> bi("numbers.needs.voice")
                m == NumberMode.TIME_HEAR || m == NumberMode.TIME_WRITE -> bi("numbers.needs.unit", "unit" to unitNamed(clockUnit, NumbersPractice.TIME_UNIT))
                else -> bi("numbers.needs.unit", "unit" to unitNamed(vm.school.unit(NumbersPractice.UNIT), NumbersPractice.UNIT))
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                Text(m.emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics {})
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        bi(m.title), style = MaterialTheme.typography.bodyMedium, fontWeight = if (isOn) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isOn) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    needs?.let { Text("🔒 $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (isOn) Text("✓", style = MaterialTheme.typography.titleMedium, color = AlpineGreen)
                // the clock's unit one tap away (its page: the intro there)
                else if ((m == NumberMode.TIME_WRITE) && clockUnit != null && NumbersPractice.TIME_PAGE !in vm.numbers.introduced()) {
                    TextButton(onClick = { vm.openUnit(NumbersPractice.TIME_UNIT) }) { Text("📖 ${inTarget("numbers.openIntro")}") }
                }
            }
        }
    }
}

/** A run of the numbers: its tasks one at a time (data/NumbersPractice.kt), then how it went, what opened, what comes back. */
@Composable
fun NumbersRunScreen(vm: AppViewModel, at: Screen.NumbersRun) {
    val exercises = remember(at) { at.tasks.map(NumbersPractice::exercise) }
    val started = remember { ScreenClock.app.now() }
    val stage = rememberStage(vm, Run.Review)
    var done by remember { mutableStateOf<NumbersDone?>(null) }
    val d = done
    if (d == null) {
        ExerciseRun(
            vm, "🔢 ${bi("numbers.title")}", exercises, moduleId = null, onExit = vm::leaveRun, stage = stage,
            source = { i -> at.tasks.getOrNull(i)?.let { ExerciseSource.numbers(it.mode.wire, it.n, it.month, i) } },
            pages = NumbersPractice.PAGES,
        ) { outs -> done = vm.numbers.finish(at.tasks, exercises, outs, minutesSince(started)) }
        return
    }
    val right = d.verdicts.count { it == Verdict.CORRECT }
    val almost = d.verdicts.count { it == Verdict.ALMOST }
    Finish(
        title = if (right + almost >= d.verdicts.size * 0.7) "${inTarget("playerScreen.bravo")} 🎉" else "${inTarget("playerScreen.goodWork")} 💪",
        lines = listOf("✅ $right   🟡 $almost   ❌ ${d.verdicts.size - right - almost}", "+${right * 10 + almost * 5 + 25} XP"),
        onDone = vm::leaveRun,
        stage = stage,
    ) {
        RewardSummary(vm.game.lastReward, onVillage = vm::openVillage)
        d.opened?.let {
            Spacer(Modifier.height(8.dp))
            Text("🔓 ${bi("numbers.opened", "range" to it.label)}", style = MaterialTheme.typography.titleMedium, color = AlpineGreen, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        if (d.missed.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("🔁 ${bi("numbers.comingBack")}: ${d.missed.joinToString(" · ")}", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { vm.startNumbers(at.fromUnit, at.fromVillage, again = true) }) { Text("▶️ ${bi("numbers.startAgain")}") }
    }
}
