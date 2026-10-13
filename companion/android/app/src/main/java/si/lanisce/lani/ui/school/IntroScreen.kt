package si.lanisce.lani.ui.school

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.CourseUnit
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.GrammarCell
import si.lanisce.lani.data.Intro
import si.lanisce.lani.data.Sheets
import si.lanisce.lani.game.School
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.AnswerKeyboard
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.DiacriticChips
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.Markdown
import si.lanisce.lani.ui.NoSuggestions
import si.lanisce.lani.ui.Portrait
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.inlineMarkdown
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.ui.words.WordText

// A unit's guided introduction (companion/COURSE.md, "The introduction"): the new material in small steps as a teacher
// brings it, each a few lines, an example or two and a bit more of the table, then a quick question about it; the answer
// shown with its why, nothing lost on a miss; the table grows as the steps go; at the end the rule's page as a summary.
// Resumable, and skippable ("⏭ Poznam to · I know this").

/** Unit [id]'s intro, where the learner left it. */
@Composable
fun IntroScreen(vm: AppViewModel, id: String, fromVillage: Boolean) {
    val unit = vm.school.unit(id)
    val intro = unit?.intro
    if (unit == null || intro == null) {
        LaunchedEffect(id) { vm.openSchool(fromVillage) }
        return
    }
    val back = { vm.openUnit(unit.id, fromVillage) }
    BackHandler(onBack = back)
    val p = vm.game.state?.let { School.progress(it, unit.language, unit.id) }
    // resumed where it was left (from the start again once it was done)
    var step by remember { mutableIntStateOf(if (p?.introDone == true) 0 else (p?.intro ?: 0).coerceIn(0, intro.steps.size)) }
    LaunchedEffect(Unit) { if (step == 0) vm.school.introProgress(unit, 0, done = false) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { EmojiLabel("✕", Labels.CLOSE) }
            LinearProgressIndicator(
                progress = { step.toFloat() / intro.steps.size }, modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape), color = XpGold,
            )
            TextButton(onClick = { vm.school.introProgress(unit, intro.steps.size, done = true); back() }) { Text("⏭ ${bi("school.knowThis")}") }
        }
        Text("📖 ${unit.titleShown(L10n.pair)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
        if (step >= intro.steps.size) {
            Summary(vm, unit, intro, Modifier.weight(1f)) {
                vm.school.introProgress(unit, intro.steps.size, done = true)
                back()
            }
            return@Column
        }
        key(step) {
            IntroStepView(vm, unit, intro, step, Modifier.weight(1f)) {
                vm.school.introProgress(unit, step + 1, done = false)
                step++
            }
        }
    }
}

/** One step: the teacher's lines, the examples, the table so far (this step's rows marked new), its question, "Naprej". */
@Composable
internal fun IntroStepView(vm: AppViewModel, unit: CourseUnit, intro: Intro, i: Int, modifier: Modifier, next: () -> Unit) {
    val s = intro.steps[i]
    val base = L10n.pair.base.code
    var answered by remember { mutableStateOf(s.ask == null) }
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(bi("school.stepOf", "n" to i + 1, "of" to intro.steps.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Top) {
                val t = vm.school.teacher()
                if (t != null) Portrait(t.art, modifier = Modifier.size(48.dp), px = 48, seed = t.id.hashCode()) else Text("👩‍🏫", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(10.dp))
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) {
                    Markdown(Grammar.text(s.say, base), Modifier.padding(12.dp))
                }
            }
            for (e in s.examples) ExampleRow(vm, e.target(unit.language), e.meaning(base), e.noteIn(base))
            val rows = intro.steps.take(i + 1).flatMap { it.rows }
            if (intro.head.isNotEmpty() && rows.isNotEmpty()) GrowingTable(vm, intro.head, rows, fresh = s.rows.size)
            s.ask?.let { ask -> IntroAsk(vm, ask) { answered = true } }
        }
        BigButton(bi("common.next"), enabled = answered, onClick = next, color = if (answered) AlpineGreen else MaterialTheme.colorScheme.primary)
    }
}

/** An example of the language: its words tappable, 🔊, what it means and its note. */
@Composable
internal fun ExampleRow(vm: AppViewModel, said: String, meant: String, note: String? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                WordText(said, onWord = si.lanisce.lani.ui.words.lookUpIn(vm, said, meant, si.lanisce.lani.data.Words.READING), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (meant != said) Text(meant, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                note?.let { Text(inlineMarkdown(it), style = MaterialTheme.typography.bodySmall) }
            }
            val t = vm.school.teacher()
            SpeakButton(vm.speaker, said, voiceName = t?.speakerVoice ?: si.lanisce.lani.data.Clips.FEMALE, fallback = t?.voice, person = t?.id)
        }
    }
}

/** The table the steps build: its head, the rows so far, the last [fresh] ones (this step's) marked. */
@Composable
internal fun GrowingTable(vm: AppViewModel, head: List<GrammarCell>, rows: List<List<GrammarCell>>, fresh: Int) {
    val base = L10n.pair.base.code
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth().border(1.dp, line, MaterialTheme.shapes.small).clip(MaterialTheme.shapes.small)) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
            for (c in head) Text(c.shown(base), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f).padding(6.dp))
        }
        rows.forEachIndexed { r, row ->
            val new = r >= rows.size - fresh
            Row(Modifier.fillMaxWidth().background(if (new) XpGold.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)) {
                for (c in row) {
                    val m = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 5.dp)
                    val text = c.shown(base)
                    if (c.isWords) WordText(text, si.lanisce.lani.ui.words.lookUpIn(vm, text, null, si.lanisce.lani.data.Words.READING), modifier = m, style = MaterialTheme.typography.bodyLarge, fontWeight = if (new) FontWeight.Bold else null)
                    else Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = m)
                }
            }
        }
    }
}

