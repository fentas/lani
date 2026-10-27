package si.lanisce.lani.ui.school

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.Hints
import si.lanisce.lani.data.ItemMark
import si.lanisce.lani.data.SheetItem
import si.lanisce.lani.data.Sheets
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.AnswerDiffText
import si.lanisce.lani.ui.AnswerKeyboard
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.BlockText
import si.lanisce.lani.ui.DiacriticChips
import si.lanisce.lani.ui.ExText
import si.lanisce.lani.ui.ExplainFormat
import si.lanisce.lani.ui.LocalLook
import si.lanisce.lani.ui.Markdown
import si.lanisce.lani.ui.NoSuggestions
import si.lanisce.lani.ui.Outcome
import si.lanisce.lani.ui.Prompt
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.emphasized
import si.lanisce.lani.ui.hintText
import si.lanisce.lani.ui.stage.LocalStage
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.FlameOrange
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold

// The school's four worksheet types on screen (companion/COURSE.md, "The school's exercise types"): a page of work at
// once, help on it (a 💡 per item, its words looked up as an exercise's, č š ž), checked all at once; then each item
// marked right (✓), almost or wrong with its correction right where it was answered (what was typed set against what is
// right, its why), the count under the sheet, and the sheet's verdict in the run's feedback.

/**
 * A text of a sheet in the language learned (the gap text's words, a table's label or given cell, a list's line given):
 * its words looked up as the exercises' are (ui/ExerciseLook: a tap, never what the sheet tests while it's open, the new
 * ones dotted); [line]: the sentence a word's card shows.
 */
@Composable
private fun SheetText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified, line: String? = null, tag: String = "sheet") {
    val look = LocalLook.current
    val shown = remember(text) { AnnotatedString(text) }
    ExText(shown, remember(text, look) { look?.taps?.prompt(text).orEmpty() }, modifier, style = style, color = color, line = line?.let { l -> { l } }, tag = tag)
}

/** The colour of an item's verdict. */
internal fun verdictColor(v: Verdict?): Color = when (v) {
    Verdict.CORRECT -> AlpineGreen
    Verdict.ALMOST -> FlameOrange
    Verdict.WRONG -> TriglavRed
    null -> Color.Unspecified
}

/** "✓", "≈", "✗": an item's verdict, for its mark. */
private fun verdictMark(v: Verdict): String = when (v) {
    Verdict.CORRECT -> "✓"
    Verdict.ALMOST -> "≈"
    Verdict.WRONG -> "✗"
}

/** An item's verdict in words, for TalkBack ("prav · right"). */
private fun verdictSaid(v: Verdict): String = when (v) {
    Verdict.CORRECT -> bi("school.itemRight")
    Verdict.ALMOST -> bi("school.itemAlmost")
    Verdict.WRONG -> bi("school.itemWrong")
}

/**
 * While a sheet is on screen, the cheat sheet's first open over it keeps what its items hold ([now]: each item's answer
 * as it is), so the items answered after it can be told at the check ([lookedUp]).
 */
@Composable
private fun ItemsForCheatSheet(vm: AppViewModel, now: () -> List<String>) {
    DisposableEffect(now) {
        vm.cheat.itemsOnScreen(now)
        onDispose { vm.cheat.itemsGone(now) }
    }
}

/** For each item as it is now ([now]), at the check: answered after the cheat sheet was opened over the sheet (right with help). */
private fun lookedUp(vm: AppViewModel, now: List<String>): List<Boolean> = vm.cheat.itemsAfter(now)

/** The sheet's outcome for the run: its items' marks, the verdict over them, what it explains. */
private fun outcomeOf(marks: List<ItemMark>, explain: String?): Outcome {
    val v = marks.map { it.verdict }
    return Outcome(Sheets.verdict(v), Sheets.scoreText(v), null, explain, hints = marks.count { it.hinted }, sheet = marks)
}

/**
 * A typed item's field: small and inline (a gap, a cell), or the line's width; once checked, outlined in its verdict's
 * colour with its mark on its corner ([VerdictBadge]: ✓, ≈, ✗; it takes no room from what was typed), TalkBack saying the
 * verdict (the correction under it says the rest).
 */
