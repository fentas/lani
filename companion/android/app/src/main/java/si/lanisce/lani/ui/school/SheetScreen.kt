package si.lanisce.lani.ui.school

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.ExerciseSource
import si.lanisce.lani.app.SheetAnswer
import si.lanisce.lani.app.SheetDone
import si.lanisce.lani.app.SheetKind
import si.lanisce.lani.data.CourseUnit
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.ScreenClock
import si.lanisce.lani.data.Sheets
import si.lanisce.lani.game.School
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.ExerciseRun
import si.lanisce.lani.ui.Finish
import si.lanisce.lani.ui.game.RewardSummary
import si.lanisce.lani.ui.game.giftWords
import si.lanisce.lani.ui.minutesSince
import si.lanisce.lani.ui.stage.Run
import si.lanisce.lani.ui.stage.rememberStage
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.XpGold

/**
 * A sheet of unit [unitId] ([kind]: a worksheet, the test, homework): its own intro first when it has one (homework's),
 * then its exercises one by one, the teacher on the stage, help one tap away all along (📋 the cheat sheet, the unit's
 * cards first; its words tappable; 💡 per item; 💬 the tutor); then what it brought, and the next step.
 */
@Composable
fun SheetScreen(vm: AppViewModel, unitId: String, kind: SheetKind, sheetId: String, fromVillage: Boolean) {
    val unit = vm.school.unit(unitId)
    val ws = if (kind == SheetKind.TEST) null else unit?.sheet(sheetId)
    if (unit == null || (kind != SheetKind.TEST && ws == null)) {
        LaunchedEffect(unitId) { vm.leaveRun() }
        return
    }
    val exercises = if (kind == SheetKind.TEST) unit.test.exercises else ws!!.exercises
    val title = if (kind == SheetKind.TEST) "🎓 ${bi("school.test")}: ${unit.titleShown(L10n.pair)}" else "📝 ${ws!!.titleShown(L10n.pair)}"
    val teacher = remember { vm.school.teacher() }
    val stage = rememberStage(vm, remember { Run.Lesson(teacher?.name, teacher?.emoji, indoors = vm.game.state?.let(School::indoors) == true) })
    var started by remember { mutableLongStateOf(ScreenClock.app.now()) }
    var result by remember { mutableStateOf<SheetDone?>(null) }
    // a sheet with an intro of its own (the tutor's homework): its steps first
    var introStep by remember { mutableIntStateOf(if (ws?.intro != null) 0 else -1) }
    val intro = ws?.intro
    // the unit's pages met (an intro skipped, a sheet opened first): their cards on the cheat sheet
    LaunchedEffect(Unit) { vm.cheat.met(unit.grammar) }

    val done = result
    when {
        done != null -> SheetFinish(vm, unit, kind, sheetId, done, title)
        intro != null && introStep in intro.steps.indices -> Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            // a step of its own each (its question, its reading)
            key(introStep) { IntroStepView(vm, unit, intro, introStep, Modifier.weight(1f)) { introStep++ } }
        }
        else -> ExerciseRun(
            vm, title, exercises, moduleId = null, onExit = { vm.leaveRun() }, stage = stage,
            source = { i -> ExerciseSource.course(unit.language, unit.id, if (kind == SheetKind.TEST) "test" else sheetId, i) },
            // an exercise that names no rule counts on the unit's first page
            rules = exercises.map { unit.grammar.firstOrNull() },
            // its cheat sheet (📋) has the unit's cards right after the exercise's own
            pages = unit.grammar,
        ) { outs ->
            val answers = outs.map { o -> SheetAnswer(o.verdict, o.paid, o.answer, o.correct, o.sheet, o.lookedUp) }
            result = vm.school.finish(unit, kind, sheetId, exercises, answers, minutesSince(started))
                ?: SheetDone(null, answers.flatMap { a -> if (a.items.isNotEmpty()) a.items.map { it.paid } else listOf(a.paid) }, unitDone = false, passed = false)
        }
    }
}

/** What a sheet brought: how it went, the test passed or not (and the unit done), the village's pay, the next step. */
@Composable
private fun SheetFinish(vm: AppViewModel, unit: CourseUnit, kind: SheetKind, sheetId: String, done: SheetDone, title: String) {
    val right = done.items.count { it == Verdict.CORRECT }
    val almost = done.items.count { it == Verdict.ALMOST }
    val heading = when {
        kind == SheetKind.TEST && done.unitDone -> "🎓 ${inTarget("school.unitDoneTitle")} 🎉"
        kind == SheetKind.TEST && done.passed -> "✅ ${inTarget("school.testPassed")}"
        kind == SheetKind.TEST -> "💪 ${inTarget("school.testNotYet")}"
        Sheets.verdict(done.items) != Verdict.WRONG -> "${inTarget("playerScreen.bravo")} 🎉"
        else -> "${inTarget("playerScreen.goodWork")} 💪"
    }
    val s = vm.game.state
    val course = vm.school.course
    val next = if (s != null && course != null) School.next(s, course) else null
    // the test passed: the teacher cheers, claps, then smiles (still with the system's animations off)
    val teacher = remember { vm.school.teacher() }
    val cheers = kind == SheetKind.TEST && done.passed && teacher != null
    Finish(
        title = heading,
        hero = if (cheers) ({ TeacherCheer(teacher!!) }) else null,
        lines = listOfNotNull(
            title,
            "✅ $right   🟡 $almost   ❌ ${done.items.size - right - almost}",
            if (kind == SheetKind.TEST) bi("school.scoreOfPass", "score" to Sheets.scoreText(done.items), "pass" to unit.test.pass) else null,
        ),
        onDone = { vm.leaveRun() },
    ) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RewardSummary(done.reward, onVillage = vm::openVillage, words = giftWords(vm))
            if (kind == SheetKind.TEST && !done.passed) Text(bi("school.testAgain"), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            // the unit done: the next one, suggested
            if (done.unitDone && next != null && next.id != unit.id) BigButton("★ ${bi("school.nextUnit", "unit" to next.titleShown(L10n.pair))}", color = XpGold, onClick = {
                vm.openUnit(next.id)
            })
            // the sheet just done goes with it
            OutlinedButton(onClick = { vm.school.ask(unit, if (kind == SheetKind.TEST) "test" else sheetId) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("💬 ${bi("common.askTutor")}") }
            Text(bi("playerScreen.tutorLookingAtResults"), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The teacher cheering a passed test: arms up and a jump, [CHEER_MS] long, then a smile; held still with the animations off. */
@Composable
private fun TeacherCheer(t: si.lanisce.lani.game.villagers.Villager) {
    val reduced = si.lanisce.lani.ui.stage.rememberReducedMotion()
    var cheering by remember { mutableStateOf(!reduced) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(CHEER_MS)
        cheering = false
    }
    TeacherPortrait(t, if (cheering) si.lanisce.lani.game.scene.Pose.CHEER else si.lanisce.lani.game.scene.Pose.HAPPY, 118.dp)
}

/** How long the teacher cheers a passed test. */
private const val CHEER_MS = 2_600L
