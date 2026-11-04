package si.lanisce.lani.ui.school

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.Link
import si.lanisce.lani.data.CourseUnit
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.Length
import si.lanisce.lani.data.Production
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.DiacriticChips
import si.lanisce.lani.ui.ExText
import si.lanisce.lani.ui.ExplainFormat
import si.lanisce.lani.ui.LocalLook
import si.lanisce.lani.ui.Markdown
import si.lanisce.lani.ui.NoSuggestions
import si.lanisce.lani.ui.Outcome
import si.lanisce.lani.ui.WordTip
import si.lanisce.lani.ui.emphasized
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.SpeakEx
import si.lanisce.lani.ui.SpeechGrader
import si.lanisce.lani.ui.heardVoice
import si.lanisce.lani.ui.inlineMarkdown
import si.lanisce.lani.ui.stage.LocalStage
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.FlameOrange
import si.lanisce.lani.ui.theme.TriglavRed

/**
 * The school sheet a speaking or writing item is on (SheetScreen): its [unit], the [sheet] (a worksheet's id, "test", a
 * homework's id), whether it is the [test] (the checks show only once checked; a speaking item rated by the learner
 * doesn't count). Null off a sheet.
 */
internal data class SheetPlace(val unit: CourseUnit, val sheet: String, val test: Boolean)

internal val LocalSheetPlace = staticCompositionLocalOf<SheetPlace?> { null }

/**
 * What the teacher asks ([Exercise.Speaking.ask], [Exercise.Writing.ask]): her line in a bubble, said in her voice as it
 * comes (her clip, else the node's, else the phone's) and again with 🔊; its words looked up with a tap.
 */
@Composable
internal fun AskedLine(vm: AppViewModel, ask: String) {
    val stage = LocalStage.current
    val voice = remember(ask) { heardVoice(stage, vm.speaker, ask) }
    LaunchedEffect(ask) { if (vm.speaker.available) vm.speaker.sayIn(ask, null, voiceName = voice) }
    val look = LocalLook.current
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("💬", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 10.dp))
            ExText(
                AnnotatedString(ask), remember(ask, look) { look?.taps?.model(ask).orEmpty() },
                style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic), modifier = Modifier.weight(1f), tag = "ask",
            )
            SpeakButton(vm.speaker, ask, Modifier.padding(start = 8.dp), voiceName = voice)
        }
    }
}

/** How a check is shown: [LIVE] ticked as the text meets it (a worksheet), [PLAIN] a list to keep to (the test), [FINAL] once checked. */
internal enum class CheckShow { LIVE, PLAIN, FINAL }

/**
 * A speaking or writing item's checks ([Production.Checked]): what it must use (✓ met, ✗ not, ○ not yet), how long, and
 * once checked what it should have avoided with why.
 */
@Composable
internal fun ChecksList(c: Production.Checked, show: CheckShow, modifier: Modifier = Modifier) {
    if (c.must.isEmpty() && c.min == null && c.avoid.none { it.hits.isNotEmpty() }) return
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("✅ ${bi("production.mustUse")}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (m in c.must) CheckRow(mark(m.met, show), inlineMarkdown(m.label + count(m)))
            if (c.min != null) CheckRow(mark(!c.short, show), AnnotatedString(bi("production.words", "n" to c.words, "asked" to asked(c))))
            if (show == CheckShow.FINAL) for (a in c.avoid.filter { it.hits.isNotEmpty() }) {
                CheckRow("✗" to TriglavRed, struck(a.hits.joinToString(", ")))
                Markdown(ExplainFormat.format(a.explain))
            }
        }
    }
}

/**
 * What a speaking or writing item's text lacks, in the verdict's panel: the panel takes the lower half of the screen and the
 * checks list runs under it, so with "Skoraj" the check not met could be out of sight, and the panel showed only the item's
 * `explain`. Each must not met, a length not reached, each word it should have avoided; nothing when all is met.
 */
@Composable
internal fun UnmetChecks(c: Production.Checked) {
    for (m in c.must.filter { !it.met }) CheckRow(mark(false, CheckShow.FINAL), inlineMarkdown(m.label + count(m)))
    if (c.min != null && c.short) CheckRow(mark(false, CheckShow.FINAL), AnnotatedString(bi("production.words", "n" to c.words, "asked" to asked(c))))
    for (a in c.avoid.filter { it.hits.isNotEmpty() }) CheckRow("✗" to TriglavRed, struck(a.hits.joinToString(", ")))
}

/** How far a must that wants more than one has come: " (1/2)". */
private fun count(m: Production.MustMark) = if (m.n > 1) " (${m.count.coerceAtMost(m.n)}/${m.n})" else ""

/** The length asked: "50–110", "25+". */
private fun asked(c: Production.Checked) = c.max?.let { "${c.min}–$it" } ?: "${c.min}+"

