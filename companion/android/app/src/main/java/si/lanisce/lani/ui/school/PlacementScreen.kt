package si.lanisce.lani.ui.school

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.ExerciseSource
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.ScreenClock
import si.lanisce.lani.game.LevelPlacement
import si.lanisce.lani.game.School
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.ExerciseRun
import si.lanisce.lani.ui.GradientHeader
import si.lanisce.lani.ui.backLabel
import si.lanisce.lani.ui.game.RewardSummary
import si.lanisce.lani.ui.game.giftWords
import si.lanisce.lani.ui.setup.ParentGated
import si.lanisce.lani.ui.stage.Run
import si.lanisce.lani.ui.stage.rememberStage
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.statusBarScrim

/**
 * "🧭 Uvrstitveni test · Placement test" (game/LevelPlacement, app/PlacementController; companion/COURSE.md "The
 * placement test"): first what it is (the teacher's line, how long, that nothing changes until the learner says yes),
 * then probe after probe on the stage, the teacher there (no cheat sheet, nothing counted on the rules), then where the
 * learner starts: the first unit, the units before it marked done, the level, and "✓ Začnem tukaj · Start here" (a
 * child's behind a parent's hold) or "Ostanem, kjer sem · Keep my place".
 */
@Composable
fun PlacementScreen(vm: AppViewModel) {
    val p = vm.placement
    val t = p.test
    when {
        t == null -> PlacementIntro(vm)
        p.result != null -> PlacementResult(vm)
        p.probe != null -> PlacementProbe(vm)
        else -> PlacementIntro(vm)
    }
}

/** What the test is, before it begins: the teacher's line, how long, what it does and doesn't. */
@Composable
private fun PlacementIntro(vm: AppViewModel) {
    val p = vm.placement
    val child = p.version == LevelPlacement.Version.CHILD
    val teacher = remember { vm.school.teacher() }
    val s = vm.game.state
    val begun = s?.course?.units?.any { (k, u) -> k.startsWith("${p.language}/") && u.done != null } == true
    val last = p.last()
    BackHandler { vm.leavePlacement() }
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().statusBarScrim(scroll).verticalScroll(scroll)) {
        GradientHeader("🧭 ${bi("placement.title")}", bi("placement.about"), vm.backLabel, onBack = vm::leavePlacement)
        Column(
            Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(16.dp).align(Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TeacherSays(vm, teacher, if (child) "placement.helloChild" else "placement.hello")
            for (line in listOfNotNull(
                "⏱️ ${bi(if (child) "placement.howLongChild" else "placement.howLong")}",
                "📝 ${bi("placement.howItems")}",
                if (child) null else "📋 ${bi("placement.howNoHelp")}",
                "✋ ${bi("placement.howNothing")}",
                if (begun) "✅ ${bi("placement.howKept")}" else null,
                last?.let { "🧭 ${bi("placement.last", "day" to it.day, "level" to (it.start ?: it.level))}" },
            )) Text(line, style = MaterialTheme.typography.bodyLarge)
            if (p.waiting) {
                Text("⏳ ${bi("treasure.tutorWaits")}", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = p::sendAgain, modifier = Modifier.heightIn(min = 48.dp)) { Text("📨 ${bi("treasure.sendAgain")}") }
            }
            Spacer(Modifier.height(4.dp))
            BigButton("▶️ ${bi("placement.begin")}", onClick = { if (!p.begin()) vm.leavePlacement() }, color = TriglavRed)
            TextButton(onClick = vm::leavePlacement, modifier = Modifier.heightIn(min = 48.dp).align(Alignment.CenterHorizontally)) { Text(bi("school.later")) }
        }
    }
}

/**
 * The probe on screen, as a run on the stage with the teacher: no cheat sheet or tutor in its top bar, nothing counted on
 * the rules; its first exercise says which part it is and whether it is harder or easier than the last. Its end asks
 * the next probe, or shows the result; "✕" before any answer of a later probe ends the test there.
 */