@Composable
private fun SheetField(
    value: TextFieldValue, onChange: (TextFieldValue) -> Unit, label: String, mark: ItemMark?, locked: Boolean, active: Boolean,
    onFocus: () -> Unit, modifier: Modifier = Modifier, minWidth: Int = 64,
) {
    val color = mark?.let { verdictColor(it.verdict) } ?: if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(modifier.width(IntrinsicSize.Min)) {
        NoSuggestions {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                enabled = !locked,
                singleLine = true,
                keyboardOptions = AnswerKeyboard,
                textStyle = TextStyle(
                    color = mark?.let { verdictColor(it.verdict) } ?: MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold, fontSize = MaterialTheme.typography.titleMedium.fontSize,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = minWidth.dp)
                    .border(BorderStroke(if (active || mark != null) 2.dp else 1.dp, color), MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .onFocusChanged { if (it.isFocused) onFocus() }
                    .semantics {
                        contentDescription = label
                        mark?.let { stateDescription = verdictSaid(it.verdict) }
                    },
            )
        }
        mark?.let { VerdictBadge(it.verdict, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-7).dp)) }
    }
}

/** An item's verdict as a small round mark on its field's corner: ✓ green, ≈ orange, ✗ red (TalkBack: the field's state). */
@Composable
private fun VerdictBadge(v: Verdict, modifier: Modifier = Modifier) {
    Surface(color = verdictColor(v), shape = CircleShape, modifier = modifier.size(18.dp).clearAndSetSemantics { }) {
        Box(contentAlignment = Alignment.Center) {
            Text(verdictMark(v), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Help on the item being filled ([item], number [n]): its hint (the rule, never the answer), else the answer's shape;
 * opening it marks the item hinted ([onUsed]: a right answer then pays like an almost).
 */
@Composable
private fun ItemHint(n: Int, item: SheetItem?, shown: Boolean, onShow: () -> Unit) {
    item ?: return
    if (!shown) {
        OutlinedButton(onClick = onShow, modifier = Modifier.heightIn(min = 48.dp)) { Text("💡 ${bi("school.hintFor", "n" to n)}") }
        return
    }
    val text = item.hint ?: Hints.hint(1, "", item.accept)?.line ?: return
    Surface(color = XpGold.copy(alpha = 0.16f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text("💡 $n: $text", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

/**
 * The first item of a checked sheet that isn't right ([at], -1: none): brought into view once the sheet is checked (the
 * feedback slid in under it, the sheet's room shrunk), and again by a tap on the count under the sheet ([CheckedCount]).
 */
@OptIn(ExperimentalFoundationApi::class)
private class FirstWrong(val at: Int, val bring: BringIntoViewRequester, private val density: Density) {
    /** The size of what it is on, and how far above it to show too (px). */
    private var size = IntSize.Zero
    private var leadPx = 0f

    /**
     * On item [i] (the whole item where it can: its line, its field, its correction): where the first one not right is
     * brought into view, with [lead] above it too (a gap's correction: the sentence over it).
     */
    fun on(i: Int, lead: Dp = 0.dp): Modifier {
        if (i != at) return Modifier
        leadPx = with(density) { lead.toPx() }
        return Modifier.bringIntoViewRequester(bring).onSizeChanged { size = it }
    }

    /** Brings it into view, what is above it by its lead too. */
    suspend fun show() = bring.bringIntoView(Rect(0f, -leadPx, size.width.toFloat(), size.height.toFloat()))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberFirstWrong(verdicts: List<Verdict>?): FirstWrong {
    val bring = remember { BringIntoViewRequester() }
    val at = verdicts?.indexOfFirst { it != Verdict.CORRECT } ?: -1
    val density = LocalDensity.current
    val first = remember(at, bring, density) { FirstWrong(at, bring, density) }
    LaunchedEffect(first) {
        if (at < 0) return@LaunchedEffect
        delay(450) // the feedback slid in, the sheet's room shrunk
        first.show()
    }
    return first
}

/**
 * Under a checked sheet, its count: "✏️ 3 od 8 narobe, 1 skoraj · 3 of 8 wrong, 1 almost"; a tap brings the first one not
 * right into view ([first]). Nothing when every item is right (the feedback says so).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CheckedCount(verdicts: List<Verdict>, first: FirstWrong) {
    val wrong = verdicts.count { it == Verdict.WRONG }
    val almost = verdicts.count { it == Verdict.ALMOST }
    if (wrong + almost == 0) return
    val scope = rememberCoroutineScope()
    Text(
        "✏️ ${bi("school.wrongOf", "n" to wrong, "of" to verdicts.size, "almost" to almost)}",
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 8.dp)
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = bi("school.toFirstWrong")) { scope.launch { first.show() } }
            .padding(horizontal = 4.dp, vertical = 12.dp),
    )
}

/** An item's own why, in shape (one rule line, the examples), its words looked up as the feedback's (ExplainFormat). */
@Composable
private fun ItemWhy(text: String, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val explained: BlockText = { styled, style, m ->
        ExText(styled, remember(styled, look) { look?.taps?.explanation(styled.text, styled.emphasized()).orEmpty() }, m, style = style.merge(MaterialTheme.typography.bodySmall), tag = "explain")
    }
    Markdown(ExplainFormat.format(text), modifier, block = explained)
}

/**
 * Item [item]'s correction where it was answered, once checked ([mark]); nothing for a right one (its field's ✓). What
 * was typed set against what is right ([AnswerDiffText]: a transform's answer without its person in brackets set
 * against the whole sentence, the person quiet, [Sheets.shown]); under it why: an almost's slip (a missing č, a typo),
 * a wrong one's own why ([withWhy]: a table's whys go under its row). [style]: smaller in a table's narrow cell.
 */
@Composable
private fun ItemCorrection(
    item: SheetItem?, mark: ItemMark, modifier: Modifier = Modifier, withWhy: Boolean = true,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    if (mark.verdict == Verdict.CORRECT) return
    val (expected, optional) = remember(item, mark) { item?.let { Sheets.shown(it, mark) } ?: (mark.expected to 0) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AnswerDiffText(mark.answer, expected, mark.verdict, optional = optional, style = style)
        if (withWhy) ItemSlip(item, mark)
    }
}

/** Why item [item] ([mark]) isn't right: an almost's slip ("Pazi na strešice · Mind the č / š / ž"), a wrong one's own why. */
@Composable
private fun ItemSlip(item: SheetItem?, mark: ItemMark) {
    when (mark.verdict) {
        Verdict.ALMOST -> hintText(mark.hint)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FlameOrange) }
        Verdict.WRONG -> item?.explain?.let { ItemWhy(it) }
        Verdict.CORRECT -> Unit
    }
}

// --- gaptext ---------------------------------------------------------------------------------------------------------

/** The end of a sentence at the end of a word ("kuhinji.", "lačen?«"). */
private val SENTENCE_END = Regex("""[.!?…]+["»“”]?$""")

/**
 * Gap [n]'s correction ([mark], [item]), on a line of its own under its sentence: its number (TalkBack: "Vrzel 3 · Gap
 * 3"), what was typed set against what is right, why, and the sentence said right (🔊).
 */
@Composable
private fun GapCorrection(vm: AppViewModel, n: Int, item: SheetItem?, mark: ItemMark, modifier: Modifier = Modifier) {
    Surface(color = verdictColor(mark.verdict).copy(alpha = 0.08f), shape = MaterialTheme.shapes.small, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            val said = bi("school.gapN", "n" to n)
            Text(
                "$n", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp).semantics { contentDescription = said },
            )
            ItemCorrection(item, mark, Modifier.weight(1f).padding(vertical = 4.dp))
            item?.takeIf { it.frame != Sheets.BLANK }?.let { SpeakButton(vm.speaker, it.said(mark.expected)) }
        }
    }
}

/** Once checked ([marks]), the corrections of gaps [gaps] not right, each on a line of its own ([GapCorrection]). */
@Composable
private fun GapCorrections(vm: AppViewModel, gaps: List<Int>, items: List<SheetItem>, marks: List<ItemMark>?, first: FirstWrong) {
    marks ?: return
    for (g in gaps) marks.getOrNull(g)?.takeIf { it.verdict != Verdict.CORRECT }?.let { mk ->
        GapCorrection(vm, g + 1, items.getOrNull(g), mk, first.on(g, lead = 96.dp))
    }
}

/**
 * A text with gaps ([Exercise.GapText]): the text laid out with a field in each gap (its words tappable), the word bank
 * under it (a tap puts a word into the gap being filled), č š ž and a 💡 for the gap being filled; "Preveri · Check"
 * checks all the gaps at once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GapTextEx(vm: AppViewModel, ex: Exercise.GapText, locked: Boolean, done: (Outcome) -> Unit) {
    val items = remember(ex) { Sheets.items(ex) }
    val parts = remember(ex) { Sheets.parts(ex.text) }
    val values = remember(ex) { mutableStateListOf<TextFieldValue>().apply { repeat(ex.gaps.size) { add(TextFieldValue("")) } } }
    val bank = remember(ex) { Sheets.bank(ex) }
    /** Which chip of the bank each gap holds. */
    val chipOf = remember(ex) { mutableStateListOf<Int?>().apply { repeat(ex.gaps.size) { add(null) } } }
    val hinted = remember(ex) { mutableStateMapOf<Int, Boolean>() }
    var active by remember(ex) { mutableIntStateOf(0) }
    var marks by remember(ex) { mutableStateOf<List<ItemMark>?>(null) }
    val stage = LocalStage.current
    val plain = remember(ex) { Sheets.filled(ex, ex.gaps.map { null }) }
    /** Each part's words and the sentence each stands in (its gaps as gaps): what a word's card shows. */
    val words = remember(ex) {
        var at = 0
        parts.map { part ->
            var i = 0
            val ws = part.split(' ').mapNotNull { w ->
                val start = at + i
                i += w.length + 1
                w.takeIf { it.isNotEmpty() }?.let { it to Sheets.sentenceAt(plain, start) }
            }
            at += part.length + Sheets.BLANK.length
            ws
        }
    }
    val now = remember(ex) { { values.map { it.text } } }
    ItemsForCheatSheet(vm, now)
    val first = rememberFirstWrong(marks?.map { it.verdict })

    Prompt("", ex.instruction ?: bi("school.fillText"))
    ex.title?.let { Text(it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        FlowRow(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // once checked, the gaps not right of each sentence corrected on a line of their own under it
            val waiting = mutableListOf<Int>()
            parts.forEachIndexed { p, _ ->
                // the words between the gaps, each on its own (punctuation stays with its word), looked up in their sentence
                words[p].forEach { (w, sentence) ->
                    SheetText(w, MaterialTheme.typography.titleMedium, Modifier.align(Alignment.CenterVertically), line = sentence, tag = "gap text")
                    if (waiting.isNotEmpty() && SENTENCE_END.containsMatchIn(w)) {
                        GapCorrections(vm, waiting.toList(), items, marks, first)
                        waiting.clear()
                    }
                }
                if (p < parts.size - 1) {
                    val g = p
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${g + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SheetField(
                            values[g], { v -> values[g] = v; if (chipOf[g] != null && v.text != bank.getOrNull(chipOf[g]!!)) chipOf[g] = null },
                            bi("school.gapN", "n" to g + 1), marks?.getOrNull(g), locked || marks != null, active == g && marks == null,
                            onFocus = { active = g },
                        )
                    }
                    waiting += g
                }
            }
            GapCorrections(vm, waiting.toList(), items, marks, first)
        }
    }
    val m = marks
    if (m != null) {
        ex.translation?.let { Text("🇬🇧 $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
        CheckedCount(m.map { it.verdict }, first)
        return
    }
    if (bank.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("🧺 ${bi("school.wordBank")}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            bank.forEachIndexed { c, w ->
                val used = c in chipOf
                FilterChip(
                    selected = false, enabled = !used && !locked,
                    onClick = {
                        val g = active.takeIf { values.getOrNull(it)?.text.isNullOrBlank() } ?: values.indexOfFirst { it.text.isBlank() }.takeIf { it >= 0 } ?: active
                        values[g] = TextFieldValue(w, androidx.compose.ui.text.TextRange(w.length))
                        chipOf[g] = c
                        // on to the next empty gap
                        values.indexOfFirst { it.text.isBlank() }.takeIf { it >= 0 }?.let { active = it }
                    },
                    label = { Text(w) },
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    DiacriticChips(values.getOrElse(active) { TextFieldValue("") }, { values[active] = it })
    Spacer(Modifier.height(10.dp))
    ItemHint(active + 1, items.getOrNull(active), hinted[active] == true) { hinted[active] = true; stage?.hint() }
    Spacer(Modifier.height(12.dp))
    if (!locked) BigButton(
        bi("playerScreen.check"), enabled = values.any { it.text.isNotBlank() },
        onClick = {
            val after = lookedUp(vm, now())
            val checked = items.mapIndexed { i, it -> Sheets.check(it, values[i].text).copy(hinted = hinted[i] == true, lookedUp = after.getOrElse(i) { false }) }
            marks = checked
            done(outcomeOf(checked, listOfNotNull(ex.explain).joinToString("\n\n").ifBlank { null }))
        },
    )
}

// --- table -----------------------------------------------------------------------------------------------------------

/** Why a table row's cells [cells] (indices of [items]) aren't right ([marks]), each once, under the row. */
@Composable
private fun RowSlips(cells: List<Int>, items: List<SheetItem>, marks: List<ItemMark>) {
    val slips = cells.mapNotNull { i ->
        val mk = marks.getOrNull(i) ?: return@mapNotNull null
        when (mk.verdict) {
            Verdict.ALMOST -> hintText(mk.hint)?.let { false to it }
            Verdict.WRONG -> items.getOrNull(i)?.explain?.let { true to it }
            Verdict.CORRECT -> null
        }
    }.distinct()
    for ((why, text) in slips) {
        if (why) ItemWhy(text) else Text(text, style = MaterialTheme.typography.bodySmall, color = FlameOrange)
    }
}

/**
 * A table to fill ([Exercise.Table]): its rows by its columns, the given cells shown (tappable words), the rest typed;
 * the rule's table one tap away ("📖", the run's help), č š ž and a 💡 for the cell being filled.
 */
@Composable
internal fun TableEx(vm: AppViewModel, ex: Exercise.Table, locked: Boolean, done: (Outcome) -> Unit) {
    val items = remember(ex) { Sheets.items(ex) }
    /** Each cell to fill: its row, its column, and its item's index. */
    val cells = remember(ex) {
        var n = 0
        ex.rows.flatMapIndexed { r, row -> row.cells.mapIndexedNotNull { c, cell -> if (cell.toFill) Triple(r, c, n++) else null } }
    }
    val index = remember(ex) { cells.associate { (r, c, i) -> (r to c) to i } }
    val values = remember(ex) { mutableStateListOf<TextFieldValue>().apply { repeat(items.size) { add(TextFieldValue("")) } } }
    val hinted = remember(ex) { mutableStateMapOf<Int, Boolean>() }
    var active by remember(ex) { mutableIntStateOf(0) }
    var marks by remember(ex) { mutableStateOf<List<ItemMark>?>(null) }
    val stage = LocalStage.current
    val now = remember(ex) { { values.map { it.text } } }
    ItemsForCheatSheet(vm, now)
    val first = rememberFirstWrong(marks?.map { it.verdict })

    Prompt(ex.prompt ?: "", ex.instruction ?: bi("school.fillTable"))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1.1f))
                for (c in ex.columns) Text(c, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
            }
            ex.rows.forEachIndexed { r, row ->
                // once checked, a cell's correction under it; the row's whys under the row (the cells are narrow)
                Row(verticalAlignment = if (marks == null) Alignment.CenterVertically else Alignment.Top) {
                    SheetText(row.label, MaterialTheme.typography.titleSmall, Modifier.weight(1.1f).then(if (marks == null) Modifier else Modifier.padding(top = 8.dp)), MaterialTheme.colorScheme.onSurfaceVariant, tag = "row")
                    row.cells.forEachIndexed { c, cell ->
                        Box(Modifier.weight(1f).padding(horizontal = 3.dp)) {
                            val i = index[r to c]
                            if (i == null) SheetText(cell.given.orEmpty(), MaterialTheme.typography.titleMedium, Modifier.then(if (marks == null) Modifier else Modifier.padding(top = 6.dp)), tag = "cell")
                            else Column(first.on(i, lead = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                SheetField(
                                    values[i], { values[i] = it }, "${row.label}, ${ex.columns.getOrNull(c).orEmpty()}", marks?.getOrNull(i), locked || marks != null,
                                    active == i && marks == null, onFocus = { active = i }, modifier = Modifier.fillMaxWidth(), minWidth = 48,
                                )
                                marks?.getOrNull(i)?.let { mk ->
                                    ItemCorrection(items.getOrNull(i), mk, withWhy = false, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                marks?.let { m -> RowSlips(row.cells.indices.mapNotNull { c -> index[r to c] }, items, m) }
            }
        }
    }
    val m = marks
    if (m != null) {
        CheckedCount(m.map { it.verdict }, first)
        return
    }
    Spacer(Modifier.height(10.dp))
    DiacriticChips(values.getOrElse(active) { TextFieldValue("") }, { values[active] = it })
    Spacer(Modifier.height(10.dp))
    ItemHint(active + 1, items.getOrNull(active), hinted[active] == true) { hinted[active] = true; stage?.hint() }
    ex.grammar?.let { page ->
        TextButton(onClick = { vm.grammar.show(page) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("📖 ${bi("school.ruleTable")}") }
    }
    Spacer(Modifier.height(8.dp))
    if (!locked) BigButton(
        bi("playerScreen.check"), enabled = values.any { it.text.isNotBlank() },
        onClick = {
            val after = lookedUp(vm, now())
            val checked = items.mapIndexed { i, it -> Sheets.check(it, values[i].text).copy(hinted = hinted[i] == true, lookedUp = after.getOrElse(i) { false }) }
            marks = checked
            done(outcomeOf(checked, ex.explain))
        },
    )
}

// --- transform -------------------------------------------------------------------------------------------------------

/** A list to change ([Exercise.Transform]): one instruction, each line given and typed whole below it. */
@Composable
internal fun TransformEx(vm: AppViewModel, ex: Exercise.Transform, locked: Boolean, done: (Outcome) -> Unit) {
    val items = remember(ex) { Sheets.items(ex) }
    val values = remember(ex) { mutableStateListOf<TextFieldValue>().apply { repeat(ex.items.size) { add(TextFieldValue("")) } } }
    val hinted = remember(ex) { mutableStateMapOf<Int, Boolean>() }
    var active by remember(ex) { mutableIntStateOf(0) }
    var marks by remember(ex) { mutableStateOf<List<ItemMark>?>(null) }
    val stage = LocalStage.current
    val now = remember(ex) { { values.map { it.text } } }
    ItemsForCheatSheet(vm, now)
    val first = rememberFirstWrong(marks?.map { it.verdict })

    Prompt("", ex.instruction)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ex.items.forEachIndexed { i, it ->
            Column(first.on(i, lead = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
                    SheetText(it.from, MaterialTheme.typography.titleMedium, Modifier.weight(1f), tag = "line")
                    SpeakButton(vm.speaker, it.from)
                }
                val arrow = ex.cue?.let { c -> "→ ($c)" } ?: "→"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(arrow, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 28.dp, end = 8.dp))
                    SheetField(
                        values[i], { v -> values[i] = v }, bi("school.lineN", "n" to i + 1), marks?.getOrNull(i), locked || marks != null,
                        active == i && marks == null, onFocus = { active = i }, modifier = Modifier.weight(1f), minWidth = 120,
                    )
                }
                // once checked, its correction right under its field (the arrow's room kept, unseen)
                marks?.getOrNull(i)?.takeIf { mk -> mk.verdict != Verdict.CORRECT }?.let { mk ->
                    Row {
                        Text(
                            arrow, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 28.dp, end = 8.dp).alpha(0f).clearAndSetSemantics { },
                        )
                        ItemCorrection(items.getOrNull(i), mk, Modifier.weight(1f).padding(start = 8.dp))
                    }
                }
            }
        }
    }
    val m = marks
    if (m != null) {
        CheckedCount(m.map { it.verdict }, first)
        return
    }
    Spacer(Modifier.height(10.dp))
    DiacriticChips(values.getOrElse(active) { TextFieldValue("") }, { values[active] = it })
    Spacer(Modifier.height(10.dp))
    ItemHint(active + 1, items.getOrNull(active), hinted[active] == true) { hinted[active] = true; stage?.hint() }
    Spacer(Modifier.height(12.dp))
    if (!locked) BigButton(
        bi("playerScreen.check"), enabled = values.any { it.text.isNotBlank() },
        onClick = {
            val after = lookedUp(vm, now())
            val checked = items.mapIndexed { i, it -> Sheets.check(it, values[i].text).copy(hinted = hinted[i] == true, lookedUp = after.getOrElse(i) { false }) }
            marks = checked
            done(outcomeOf(checked, ex.explain))
        },
    )
}

// --- sort ------------------------------------------------------------------------------------------------------------

/**
 * Words into columns ([Exercise.Sort]), by tap: a word, then its column; a word placed goes back with a tap. "Preveri ·
 * Check" once every word is placed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SortEx(vm: AppViewModel, ex: Exercise.Sort, locked: Boolean, done: (Outcome) -> Unit) {
    val words = remember(ex) { Sheets.sortWords(ex) }
    /** Each word's column (an index into the columns), by its place in [words]. */
    val placed = remember(ex) { mutableStateMapOf<Int, Int>() }
    var picked by remember(ex) { mutableStateOf<Int?>(null) }
    var marks by remember(ex) { mutableStateOf<Map<Int, ItemMark>?>(null) }
    // its items in the exercise's order (its columns' words), as it is marked: each word's column so far
    val order = remember(ex) { ex.columns.flatMap { it.words } }
    val byWord = remember(ex) { words.indices.associateBy { words[it] } }
    val now = remember(ex) { { order.map { word -> placed[byWord.getValue(word)]?.toString().orEmpty() } } }
    ItemsForCheatSheet(vm, now)
    val first = rememberFirstWrong(marks?.let { m -> order.map { word -> m[byWord.getValue(word)]?.verdict ?: Verdict.WRONG } })

    Prompt(ex.prompt ?: "", ex.instruction ?: bi("school.sortWords"))
    // the words not placed yet
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        words.forEachIndexed { w, word ->
            if (w !in placed) FilterChip(
                selected = picked == w, enabled = !locked && marks == null,
                onClick = { picked = if (picked == w) null else w },
                label = { Text(word, style = MaterialTheme.typography.titleMedium) },
            )
        }
    }
    picked?.let { w ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("👉 ${bi("school.pickColumn", "word" to words[w])}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            SpeakButton(vm.speaker, words[w])
        }
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = if (ex.columns.size == 4) 2 else ex.columns.size) {
        ex.columns.forEachIndexed { c, col ->
            Card(
                onClick = { picked?.let { w -> placed[w] = c; picked = null } },
                enabled = picked != null && !locked && marks == null,
                colors = CardDefaults.cardColors(containerColor = if (picked != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.weight(1f).heightIn(min = 120.dp),
            ) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(col.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    for ((w, at) in placed.entries.sortedBy { it.key }) if (at == c) {
                        val mk = marks?.get(w)
                        InputChip(
                            selected = mk != null, enabled = !locked || mk != null,
                            onClick = { if (!locked && marks == null) placed.remove(w) },
                            label = {
                                // once checked, the word can be looked up (before, its card would say its gender); a word
                                // in the wrong column says where it goes, there at the word
                                val look = LocalLook.current
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    Row {
                                        mk?.let { Text("${verdictMark(it.verdict)} ", color = verdictColor(it.verdict)) }
                                        ExText(
                                            AnnotatedString(words[w]), remember(w, look) { look?.taps?.sorted(words[w]).orEmpty() },
                                            color = mk?.let { verdictColor(it.verdict) } ?: Color.Unspecified, tag = "sorted",
                                        )
                                    }
                                    mk?.takeIf { it.verdict != Verdict.CORRECT }?.let {
                                        Text(
                                            "→ ${it.expected}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AlpineGreen,
                                            modifier = Modifier.semantics { contentDescription = "${bi("school.rightIs")}: ${it.expected}" },
                                        )
                                    }
                                }
                            },
                            modifier = first.on(order.indexOf(words[w])),
                        )
                    }
                }
            }
        }
    }
    val m = marks
    if (m != null) {
        CheckedCount(order.map { word -> m[byWord.getValue(word)]?.verdict ?: Verdict.WRONG }, first)
        return
    }
    Spacer(Modifier.height(12.dp))
    if (!locked) BigButton(
        bi("playerScreen.check"), enabled = placed.size == words.size,
        onClick = {
            // marked in the exercise's order (its columns' words), as its items are
            val after = lookedUp(vm, now())
            val checked = order.mapIndexed { i, word -> val w = byWord.getValue(word); Sheets.sorted(ex, word, placed[w]).copy(lookedUp = after.getOrElse(i) { false }) }
            marks = order.mapIndexed { i, word -> byWord.getValue(word) to checked[i] }.toMap()
            done(outcomeOf(checked, ex.explain))
        },
    )
}