/**
 * A step's quick question, graded lightly: a tap choice or a one-word gap; the answer shown with its why, a miss costs
 * nothing (nothing pays, nothing counts on the rule). [onAnswered] lets the step go on.
 */
@Composable
private fun IntroAsk(vm: AppViewModel, ask: Exercise, onAnswered: () -> Unit) {
    var verdict by remember { mutableStateOf<Grading.Verdict?>(null) }
    var picked by remember { mutableStateOf<Int?>(null) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("❓ ${bi("school.quickQuestion")}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (ask) {
                is Exercise.Choice -> {
                    ask.instruction?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                    Text(inlineMarkdown(ask.prompt), style = MaterialTheme.typography.titleLarge)
                    ask.options.forEachIndexed { o, opt ->
                        val color = when {
                            verdict == null -> CardDefaults.cardColors()
                            o == ask.answer -> CardDefaults.cardColors(containerColor = AlpineGreen.copy(alpha = 0.2f))
                            o == picked -> CardDefaults.cardColors(containerColor = TriglavRed.copy(alpha = 0.2f))
                            else -> CardDefaults.cardColors()
                        }
                        Card(
                            onClick = {
                                if (verdict == null) {
                                    picked = o
                                    verdict = if (o == ask.answer) Grading.Verdict.CORRECT else Grading.Verdict.WRONG
                                    onAnswered()
                                }
                            },
                            colors = color, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text(opt, Modifier.padding(14.dp), style = MaterialTheme.typography.titleMedium) }
                    }
                    verdict?.let { v -> AskResult(vm, v, ask.options.getOrNull(ask.answer).orEmpty(), ask.explain, gapFilled(ask.prompt, ask.options.getOrNull(ask.answer))) }
                }
                is Exercise.Cloze -> {
                    var value by remember { mutableStateOf(TextFieldValue("")) }
                    ask.instruction?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                    Text(inlineMarkdown(ask.text.replace(Sheets.GAP, "______")), style = MaterialTheme.typography.titleLarge)
                    NoSuggestions {
                        OutlinedTextField(
                            value = value, onValueChange = { value = it }, enabled = verdict == null, singleLine = true,
                            keyboardOptions = AnswerKeyboard, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(ask.hint ?: bi("playerScreen.typeAnswer")) },
                        )
                    }
                    if (verdict == null) {
                        DiacriticChips(value, { value = it })
                        BigButton(bi("playerScreen.check"), enabled = value.text.isNotBlank(), onClick = {
                            verdict = Grading.check(value.text, ask.accept).verdict
                            onAnswered()
                        })
                    }
                    verdict?.let { v -> AskResult(vm, v, ask.accept.firstOrNull().orEmpty(), ask.explain, gapFilled(ask.text, ask.accept.firstOrNull())) }
                }
                else -> Unit
            }
        }
    }
}

/** A sentence with its gap filled with [answer], without a translation in brackets at its end; null when there's no gap. */
private fun gapFilled(text: String, answer: String?): String? {
    if (answer == null || !Sheets.GAP.containsMatchIn(text)) return null
    return text.replace(Sheets.GAP, answer).replace(Regex("""\s*\([^()]*\)\s*$"""), "").trim()
}

@Composable
private fun AskResult(vm: AppViewModel, v: Grading.Verdict, right: String, explain: String?, sentence: String?) {
    val (label, color) = when (v) {
        Grading.Verdict.CORRECT -> "${inTarget("playerScreen.correct")} ✅" to AlpineGreen
        Grading.Verdict.ALMOST -> "${inTarget("playerScreen.almost")} 🟡" to si.lanisce.lani.ui.theme.FlameOrange
        Grading.Verdict.WRONG -> "${bi("school.missNoMatter")} 🙂" to TriglavRed
    }
    Text(label, style = MaterialTheme.typography.titleMedium, color = color)
    if (v != Grading.Verdict.CORRECT) Text("→ $right", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    sentence?.let { s ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            SpeakButton(vm.speaker, s)
        }
    }
    explain?.let { Markdown(it) }
}

/** The end: the rule's page as a summary card (its rule and its whole table), the book one tap away, then the worksheets. */
@Composable
private fun Summary(vm: AppViewModel, unit: CourseUnit, intro: Intro, modifier: Modifier, done: () -> Unit) {
    val base = L10n.pair.base.code
    val page = unit.grammar.firstNotNullOfOrNull { vm.grammar.page(it) }
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("🧾 ${bi("school.summary")}", style = MaterialTheme.typography.titleLarge)
            if (page != null) {
                Text("${page.emoji} ${page.titleShown(L10n.pair)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Markdown(page.ruleIn(base))
                page.table?.let { GrowingTable(vm, it.head, it.rows, fresh = 0) }
                TextButton(onClick = { vm.grammar.show(page.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("📖 ${bi("school.toTheBook")}") }
            } else {
                val rows = intro.steps.flatMap { it.rows }
                if (intro.head.isNotEmpty() && rows.isNotEmpty()) GrowingTable(vm, intro.head, rows, fresh = 0)
            }
        }
        Box(Modifier.padding(top = 8.dp)) { BigButton("✓ ${bi("school.toWorksheets")}", color = AlpineGreen, onClick = done) }
    }
}
