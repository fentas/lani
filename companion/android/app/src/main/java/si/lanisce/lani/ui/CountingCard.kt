package si.lanisce.lani.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.CountStep
import si.lanisce.lani.data.Counting
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.ItemMark
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.school.verdictColor
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.TriglavRed

// A counting card on screen ("🔢 Štetje · Counting", data/Counting.kt): the noun at the top (its picture, its base form,
// what it means), then its four steps one at a time, 1 · 2 · 3 · 5, each answered (chosen among its options, or typed
// once the card is secure: "✋ Izberi raje · Let me choose" goes back to the options) and marked at once with the right
// line, step 1 with what the noun's end says of its gender; a right one goes on by itself, a wrong one at "Naprej · Next".
// At the end all four lines together, said by the narrator, and the card's outcome for the run (its steps as the sheet's
// items: they count on the grammar book's pages, Counting.ruleAnswers).

/** How long a right step stays marked before the next comes. */
private const val RIGHT_MS = 900L

/**
 * [ex] played step by step; [done] with the card's outcome at the end, [onStep] with the steps answered so far (the
 * chat's attachment while the card is on).
 */
@Composable
internal fun CountingEx(vm: AppViewModel, ex: Exercise.Counting, locked: Boolean, done: (Outcome) -> Unit, onStep: (List<ItemMark>) -> Unit = {}) {
    val marks = remember { mutableStateListOf<ItemMark>() }
    /** The step on screen's mark, until it is left. */
    var mark by remember { mutableStateOf<ItemMark?>(null) }
    var at by remember { mutableIntStateOf(0) }
    /** Typed: the card is secure, until "Let me choose". */
    var typed by remember { mutableStateOf(ex.typed) }
    var reported by remember { mutableStateOf(false) }
    val over = locked || at >= ex.steps.size
    val next: () -> Unit = {
        mark?.let { marks += it }
        mark = null
        onStep(marks.toList())
        at++
        if (at >= ex.steps.size && !reported) {
            reported = true
            // all four together, said by the narrator (a clip made on demand, as any line)
            vm.speaker.say(ex.said, voiceName = Clips.FEMALE)
            val v = Counting.verdict(marks)
            done(Outcome(v, marks.joinToString(" · ") { it.answer }, null, null, sheet = marks.toList()))
        }
    }
    LaunchedEffect(mark) {
        // the chat's attachment has the step on screen too, once answered
        onStep(marks.toList() + listOfNotNull(mark))
        val m = mark ?: return@LaunchedEffect
        if (m.verdict != Verdict.WRONG) {
            delay(RIGHT_MS)
            if (mark === m) next()
        }
    }

    Text("🔢 ${bi("counting.title")}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Noun(ex)
    // the rule's page and the gender's on the cheat sheet, one tap away (help, not a hint; the top bar's 📋 opens them too)
    TextButton(onClick = { vm.cheat.show(ex.pages) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("📋 ${bi("counting.help")}") }
    Spacer(Modifier.height(4.dp))
    // the steps answered, each with its mark and the right line
    for (i in marks.indices) StepDone(vm, ex, i, marks[i])
    if (!over) {
        val s = ex.steps[at]
        // the step on screen kept in view as the steps above it grow (the card scrolls in the run)
        Column(Modifier.then(revealOn(at, mark != null))) {
            Spacer(Modifier.height(8.dp))
            StepAsked(ex, s, typed && mark == null)
            val m = mark
            if (m == null) {
                if (typed) {
                    TypedStep(s) { a -> mark = Counting.grade(s, a, typed = true) }
                    TextButton(onClick = { typed = false }, modifier = Modifier.heightIn(min = 48.dp).padding(top = 4.dp)) { Text("✋ ${bi("adaptive.letMeChoose")}") }
                } else ChosenStep(s) { a -> mark = Counting.grade(s, a, typed = false).let { if (ex.typed) it.copy(hinted = true) else it } }
            } else {
                StepDone(vm, ex, at, m, now = true)
                if (m.verdict == Verdict.WRONG) {
                    Spacer(Modifier.height(8.dp))
                    BigButton(bi("common.next"), onClick = next)
                }
            }
        }
    } else if (marks.size == ex.steps.size) {
        Spacer(Modifier.height(12.dp))
        // all four together, in view once the run's feedback slid in below
        Column(Modifier.then(revealOn(ex.steps.size, true, after = 450))) { AllFour(vm, ex, marks) }
    }
    Spacer(Modifier.height(8.dp))
}

/** Scrolls what it's on into view each time [step] or [answered] change ([after] ms later: what slides in below first). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun revealOn(step: Int, answered: Boolean, after: Long = 80L): Modifier {
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(step, answered) {
        delay(after)
        bring.bringIntoView()
    }
    return Modifier.bringIntoViewRequester(bring)
}

/** The noun: its picture, its base form big, what it means. */
@Composable
private fun Noun(ex: Exercise.Counting) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(ex.emoji ?: "🔢", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(ex.lemma, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            ex.meaning?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** What a step shows: its number, as many pictures as it counts (five for 5+), and what to do ([typed]: write it). */
@Composable
private fun StepAsked(ex: Exercise.Counting, s: CountStep, typed: Boolean) {
    val pictures = (ex.emoji ?: "").let { e -> if (e.isBlank()) "" else List(s.n) { e }.joinToString("") }
    val number = if (s.n == 5) "5 +" else "${s.n}"
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = number }) {
        Text(number, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        if (pictures.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Text(pictures, style = MaterialTheme.typography.headlineSmall)
        }
    }
    Spacer(Modifier.height(6.dp))
    val what = when {
        s.n == 1 -> if (typed) "counting.typeNumeral" else "counting.pickNumeral"
        else -> if (typed) "counting.typePhrase" else "counting.pickPhrase"
    }
    Text(bi(what), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
}

/** A step's options: a tap answers (step 1 en / ena / eno, the others three phrases, each with its 🔊 once answered). */
@Composable
private fun ChosenStep(s: CountStep, answer: (String) -> Unit) {
    for (o in s.options) {
        Card(
            onClick = { answer(o) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            shape = MaterialTheme.shapes.medium,
        ) { Text(o, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(18.dp)) }
    }
}

/** A step typed: the field (the language learned's keyboard, no suggestions), č š ž, "Preveri · Check". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TypedStep(s: CountStep, answer: (String) -> Unit) {
    var value by remember(s.n) { mutableStateOf(TextFieldValue("")) }
    // with the keyboard up, the field, its letters and "Preveri · Check" scrolled into view together; the keyboard's ✓
    // checks too
    val bring = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    // again as the keyboard comes up (the run's room shrinks with it, frame by frame): the last one once it is up
    val keyboard = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, keyboard) {
        if (!focused) return@LaunchedEffect
        delay(60)
        bring.bringIntoView()
    }
    val check = { if (value.text.isNotBlank()) answer(value.text) }
    Column(Modifier.bringIntoViewRequester(bring)) {
        NoSuggestions(lang = Lang.SL) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                placeholder = { Text(if (s.n == 1) "en / ena / eno" else bi("playerScreen.typeAnswer")) },
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = AnswerKeyboard.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { check() }),
                singleLine = true,
            )
        }
        Spacer(Modifier.height(8.dp))
        DiacriticChips(value, { value = it }, lang = Lang.SL)
        Spacer(Modifier.height(12.dp))
        BigButton(bi("playerScreen.check"), onClick = check, enabled = value.text.isNotBlank())
    }
}

/**
 * Step [i] answered ([m]): ✓ or ✗ with what was answered, the right line (bold, its 🔊), and for step 1 what the noun's
 * end says of its gender. [now]: the step just answered (said to TalkBack at once).
 */
@Composable
private fun StepDone(vm: AppViewModel, ex: Exercise.Counting, i: Int, m: ItemMark, now: Boolean = false) {
    val s = ex.steps[i]
    val color = verdictColor(m.verdict)
    Surface(
        color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).then(if (now) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (s.n == 5) "5+" else "${s.n}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, modifier = Modifier.width(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.line, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (m.verdict == Verdict.WRONG) AlpineGreen else MaterialTheme.colorScheme.onSurface)
                    if (m.verdict != Verdict.CORRECT) {
                        Text(
                            "${if (m.verdict == Verdict.WRONG) "✗" else "≈"} ${m.answer.ifBlank { "…" }}",
                            style = MaterialTheme.typography.bodyMedium, color = if (m.verdict == Verdict.WRONG) TriglavRed else color,
                        )
                    }
                }
                Text(if (m.verdict == Verdict.WRONG) "❌" else if (m.verdict == Verdict.ALMOST) "🟡" else "✅")
                SpeakButton(vm.speaker, s.line, Modifier.padding(start = 6.dp))
            }
            if (i == 0) ex.why?.let { Text("💡 $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

/** The end: the four lines together ("ena kava, dve kavi, tri kave, pet kav"), each marked, their 🔊 together. */
@Composable
private fun AllFour(vm: AppViewModel, ex: Exercise.Counting, marks: List<ItemMark>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧾 ${bi("counting.allFour")}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                SpeakButton(vm.speaker, ex.said)
            }
            ex.steps.forEachIndexed { i, s ->
                val v = marks.getOrNull(i)?.verdict
                Text(
                    "${if (v == Verdict.WRONG) "✗" else "✓"}  ${s.line}",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = if (v == Verdict.WRONG) TriglavRed else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