private fun struck(s: String) = androidx.compose.ui.text.buildAnnotatedString {
    withStyle(androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough, color = TriglavRed)) { append(s) }
}

private fun mark(met: Boolean, show: CheckShow) = when {
    met && show != CheckShow.PLAIN -> "✓" to AlpineGreen
    show == CheckShow.FINAL -> "✗" to TriglavRed
    else -> "○" to FlameOrange
}

@Composable
private fun CheckRow(mark: Pair<String, androidx.compose.ui.graphics.Color>, text: AnnotatedString) {
    Row(Modifier.semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.Top) {
        Text(mark.first, style = MaterialTheme.typography.titleSmall, color = mark.second, modifier = Modifier.width(22.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * A unit's speaking item (companion/COURSE.md, "Speaking and writing"): the module's speak exercise (ui/SpeakExercise.kt:
 * the phone's recognizer or the node's, three tries, 💡, say it again) with what the teacher asks under the task, its takes
 * graded by [Production.heard] (a form people say right, an open answer by its checks), and once heard what it used. With
 * no microphone or recognizer the learner rates themselves: practice, which a test doesn't count (said so beside it).
 */
@Composable
internal fun SpeakingEx(vm: AppViewModel, ex: Exercise.Speaking, locked: Boolean, done: (Outcome) -> Unit) {
    val place = LocalSheetPlace.current
    val lang = L10n.pair.target
    val speak = remember(ex) { ex.asSpeak() }
    val grader = remember(ex) { SpeechGrader { a, c -> Production.heard(ex, a, c, lang) } }
    var heard by remember { mutableStateOf<Outcome?>(null) }
    SpeakEx(
        vm, speak, locked,
        done = { o ->
            val checked = if (ex.must.isNotEmpty() && !o.rated) o.copy(checks = Production.check(o.answer, ex.must, ex.avoid, ex.minWords?.let { Length(it) }, lang)) else o
            heard = checked
            done(checked)
        },
        grader = grader,
        // an open answer: the node's recognizer isn't told the model, or it would hear it
        steer = ex.say.takeIf { ex.must.isEmpty() },
        asked = ex.ask?.let { a -> { AskedLine(vm, a) } },
        ratedNote = if (place?.test == true) "📝 ${bi("production.ratedNotCounted")}" else null,
    )
    if (locked) heard?.checks?.let { ChecksList(it, CheckShow.FINAL, Modifier.padding(top = 8.dp)) }
}

/**
 * A unit's writing item (companion/COURSE.md, "Speaking and writing"): the task, what the teacher asks, what the text must
 * use (ticked as it is met on a worksheet, a list to keep to in the test), the field with the language's letters under it
 * and the words counted; "Preveri · Check" checks it at once by the rules ([Production.verdict]: the item's verdict), then
 * the checks, the model and the tutor's feedback, which comes when it comes (offline: from the outbox, into the chat) and
 * never holds the learner up. A child says it first ([ChildWritingEx]).
 */
@Composable
internal fun WritingEx(vm: AppViewModel, ex: Exercise.Writing, index: Int, locked: Boolean, done: (Outcome) -> Unit) {
    if (vm.child) return ChildWritingEx(vm, ex, locked, done)
    val place = LocalSheetPlace.current
    val lang = L10n.pair.target
    var value by remember { mutableStateOf(TextFieldValue("")) }
    var conv by rememberSaveable { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Production.Checked?>(null) }
    val checked = remember(value.text) { Production.check(value.text, ex.must, ex.avoid, ex.length, lang) }
    // the task, a few lines: smaller than a question's prompt, its words looked up as a prompt's
    ex.instruction?.let {
        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
    val look = LocalLook.current
    val shown = remember(ex.prompt) { inlineMarkdown(ex.prompt) }
    ExText(shown, remember(shown, look) { look?.taps?.prompt(shown.text, shown.emphasized()).orEmpty() }, style = MaterialTheme.typography.titleLarge, tag = "prompt")
    look?.let { WordTip(it) }
    ex.ask?.let {
        Spacer(Modifier.height(12.dp))
        AskedLine(vm, it)
    }
    Spacer(Modifier.height(12.dp))
    ChecksList(result ?: checked, if (locked) CheckShow.FINAL else if (place?.test == true) CheckShow.PLAIN else CheckShow.LIVE)
    Spacer(Modifier.height(12.dp))
    NoSuggestions {
        OutlinedTextField(
            value = value, onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            enabled = !locked && result == null,
            placeholder = { Text(bi("playerScreen.writeSlovene")) },
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false),
        )
    }
    if (result == null && !locked) {
        DiacriticChips(value, { value = it })
        Spacer(Modifier.height(12.dp))
        BigButton(bi("playerScreen.check"), enabled = checked.words > 0, onClick = {
            val c = Production.check(value.text, ex.must, ex.avoid, ex.length, lang)
            result = c
            val v = Production.verdict(c)
            // the tutor reads it when there is one (offline: when reachable); the verdict is the rules', now
            if (vm.bridgeUrl != null && place != null) conv = vm.chat.requestFeedback(value.text, feedbackData(place, index, ex, value.text, v, c))
            done(Outcome(v, value.text.trim(), null, ex.explain, checks = c))
        })
        return
    }
    Spacer(Modifier.height(12.dp))
    Model(vm, ex.model)
    TutorFeedback(vm, conv)
}

/** A writing item's model text: "📝 Primer · A model answer", heard with 🔊, its words looked up. */
@Composable
private fun Model(vm: AppViewModel, model: String) {
    val look = LocalLook.current
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📝 ${bi("production.model")}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                SpeakButton(vm.speaker, model, voiceName = heardVoice(LocalStage.current, vm.speaker, model))
            }
            ExText(AnnotatedString(model), remember(model, look) { look?.taps?.model(model).orEmpty() }, style = MaterialTheme.typography.bodyLarge, tag = "model")
        }
    }
}

/** The tutor's feedback on a writing item ([conv]: its request), under the text: on its way, or there; none without a tutor. */
@Composable
private fun TutorFeedback(vm: AppViewModel, conv: String?) {
    conv ?: return
    val reply = vm.chat.gradedReplies[conv]
    Spacer(Modifier.height(12.dp))
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (reply == null) Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.link != Link.OFFLINE) CircularProgressIndicator(Modifier.size(18.dp).padding(end = 0.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(if (vm.link == Link.OFFLINE) "📶 ${bi("production.tutorLater")}" else bi("production.tutorReading"), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("🧑‍🏫 ${bi("production.tutorSays")}", style = MaterialTheme.typography.labelLarge)
                Markdown(reply.text)
            }
        }
    }
}

