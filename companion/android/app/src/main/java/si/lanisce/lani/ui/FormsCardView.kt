package si.lanisce.lani.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.FormsAsk
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.FormsCard
import si.lanisce.lani.data.FormsCell
import si.lanisce.lani.data.FormsCellState
import si.lanisce.lani.data.FormsGap
import si.lanisce.lani.data.FormsSection
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.ItemMark
import si.lanisce.lani.data.WordEntry
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.ui.school.verdictColor
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.TriglavRed

// A forms card on screen ("📋 Oblike · Forms", data/FormsCard.kt): the word at the top (its picture, its base form, what it
// means), then the sections of its table with a gap asked now (biti's present; its negative, future, past and imperative
// hidden in the test): the forms the school has introduced, this review's part as gaps (the one asked framed), the others
// shown; the forms not introduced yet greyed, to read ahead. Each gap answered under the table, chosen among
// three or typed once the card is secure ("✋ Izberi raje · Let me choose" goes back), and marked at once in the table and
// under it; a right one goes on by itself, a wrong one at "Naprej · Next". At the end the table whole, the rows asked
// read by the narrator together, each row's own 🔊 (a clip made on demand), and the card's outcome for the run (its gaps
// as the sheet's items: they count on the grammar book's pages, FormsCard.ruleAnswers); "📋 Vse oblike · All forms" there
// opens the whole table to read (ui/FormsOverviewSheet.kt).

/** How long a right gap stays marked before the next comes. */
private const val RIGHT_MS = 700L

/** The width of a row's label ("2. koga? česa?"). */
private val LABEL = 76.dp

/**
 * [ex] played gap by gap; [done] with the card's outcome at the end, [onStep] with the gaps answered so far (the chat's
 * attachment while the card is on).
 */