@Composable
private fun PlacementProbe(vm: AppViewModel) {
    val p = vm.placement
    val teacher = remember { vm.school.teacher() }
    val stage = rememberStage(vm, remember { Run.Lesson(teacher?.name, teacher?.emoji, indoors = vm.game.state?.let(School::indoors) == true) })
    val n = p.asked
    // the probe on screen (taken before key: an early return out of key's inline block can't be dexed)
    val probe = p.probe ?: return
    key(n) {
        val exercises = remember { probe.items.map { it.exercise } }
        val started = remember { ScreenClock.app.now() }
        val before = p.test?.probes?.lastOrNull()
        val note = when {
            before == null -> bi("placement.partFirst")
            before.passed -> bi("placement.partHarder", "n" to n)
            else -> bi("placement.partEasier", "n" to n)
        }
        ExerciseRun(
            vm, "🧭 ${bi("placement.title")}", exercises, moduleId = null, stage = stage,
            onExit = { if (p.test?.probes.isNullOrEmpty()) vm.leavePlacement() else p.stop() },
            before = { Text(note, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp)) },
            source = { i -> probe.items.getOrNull(i)?.let { ExerciseSource.course(vm.placement.language, it.unit, "test", it.index) } },
            rules = exercises.map { null },
            practice = false, help = false,
            language = p.language,
            readOut = p.version == LevelPlacement.Version.CHILD && vm.introVoice.auto,
        ) { outs ->
            val items = outs.map { o -> if (o.sheet.isNotEmpty()) o.sheet.map { it.paid } else listOf(o.paid) }
            val seconds = ((ScreenClock.app.now() - started) / 1000).toInt()
            p.answered(items, seconds, complete = outs.size >= exercises.size)
        }
    }
}

/**
 * Where the learner starts: the teacher says it, the first unit, the units before it marked done (still open to do
 * again), the level (raised, or as it stays), what the answers paid; "✓ Začnem tukaj · Start here" takes it (a child's
 * behind a parent's two-second hold), "Ostanem, kjer sem · Keep my place" doesn't. Nothing to take: one "V redu · OK".
 */
@Composable
private fun PlacementResult(vm: AppViewModel) {
    val p = vm.placement
    val r = p.result ?: return
    val course = vm.school.course
    val teacher = remember { vm.school.teacher() }
    val child = p.version == LevelPlacement.Version.CHILD
    val changes = r.changes(p.from)
    BackHandler { vm.leavePlacement() }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Text("🧭 ${bi("placement.resultTitle")}", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    r.known < 0 -> TeacherSays(vm, teacher, "placement.startBeginning")
                    r.start == null -> TeacherSays(vm, teacher, "placement.startPast")
                    else -> TeacherSays(vm, teacher, "placement.startAt", "level" to r.start.level)
                }
                val first = r.start?.units?.firstOrNull()?.let { course?.units?.get(it) }
                // the unit's title in each half's own language ("Tvoja prva enota: Oče … · Your first unit: Odd nouns …")
                val title = first?.let { u -> si.lanisce.lani.l10n.Localized { lang -> "${u.emoji} ${si.lanisce.lani.data.Grammar.text(u.title, lang.code)}" } }
                if (title != null && r.known >= 0) Text(bi("placement.firstUnit", "unit" to title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (r.units.isNotEmpty()) Text("✅ ${bi("placement.marks", "n" to r.units.size)}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (r.rises(p.from)) bi("placement.levelRises", "from" to p.from, "level" to r.level) else bi("placement.levelStays", "level" to p.from),
                    style = MaterialTheme.typography.bodyLarge,
                )
                val v = p.test?.verdicts.orEmpty()
                val right = v.count { it == Verdict.CORRECT }
                val almost = v.count { it == Verdict.ALMOST }
                Text("✅ $right   🟡 $almost   ❌ ${v.size - right - almost}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            RewardSummary(p.reward, onVillage = vm::openVillage, words = giftWords(vm))
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!changes) BigButton("✓ ${bi("placement.ok")}", onClick = { p.keep(); vm.leavePlacement() })
                else {
                    ParentGated(child, onLight = true) {
                        BigButton("✓ ${bi("placement.take")}", onClick = { if (p.take()) vm.placementTaken() }, color = TriglavRed)
                    }
                    OutlinedButton(onClick = { p.keep(); vm.leavePlacement() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(bi("placement.keep")) }
                }
            }
        }
    }
}
