package si.lanisce.lani.ui.cases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.Screen
import si.lanisce.lani.app.CasesDone
import si.lanisce.lani.app.ExerciseSource
import si.lanisce.lani.data.CaseKind
import si.lanisce.lani.data.CaseTrainer
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.Kase
import si.lanisce.lani.data.ScreenClock
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.ExerciseRun
import si.lanisce.lani.ui.Finish
import si.lanisce.lani.ui.GradientHeader
import si.lanisce.lani.ui.Markdown
import si.lanisce.lani.ui.backLabel
import si.lanisce.lani.ui.game.RewardSummary
import si.lanisce.lani.ui.game.stars
import si.lanisce.lani.ui.minutesSince
import si.lanisce.lani.ui.stage.Run
import si.lanisce.lani.ui.stage.rememberStage
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.XpGold
import java.time.LocalDate

/**
 * "🧩 Skloni · Cases" (companion/README.md "Case trainer"): closed, what opens it (the first case unit's intro, the
 * grammar book's case pages met) one tap away; open, "▶️ Začni · Start" (mixed, or on [focus]: a case unit's, Home's
 * weak case, the tutor's assignment), the items due again, the six cases in their colours with their stars (a tap: a
 * session on that case), what decides them (each rule's stars and how often its deciding word was tapped right; a tap: a
 * session on it), the three kinds, and that its Slovene is machine-written.
 */