@Composable
internal fun FormsCardEx(vm: AppViewModel, ex: Exercise.FormsTable, locked: Boolean, done: (Outcome) -> Unit, onStep: (List<ItemMark>) -> Unit = {}) {
    val marks = remember { mutableStateListOf<ItemMark>() }
    /** The gap on screen's mark, until it is left. */
    var mark by remember { mutableStateOf<ItemMark?>(null) }
    var at by remember { mutableIntStateOf(0) }
    /** Typed: the card is secure, until "Let me choose". */
    var typed by remember { mutableStateOf(ex.typed) }
    var reported by remember { mutableStateOf(false) }
    val over = locked || at >= ex.gaps.size
    val next: () -> Unit = {
        mark?.let { marks += it }
        mark = null
        onStep(marks.toList())
        at++
        if (at >= ex.gaps.size && !reported) {
            reported = true
            // the rows asked, said together by the narrator (a clip made on demand, as any line)
            if (ex.said.isNotBlank()) vm.speaker.say(ex.said, voiceName = Clips.FEMALE)
            done(Outcome(FormsCard.verdict(marks), marks.joinToString(" · ") { it.answer }, null, null, sheet = marks.toList()))
        }
    }
    LaunchedEffect(mark) {
        // the chat's attachment has the gap on screen too, once answered
        onStep(marks.toList() + listOfNotNull(mark))
        val m = mark ?: return@LaunchedEffect
        if (m.verdict != Verdict.WRONG) {
            delay(RIGHT_MS)
            if (mark === m) next()
        }
    }
    // QA reads the sections shown (and hidden: no gap asked in them), which gap is asked, its right form and its options
    // (a debug or qa build)
    if (si.lanisce.lani.BuildConfig.QA) LaunchedEffect(ex) {
        android.util.Log.i("FormsCard", "sections ${ex.sections.joinToString("|") { it.title }} hidden ${ex.hidden.joinToString("|")}")
    }
    if (si.lanisce.lani.BuildConfig.QA) LaunchedEffect(at, typed) {
        ex.gaps.getOrNull(at)?.let { g ->
            android.util.Log.i("FormsCard", "gap ${at + 1}/${ex.gaps.size} ${g.key} «${g.answer}» [${g.options.joinToString("|")}] ${if (typed && !g.chosen) "typed" else "chosen"}")
        }
    }
    val byKey = ex.gaps.mapIndexedNotNull { i, g -> (marks.getOrNull(i) ?: mark?.takeIf { i == at })?.let { g.key to it } }.toMap()
    val current = ex.gaps.getOrNull(at)?.key?.takeIf { !over }
    // the end: every gap answered (the run locks the card then; its whole table and 🔊 stay)
    val end = ex.gaps.isNotEmpty() && marks.size == ex.gaps.size

    Text("📋 ${bi("formsCard.title")}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Head(ex)
    // the pages of its gaps on the cheat sheet, one tap away (help, not a hint; the top bar's 📋 opens them too)
    TextButton(onClick = { vm.cheat.show(ex.pages) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("📖 ${bi("formsCard.help")}") }
    if (ex.fresh) {
        Text("🆕 ${bi("formsCard.fresh")}", style = MaterialTheme.typography.bodyMedium, color = AlpineGreen, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
    }
    for (s in ex.sections) Section(vm, s, byKey, current, end)
    if (ex.sections.any { s -> s.rows.any { r -> r.cells.any { it?.state == FormsCellState.PREVIEW } } }) {
        Text("🔒 ${bi("formsCard.previewNote")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
    if (ex.checked) Text("🤖 ${bi("words.machineWritten")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (!over) {
        val g = ex.gaps[at]
        // the gap on screen kept in view as the card scrolls in the run
        Column(Modifier.then(revealFormsOn(at, mark != null))) {
            Spacer(Modifier.height(10.dp))
            val typing = typed && !g.chosen
            Asked(ex.lemma, g, at, ex.gaps.size, typing && mark == null)
            val m = mark
            if (m == null) {
                if (typing) {
                    TypedGap(g) { a -> mark = FormsCard.grade(g, a, typed = true) }
                    TextButton(onClick = { typed = false }, modifier = Modifier.heightIn(min = 48.dp).padding(top = 4.dp)) { Text("✋ ${bi("adaptive.letMeChoose")}") }
                } else ChosenGap(g) { a -> mark = FormsCard.grade(g, a, typed = false).let { if (ex.typed && !g.chosen) it.copy(hinted = true) else it } }
            } else {
                GapDone(vm, g, m)
                if (m.verdict == Verdict.WRONG) {
                    Spacer(Modifier.height(8.dp))
                    BigButton(bi("common.next"), onClick = next)
                }
            }
        }
    } else if (end) {
        Spacer(Modifier.height(12.dp))
        Column(Modifier.then(revealFormsOn(ex.gaps.size, true, after = 450))) { Whole(vm, ex, marks) }
    }
    Spacer(Modifier.height(8.dp))
}

/** Scrolls what it's on into view each time [step] or [answered] change ([after] ms later: what slides in below first). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun revealFormsOn(step: Int, answered: Boolean, after: Long = 80L): Modifier {
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(step, answered) {
        delay(after)
        bring.bringIntoView()
    }
    return Modifier.bringIntoViewRequester(bring)
}

/** The word: its picture, its base form big, what it is and what it means. */
@Composable
private fun Head(ex: Exercise.FormsTable) = FormsHead(ex.emoji, ex.lemma, ex.gender, ex.pos, ex.meaning)

/** A word's head on its forms card and its overview: its [emoji], its [lemma] big with a noun's [gender], its [pos] and [meaning]. */
@Composable
internal fun FormsHead(emoji: String?, lemma: String, gender: String?, pos: String, meaning: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji ?: "📋", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.width(12.dp))
        Column {
            // the gender in the learner's base, as the word card says it ("m.", not "m. · m.")
            val g = when (gender) {
                "m" -> inBase("words.genderM")
                "f" -> inBase("words.genderF")
                "n" -> inBase("words.genderN")
                else -> null
            }
            Text(lemma + (g?.let { " · $it" } ?: ""), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            val what = listOfNotNull(pos.takeIf { it.isNotBlank() }?.let { si.lanisce.lani.ui.words.posLabel(it) }, meaning?.takeIf { it.isNotBlank() })
            if (what.isNotEmpty()) Text(what.joinToString(" — "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A part of the table: its title, its columns' heads, its rows (a label, its cells; at the [end] each row's 🔊). [marks]:
 * the gaps answered by key; [current]: the gap asked now.
 */
@Composable
private fun Section(vm: AppViewModel, s: FormsSection, marks: Map<String, ItemMark>, current: String?, end: Boolean) {
    Text(s.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
    val labelled = s.rows.any { it.label.isNotBlank() }
    Row(Modifier.fillMaxWidth()) {
        if (labelled) Spacer(Modifier.width(LABEL))
        for (c in s.columns) Text(c, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (end) Spacer(Modifier.width(40.dp))
    }
    for (r in s.rows) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (labelled) Text(r.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(LABEL))
            for (cell in r.cells) {
                Column(Modifier.weight(1f).padding(end = 4.dp)) {
                    if (cell == null) Text("–", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Cell(cell, marks[cell.key], cell.key == current, end)
                }
            }
            if (end) Row(Modifier.width(40.dp)) { SpeakButton(vm.speaker, r.said) }
        }
    }
}

/** A cell: the words before it small, then its form (greyed to read ahead; a gap's ____ until answered, framed when asked now). */
@Composable
private fun Cell(cell: FormsCell, mark: ItemMark?, now: Boolean, end: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (cell.before.isNotBlank()) {
        Text(cell.before, style = MaterialTheme.typography.labelSmall, color = if (cell.state == FormsCellState.PREVIEW) muted.copy(alpha = 0.5f) else muted)
    }
    when (cell.state) {
        FormsCellState.GIVEN -> Text(cell.form + cell.after, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        FormsCellState.TESTED -> Text(cell.form + cell.after, style = MaterialTheme.typography.bodyMedium)
        FormsCellState.PREVIEW -> Text(
            cell.form + cell.after, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = muted.copy(alpha = 0.55f),
            modifier = Modifier.semantics { contentDescription = "${cell.text}, ${bi("formsCard.preview")}" },
        )
        FormsCellState.GAP -> when {
            mark != null -> {
                val color = if (mark.verdict == Verdict.WRONG) AlpineGreen else verdictColor(mark.verdict)
                Text(cell.form + cell.after, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
                if (mark.verdict == Verdict.WRONG) Text("✗ ${mark.answer.ifBlank { "…" }}", style = MaterialTheme.typography.labelSmall, color = TriglavRed)
            }
            end -> Text(cell.form + cell.after, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            now -> Surface(
                color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small,
                modifier = Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
            ) { Text("____${cell.after}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp)) }
            else -> Text("____${cell.after}", style = MaterialTheme.typography.bodyMedium, color = muted)
        }
    }
}

/**
 * What a gap asks: the word and where the gap stands ("📋 delati · 2/6 · sedanjik · present — dvojina · dual": the card's
 * head may be scrolled away by now), the gap with the words around it, what to do.
 */
@Composable
private fun Asked(lemma: String, g: FormsGap, i: Int, of: Int, typed: Boolean) {
    Text("📋 $lemma · ${i + 1}/$of · ${g.where}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    Text(listOf(g.before, "____" + g.after).filter { it.isNotBlank() }.joinToString(" "), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(bi(if (typed) "formsCard.type" else "formsCard.pick"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
}

/** A gap's three options: a tap answers. */
@Composable
private fun ChosenGap(g: FormsGap, answer: (String) -> Unit) {
    for (o in g.options) {
        Card(onClick = { answer(o) }, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), shape = MaterialTheme.shapes.medium) {
            Text(o, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(18.dp))
        }
    }
}

/** A gap typed: the field (the language learned's keyboard, no suggestions), č š ž, "Preveri · Check". */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TypedGap(g: FormsGap, answer: (String) -> Unit) {
    var value by remember(g.key) { mutableStateOf(TextFieldValue("")) }
    // with the keyboard up, the field, its letters and "Preveri · Check" scrolled into view together; the keyboard's ✓ checks too
    val bring = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
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
                placeholder = { Text(bi("playerScreen.typeAnswer")) },
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

/** A gap answered: ✓ or ✗ with what was answered, the right form (bold, its 🔊). */
@Composable
private fun GapDone(vm: AppViewModel, g: FormsGap, m: ItemMark) {
    val color = verdictColor(m.verdict)
    val right = listOf(g.before, g.answer + g.after).filter { it.isNotBlank() }.joinToString(" ")
    Surface(
        color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(right, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (m.verdict == Verdict.WRONG) AlpineGreen else MaterialTheme.colorScheme.onSurface)
                if (m.verdict != Verdict.CORRECT) {
                    Text("${if (m.verdict == Verdict.WRONG) "✗" else "≈"} ${m.answer.ifBlank { "…" }}", style = MaterialTheme.typography.bodyMedium, color = if (m.verdict == Verdict.WRONG) TriglavRed else color)
                }
            }
            Text(if (m.verdict == Verdict.WRONG) "❌" else if (m.verdict == Verdict.ALMOST) "🟡" else "✅")
            SpeakButton(vm.speaker, right, Modifier.padding(start = 6.dp))
        }
    }
}

/**
 * The end: how the part went, the rows asked said together (their 🔊), each row's own 🔊 in the table above, and the
 * whole table to read ("📋 Vse oblike · All forms": the overview, every section, the card's hidden ones too).
 */
@Composable
private fun Whole(vm: AppViewModel, ex: Exercise.FormsTable, marks: List<ItemMark>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧾 ${bi("formsCard.whole")}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (ex.said.isNotBlank()) SpeakButton(vm.speaker, ex.said)
            }
            val right = marks.count { it.verdict != Verdict.WRONG }
            Text(bi("formsCard.score", "right" to right, "of" to marks.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(bi("formsCard.rowsHint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            ex.data?.let { d ->
                TextButton(onClick = { vm.formsCards.open(d) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("📋 ${bi("formsCard.allForms")}") }
            }
        }
    }
}

/**
 * "📋 Vse oblike · All forms" on a word's card ([e]: a noun, a verb or an adjective; the home language's, not a word of an
 * exercise still open): its whole table to read at once (the overview, ui/FormsOverviewSheet.kt), where "🃏 Vadi oblike ·
 * Practise the forms" makes it a card.
 */
@Composable
fun AllFormsButton(vm: AppViewModel, e: WordEntry) {
    val pos = e.pos ?: return
    if (!vm.formsCards.viewable() || pos !in FormsCard.POS) return
    OutlinedButton(onClick = { vm.formsCards.open(e) }, modifier = Modifier.padding(top = 6.dp).heightIn(min = 48.dp)) { Text("📋 ${bi("formsCard.allForms")}") }
}

/**
 * The overview's way to practise [lemma]'s forms (its card [itemId], if the learner has the word): "🃏 Vadi oblike ·
 * Practise the forms" makes its forms card at once (the node: [FormsCardController.make]), then "▶ Vadi zdaj · Practise
 * now"; a word that has one says so and offers it at once; nothing of it introduced yet, or too few forms: says which.
 */
@Composable
internal fun PractiseForms(vm: AppViewModel, lemma: String, itemId: String?) {
    if (!vm.formsCards.on()) return
    val card = vm.formsCards.cardOf(lemma)
    val ask = vm.formsCards.asks[FormsCard.key(lemma)]
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            card != null -> {
                Text("📋 ${bi("formsCard.inReviews")}", style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = { vm.playFormsCard(card.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("▶ ${bi("formsCard.playNow")}") }
            }
            ask == FormsAsk.Making -> Text("📋 ${bi("formsCard.making")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ask is FormsAsk.None -> Text(
                "🤷 ${bi(if (ask.early) "formsCard.noneYet" else "formsCard.noTable")}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                if (ask == FormsAsk.Offline) Text("📶 ${bi("formsCard.offline")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = { vm.formsCards.make(lemma, itemId) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("🃏 ${bi("formsView.practise")}") }
            }
        }
    }
}
