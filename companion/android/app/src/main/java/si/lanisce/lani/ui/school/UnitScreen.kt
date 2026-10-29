package si.lanisce.lani.ui.school

import si.lanisce.lani.ui.backTo
import si.lanisce.lani.ui.backLabel
import si.lanisce.lani.ui.BackButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.SheetKind
import si.lanisce.lani.data.CourseUnit
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.Prints
import si.lanisce.lani.data.Worksheet
import si.lanisce.lani.game.School
import si.lanisce.lani.game.SheetBest
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.GradientHeader
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.XpGold

/** "8/10" of a sheet's best try (an almost counting half). */
internal fun bestText(b: SheetBest): String {
    val s = b.score
    val whole = s.toInt()
    return (if (s - whole >= 0.5) "$whole½" else "$whole") + "/${b.of}"
}

/**
 * A unit of the course: what it teaches and where the learner is in it: its intro, its worksheets, its test (and "⏩ I
 * know it: straight to the test"), the tutor's homework; the next step one tap away; its pages in the book and the
 * tutor, one tap away too. Back leads where it was opened (the school, Today, Napredek, a notification: Home); opened
 * elsewhere than the school, "🏫 Šola ›" leads to its other units.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnitScreen(vm: AppViewModel, id: String) {
    val unit = vm.school.unit(id) ?: run {
        // the course still on its way (the app started again here): a moment; the unit gone (the course changed): back
        if (vm.school.course == null) Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            BackButton(vm)
            CircularProgressIndicator(Modifier.padding(24.dp))
        } else androidx.compose.runtime.LaunchedEffect(id) { vm.back() }
        return
    }
    val s = vm.game.state
    val p = s?.let { School.progress(it, unit.language, unit.id) }
    val step = School.nextStep(unit, p)
    val base = L10n.pair.base.code
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            GradientHeader(
                "${unit.emoji} ${unit.titleShown(L10n.pair)}",
                listOfNotNull(unit.level, unit.goal?.let { Grammar.text(it, base) }).joinToString(" · "),
                vm.backLabel,
                onBack = vm::back,
            )
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TeacherSays(vm, vm.school.teacher(), if (p?.done != null) "school.unitDoneSays" else "school.today", "unit" to si.lanisce.lani.l10n.Localized { l -> Grammar.text(unit.title, l.code) })
                when (step) {
                    School.Step.Intro -> BigButton("▶️ ${bi("school.continueStep", "step" to bi("school.intro"))}", color = XpGold, onClick = { vm.startIntro(unit.id) })
                    is School.Step.Sheet -> unit.sheet(step.id)?.let { ws ->
                        BigButton("▶️ ${bi("school.continueStep", "step" to ws.titleShown(L10n.pair))}", color = XpGold, onClick = { vm.startSheet(unit.id, SheetKind.WORKSHEET, ws.id) })
                    }
                    School.Step.Test -> BigButton("🎓 ${bi("school.continueStep", "step" to bi("school.test"))}", color = XpGold, onClick = { vm.startSheet(unit.id, SheetKind.TEST, "test") })
                    School.Step.Done -> Text("✅ ${bi("school.unitIsDone")}", style = MaterialTheme.typography.titleMedium, color = AlpineGreen)
                }
                // knows it already: the test straight away; passed, the unit is done (tested out)
                if (step != School.Step.Done && step != School.Step.Test) OutlinedButton(
                    onClick = { vm.startSheet(unit.id, SheetKind.TEST, "test") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("⏩ ${bi("school.testOut")}") }
                // opened from Today, Napredek or a notification: the school's other units one tap away
                if (vm.backTo != si.lanisce.lani.Screen.School) TextButton(onClick = vm::openSchool, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("🏫 ${bi("school.title")} ›")
                }
            }
        }
        unit.intro?.let { intro ->
            item { SectionTitle("📖 ${bi("school.intro")}") }
            item {
                StepCard(
                    "📖", bi("school.intro"),
                    when {
                        p?.introDone == true -> "✅ ${bi("school.done")}"
                        (p?.intro ?: 0) > 0 -> bi("school.introSteps", "done" to (p?.intro ?: 0), "of" to intro.steps.size)
                        else -> bi("school.introAbout", "n" to intro.steps.size)
                    },
                    done = p?.introDone == true,
                ) { vm.startIntro(unit.id) }
            }
        }
        // the unit stevila's own practice (companion/README.md, "Numbers"): opened by its intro, closed before it, one tap
        // away either way (closed, it says what opens it and leads to the intro)
        if (unit.id == si.lanisce.lani.data.NumbersPractice.UNIT && unit.language == si.lanisce.lani.data.NumbersPractice.LANGUAGE) {
            item { SectionTitle("🔢 ${bi("numbers.title")}") }
            item {
                val open = si.lanisce.lani.data.NumbersPractice.open(s)
                val np = s?.numbers
                val due = si.lanisce.lani.data.NumbersPractice.due(np, java.time.LocalDate.now()).size
                StepCard(
                    "🔢", bi("numbers.title"),
                    when {
                        !open -> "🔒 ${bi("numbers.lockedShort")}"
                        due > 0 -> "🔁 ${bi("numbers.dueAgain", "n" to due)}"
                        else -> si.lanisce.lani.data.NumbersPractice.ranges(np, true).joinToString(" · ") { it.label }
                    },
                    done = false,
                ) { vm.openNumbers() }
            }
        }
        // a unit about the cases: the case trainer, a session on what the unit teaches (companion/README.md "Case trainer")
        if (unit.id in si.lanisce.lani.data.CaseTrainer.CASE_UNITS && unit.language == si.lanisce.lani.data.CaseTrainer.LANGUAGE && vm.caseTrainer.available) {
            item { SectionTitle("🧩 ${bi("cases.title")}") }
            item {
                val focus = si.lanisce.lani.data.CaseTrainer.focusOfUnit(unit.id)
                val open = vm.caseTrainer.open
                StepCard(
                    "🧩", bi("cases.title"),
                    when {
                        !open -> "🔒 ${bi("cases.lockedShort")}"
                        focus != null -> bi("cases.practiseFocus", "focus" to si.lanisce.lani.ui.cases.focusTitle(vm, focus))
                        else -> bi("cases.practiseAll")
                    },
                    done = false,
                ) { if (open) vm.startCases(focus) else vm.openCases(focus) }
            }
        }
        item { SectionTitle("📝 ${bi("school.worksheets")}") }
        for ((i, ws) in unit.worksheets.withIndex()) item("ws-${ws.id}") {
            SheetCard(vm, unit, ws, i + 1, p?.sheets?.get(ws.id), SheetKind.WORKSHEET)
        }
        item { SectionTitle("🎓 ${bi("school.test")}") }
        item {
            val t = p?.test
            StepCard(
                "🎓", bi("school.test"),
                listOfNotNull(
                    bi("school.testPass", "pass" to unit.test.pass),
                    t?.let { bi("school.best", "score" to bestText(it)) },
                    t?.let { bi("school.tries", "n" to it.tries) },
                ).joinToString(" · "),
                done = p?.done != null,
            ) { vm.startSheet(unit.id, SheetKind.TEST, "test") }
        }
        if (unit.homework.isNotEmpty()) {
            item { SectionTitle("🧑‍🏫 ${bi("school.homework")}") }
            for ((i, ws) in unit.homework.withIndex()) item("hw-${ws.id}") {
                SheetCard(vm, unit, ws, i + 1, p?.sheets?.get(ws.id), SheetKind.HOMEWORK)
            }
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (unit.grammar.isNotEmpty()) {
                    Text("📖 ${bi("school.inTheBook")}", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (g in unit.grammar) vm.grammar.page(g)?.let { page ->
                            AssistChip(onClick = { vm.grammar.show(g) }, label = { Text("${page.emoji} ${page.target}") })
                        }
                    }
                }
                OutlinedButton(onClick = { vm.school.ask(unit) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("💬 ${bi("common.askTutor")}") }
                // its sheet on paper (companion/COURSE.md, "Printed sheets"); a unit that starts from a page with an overview
                // (skloni: the six cases) prints that too
                PrintButton(vm, vm.school.course?.let { Prints.sheet(it, unit.id) }, bi("school.printSheet"))
                PrintButton(vm, Prints.overview(unit.language, unit.grammar.firstOrNull()), bi("school.printOverview"))
                if (unit.machineWritten) Text("🤖 ${bi("school.machineWritten")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp).semantics { heading() })
}

/** One step of a unit: its picture, name, where it stands; ✓ once done. */
@Composable
private fun StepCard(emoji: String, title: String, line: String, done: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = if (done) AlpineGreen.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (done) "✅" else "›", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** A worksheet (or homework) of the unit: its number and title, its best try. */
@Composable
private fun SheetCard(vm: AppViewModel, unit: CourseUnit, ws: Worksheet, n: Int, best: SheetBest?, kind: SheetKind) {
    StepCard(
        if (kind == SheetKind.HOMEWORK) "🧑‍🏫" else "📝",
        "$n. ${ws.titleShown(L10n.pair)}",
        best?.let { bi("school.best", "score" to bestText(it)) } ?: bi("school.exercises", "n" to ws.exercises.size),
        done = best != null,
    ) { vm.startSheet(unit.id, kind, ws.id) }
}