@Composable
fun CasesScreen(vm: AppViewModel, focus: String?) {
    val pool = remember(vm.cases.bank, vm.game.state, vm.content.dashboard, vm.forms.cache) { vm.cases.pool() }
    val open = pool?.let(CaseTrainer::isOpen) == true
    val p = vm.cases.progress
    val due = CaseTrainer.due(p, LocalDate.now()).size
    val assigned = vm.cases.assigned
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { GradientHeader("🧩 ${bi("cases.title")}", bi("cases.about"), vm.backLabel, onBack = vm::back) }
        if (!open) {
            item { Locked(vm) }
        } else {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    assigned?.let { a -> Assigned(vm, a.focus, a.note) }
                    val focusTitle = focus?.let { focusTitle(vm, it) }
                    BigButton(
                        "▶️ ${if (focusTitle != null) bi("cases.startFocus", "focus" to focusTitle) else bi("cases.start")}",
                        onClick = { vm.startCases(focus) }, color = AlpineGreen,
                    )
                    if (focus != null) TextButton(onClick = { vm.startCases(null) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("🔀 ${bi("cases.startMixed")}") }
                    // the storm: phrases into the six cases against the clock
                    OutlinedButton(onClick = { vm.startStorm() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text("⚡ ${bi("cases.storm.start", "s" to CaseTrainer.STORM_SECONDS)}")
                    }
                    if (due > 0) Text("🔁 ${bi("cases.dueAgain", "n" to due)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            item { SectionTitle("🎨 ${bi("cases.theCases")}") }
            items(Kase.entries, key = { it.id }) { k -> CaseRow(vm, k) }
            item { SectionTitle("🔑 ${bi("cases.whatDecides")}") }
            item { RuleRows(vm) }
            item { SectionTitle("🎛️ ${bi("cases.kinds")}") }
            item { Kinds() }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Markdown("💡 ${bi("cases.tip")}")
                Text("🤖 ${bi("cases.machineWritten")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A focus's name: a case's ("2. rodilnik · genitive"), else its grammar page's title, else its id. */
internal fun focusTitle(vm: AppViewModel, focus: String): String =
    Kase.of(focus)?.label() ?: vm.grammar.page(focus)?.titleShown(L10n.pair) ?: focus

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp).semantics { heading() })
}

/** Closed: why (nothing introduced to ask yet), and the one tap there: the school's first case unit, else the grammar book. */
@Composable
private fun Locked(vm: AppViewModel) {
    val unit = vm.school.unit("tozilnik")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔒", style = MaterialTheme.typography.displaySmall, modifier = Modifier.clearAndSetSemantics {})
            Text(bi(if (vm.cases.available) "cases.locked" else "cases.notHere"), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            if (vm.cases.available) {
                if (unit != null) BigButton("🏫 ${bi("cases.toUnit")}", onClick = { vm.openUnit(unit.id) }, color = XpGold)
                else BigButton("📖 ${bi("cases.toBook")}", onClick = { vm.openGrammarBook() }, color = XpGold)
            }
        }
    }
}

/** The tutor's assignment: what it is about, why, and its session one tap away. */
@Composable
private fun Assigned(vm: AppViewModel, focus: String, note: String?) {
    Card(colors = CardDefaults.cardColors(containerColor = XpGold.copy(alpha = 0.16f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🧑‍🏫 ${bi("cases.assigned", "focus" to focusTitle(vm, focus))}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            OutlinedButton(onClick = { vm.startCases(focus) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("▶️ ${bi("cases.startAssigned")}") }
        }
    }
}

/** A case's row: its colour, number and name, its questions, its stars and its last answers; a tap starts a session on it. */
@Composable
private fun CaseRow(vm: AppViewModel, k: Kase) {
    val p = vm.cases.progress
    val key = CaseTrainer.caseKey(k)
    val (right, of) = CaseTrainer.score(p, key)
    val st = CaseTrainer.stars(p, key)
    val c = k.color()
    Card(
        onClick = { vm.startCases(k.id) },
        colors = CardDefaults.cardColors(containerColor = c.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).semantics(mergeDescendants = true) {},
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(c).clearAndSetSemantics {})
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(k.label(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(k.questions, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (of == 0) bi("cases.notYet") else bi("cases.lately", "right" to right, "of" to of),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(st?.let(::stars) ?: "☆☆☆☆☆", style = MaterialTheme.typography.titleSmall, color = XpGold, modifier = Modifier.padding(end = 14.dp))
        }
    }
}

/**
 * What decides the cases: each rule of the bank the learner has met or can be asked (its grammar page's title, its stars,
 * how often its deciding word was tapped right lately, what the case lens showed); a tap starts a session on it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleRows(vm: AppViewModel) {
    val bank = vm.cases.bank ?: return
    val p = vm.cases.progress
    val rules = bank.rules.values.map { it.page to Kase.of(it.case) }.distinctBy { it.first }
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((page, k) in rules) {
            val title = vm.grammar.page(page)?.titleShown(L10n.pair) ?: page
            val st = CaseTrainer.stars(p, CaseTrainer.ruleKey(page))
            val why = p?.why?.get(page).orEmpty()
            val c = k?.color() ?: MaterialTheme.colorScheme.secondary
            Card(
                onClick = { vm.startCases(page) },
                colors = CardDefaults.cardColors(containerColor = c.copy(alpha = 0.10f)),
                modifier = Modifier.heightIn(min = 48.dp).semantics(mergeDescendants = true) {},
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(
                            st?.let(::stars),
                            why.takeIf { it.isNotEmpty() }?.let { w -> "🤔 ${w.count { it == '1' }}/${w.length}" },
                            p?.seen?.get(page)?.let { s -> "👁 ${s.n}×" },
                        ).joinToString("  ").ifBlank { bi("cases.notYet") },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The three kinds, a line each. */
@Composable
private fun Kinds() {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (k in CaseKind.entries) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(k.emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clearAndSetSemantics {})
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(bi(k.title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(bi(k.about), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A session of the trainer: its tasks one at a time, then how it went (each case in its colour), what comes back. */
@Composable
fun CasesRunScreen(vm: AppViewModel, at: Screen.CasesRun) {
    val exercises = remember(at) { at.planned.map { it.exercise } }
    val started = remember { ScreenClock.app.now() }
    val stage = rememberStage(vm, Run.Review)
    var done by remember { mutableStateOf<CasesDone?>(null) }
    val d = done
    if (d == null) {
        ExerciseRun(
            vm, if (at.storm) "⚡ ${bi("cases.storm.title")}" else "🧩 ${bi("cases.title")}", exercises, moduleId = null, onExit = vm::leaveRun, stage = stage,
            // the storm against the clock: a right pick flashes and the next phrase comes (ExerciseRun's timed run)
            timeLimitSeconds = if (at.storm) CaseTrainer.STORM_SECONDS else null, fast = at.storm, endless = at.storm,
            source = { i -> at.planned.getOrNull(i)?.let { p -> ExerciseSource.cases(p.task.kind.wire, p.task.key, i, tutor = vm.cases.bank?.items?.firstOrNull { it.id == p.task.key }?.source == "tutor") } },
            pages = listOf("skloni"),
            // a form typed wrong counts on its page with both sentences, as a dialog's turn records one
            slips = { i, o ->
                val ex = exercises.getOrNull(i)
                if (ex == null || ex.kind != "decline" || o.correct == null) null
                else ex.prompt.substringBeforeLast(" (").replace("___", o.answer) to ex.sentence
            },
        ) { outs -> done = vm.cases.finish(at.planned, outs, minutesSince(started), at.focus, storm = at.storm) }
        return
    }
    if (at.storm) return StormEnd(vm, d, stage)
    val right = d.verdicts.count { it == Verdict.CORRECT }
    val almost = d.verdicts.count { it == Verdict.ALMOST }
    Finish(
        title = if (right + almost >= d.verdicts.size * 0.7) "${inTarget("playerScreen.bravo")} 🎉" else "${inTarget("playerScreen.goodWork")} 💪",
        lines = listOf("✅ $right   🟡 $almost   ❌ ${d.verdicts.size - right - almost}", "+${right * 10 + almost * 5 + 25} XP"),
        onDone = vm::leaveRun,
        stage = stage,
    ) {
        RewardSummary(vm.game.lastReward, onVillage = vm::openVillage)
        Spacer(Modifier.height(10.dp))
        ByCase(d)
        if (d.missed.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("🔁 ${bi("cases.comingBack", "n" to d.missed.size)}", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { vm.startCases(at.focus, again = true) }) { Text("▶️ ${bi("cases.startAgain")}") }
    }
}

/** A storm's end: its medal (gold, silver, bronze), how many right, the longest run of right ones, each case, once more. */
@Composable
private fun StormEnd(vm: AppViewModel, d: CasesDone, stage: si.lanisce.lani.ui.stage.StageState) {
    val right = d.verdicts.count { it == Verdict.CORRECT }
    val medal = CaseTrainer.medal(d.verdicts)
    Finish(
        title = "${medal.emoji} ${inTarget(medal.key)}",
        lines = listOf(
            bi("cases.storm.sorted", "right" to right, "of" to d.verdicts.size),
            "🔥 ${bi("cases.storm.streak", "n" to CaseTrainer.streak(d.verdicts))}",
            "+${right * 10 + 25} XP",
        ),
        onDone = vm::leaveRun,
        stage = stage,
    ) {
        RewardSummary(vm.game.lastReward, onVillage = vm::openVillage)
        Spacer(Modifier.height(10.dp))
        ByCase(d)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { vm.startStorm(again = true) }) { Text("⚡ ${bi("cases.storm.again")}") }
    }
}

/** How the session went by case: each case asked, in its colour, right of asked, and its stars now. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ByCase(d: CasesDone) {
    val by = d.tasks.zip(d.verdicts).groupBy({ it.first.case }, { it.second })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        for (k in Kase.entries) {
            val vs = by[k] ?: continue
            val c = k.color()
            Card(colors = CardDefaults.cardColors(containerColor = c.copy(alpha = 0.16f)), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(k.label(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("${vs.count { it != Verdict.WRONG }}/${vs.size}  ${d.stars[k]?.let(::stars).orEmpty()}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