/** What the tutor gets with a writing item's text (kind answer; the guide's "answer" with course): the verdict is the app's. */
internal fun feedbackData(place: SheetPlace, index: Int, ex: Exercise.Writing, text: String, verdict: Verdict, c: Production.Checked): JsonObject = buildJsonObject {
    put("course", buildJsonObject {
        put("language", place.unit.language)
        put("unit", place.unit.id)
        put("level", place.unit.level)
        put("sheet", place.sheet)
        put("index", index)
    })
    put("type", "writing")
    put("exercise_index", index)
    put("prompt", ex.prompt)
    ex.ask?.let { put("ask", it) }
    put("rubric", rubricOf(ex, place.unit.level))
    put("answer", text.take(2000))
    put("verdict", verdict.name.lowercase())
    put("checks", Production.json(c))
    put("model", ex.model)
}

/** The rubric the tutor reads: what it must use, what it must avoid, how long, the level. */
internal fun rubricOf(ex: Exercise.Writing, level: String): String = listOfNotNull(
    "Level $level.",
    ex.must.takeIf { it.isNotEmpty() }?.joinToString("; ", "Must use: ", ".") { it.label },
    ex.avoid.takeIf { it.isNotEmpty() }?.joinToString("; ", "Avoid: ", ".") { it.explain.lineSequence().first() },
    ex.length?.let { l -> "Length: ${l.min}${l.max?.let { "–$it" } ?: "+"} words." },
).joinToString(" ")

/**
 * A child's writing item (companion/COURSE.md, "A child's version"): spoken first, as a speaking item of its model and its
 * checks, which counts; then, if they like, "✍️ Napiši še · Write it too": the same few words written, checked by the
 * rules, just for practice (the result stays).
 */
@Composable
private fun ChildWritingEx(vm: AppViewModel, ex: Exercise.Writing, locked: Boolean, done: (Outcome) -> Unit) {
    val spoken = remember(ex) { ex.spoken() }
    SpeakingEx(vm, spoken, locked, done)
    if (!locked) return
    var open by rememberSaveable { mutableStateOf(false) }
    var value by remember { mutableStateOf(TextFieldValue("")) }
    var tried by remember { mutableStateOf<Production.Checked?>(null) }
    val lang = L10n.pair.target
    Spacer(Modifier.height(12.dp))
    if (!open) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("✍️ ${bi("production.writeToo")}") }
        return
    }
    NoSuggestions {
        OutlinedTextField(
            value = value, onValueChange = { value = it; tried = null },
            modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
            placeholder = { Text(bi("playerScreen.writeSlovene")) },
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false),
        )
    }
    DiacriticChips(value, { value = it; tried = null })
    Spacer(Modifier.height(8.dp))
    val t = tried
    if (t != null) ChecksList(t, CheckShow.FINAL)
    else OutlinedButton(onClick = { tried = Production.check(value.text, ex.must, ex.avoid, ex.length, lang) }, enabled = value.text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text(bi("playerScreen.check"))
    }
    Text(bi("speakExercise.justPractice"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
