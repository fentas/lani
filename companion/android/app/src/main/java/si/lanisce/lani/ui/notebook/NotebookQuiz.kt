package si.lanisce.lani.ui.notebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.ScreenClock
import si.lanisce.lani.data.Words
import si.lanisce.lani.game.scene.BookQuestion
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.NoteBlock
import si.lanisce.lani.game.scene.Notebook
import si.lanisce.lani.game.scene.NotebookEntry
import si.lanisce.lani.game.scene.StoryBooks
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.game.Heading
import si.lanisce.lani.ui.game.Paper
import si.lanisce.lani.ui.game.PeekButton
import si.lanisce.lani.ui.game.ReadRun
import si.lanisce.lani.ui.game.Sheet
import si.lanisce.lani.ui.game.SheetHeader
import si.lanisce.lani.ui.game.looksText
import si.lanisce.lani.ui.minutesSince
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.ui.villagers.VillagerLogic
import si.lanisce.lani.ui.words.WordText
import si.lanisce.lani.ui.words.lookUpIn
import si.lanisce.lani.ui.words.lookUpWords

/**
 * "🎯 Preveri se · Test yourself" at the end of a notebook entry (companion/SCENES.md, "The story notebook"): the
 * telling's turns at [level] as questions ([StoryBooks.questions]), one at a time, the notebook's text hidden (it was
 * just read), "👁 Pokaži besedilo · Show the text" bringing it back over the question (GAME.md, "Read first, then
 * answer"); a pick shows right or wrong, the why of a wrong one and the teller's reply. All answered, [onAnswered] (the
 * verdicts, the minutes on screen, the looks back while a question was open): reading practice, as a book read was; it
 * pays nothing (the story paid by the fire), so looking back costs nothing. [reader]: the teller, whose voice reads.
 */
@Composable
fun BookQuizSheet(
    vm: AppViewModel,
    entry: NotebookEntry,
    level: String,
    reader: Villager?,
    onAnswered: (verdicts: List<Verdict>, minutes: Int, looks: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val book = entry.book
    val questions = remember(book, level) { StoryBooks.questions(book, level) }
    val text = remember(entry, level) {
        Notebook.evenings(entry, level, L10n.pair.base.code).filter { !it.locked }.flatMap { it.blocks.filterIsInstance<NoteBlock.Text>() }
    }
    var run by remember(book.id, level) { mutableStateOf(ReadRun().start()) }
    var picked by remember(book.id, level) { mutableStateOf<Map<Int, DialogChoice>>(emptyMap()) }
    var done by remember(book.id, level) { mutableStateOf(false) }
    val started = remember(book.id, level) { ScreenClock.app.now() }
    val showBase = vm.dialogPrefs.translations
    val speak: @Composable (String) -> Unit = { t ->
        SpeakButton(
            vm.speaker, t, Modifier.padding(start = 4.dp),
            voiceName = reader?.speakerVoice ?: Clips.MALE, fallback = reader?.let { VillagerLogic.voiceOf(it) }, person = reader?.id,
        )
    }
    val title = book.story.title
    Sheet(onDismiss) {
        SheetHeader("🎯", bi("notebook.test"), title)
        Column(Modifier.heightIn(max = 680.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (questions.isEmpty()) return@Column
            val q = run.at.coerceIn(0, questions.lastIndex)
            PeekButton(run.peeking) { run = run.peek(open = !done && q !in picked) }
            if (run.peeking) Paper {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (t in text) Column {
                        WordText(t.said, lookUpIn(vm, t.said, t.meant, Words.READING), style = MaterialTheme.typography.bodyLarge)
                        if (showBase) Text(t.meant, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Heading("❓ ${bi("reading.questionOf", "n" to q + 1, "total" to questions.size)}")
                TurnCard(vm, q + 1, questions[q], picked[q], showBase = showBase, speak = speak) { choice ->
                    if (q in picked) return@TurnCard
                    val next = picked + (q to choice)
                    picked = next
                    if (!done && questions.indices.all { it in next }) {
                        done = true
                        onAnswered(questions.indices.map { if (questions[it].right(next.getValue(it))) Verdict.CORRECT else Verdict.WRONG }, minutesSince(started), run.looks)
                    }
                }
                if (q in picked && q < questions.lastIndex) BigButton("${bi("common.next")} →", onClick = { run = run.next() })
            }
            if (done) Surface(color = XpGold.copy(alpha = 0.16f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "✓ ${bi("reading.score", "right" to questions.indices.count { questions[it].right(picked.getValue(it)) }, "total" to questions.size)}" +
                        (looksText(run.looks, 0, pays = false)?.let { "\n$it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/**
 * A turn of the telling as a question: the teller's line before it (its words to tap, 🔊), the choices, and once one is
 * picked, right or not: the teller's answer to a right one, why a wrong one is wrong (and his reaction to it).
 */
@Composable
private fun TurnCard(vm: AppViewModel, n: Int, q: BookQuestion, picked: DialogChoice?, showBase: Boolean, speak: @Composable (String) -> Unit, onPick: (DialogChoice) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (q.ask.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                val onWord = lookUpIn(vm, q.ask, q.askBase, Words.READING)
                Column(
                    Modifier.weight(1f).clearAndSetSemantics {
                        contentDescription = "$n. ${q.ask}" + if (showBase) " · ${q.askBase}" else ""
                        heading()
                        lookUpWords(q.ask, onWord)
                    },
                ) {
                    WordText("$n. ${q.ask}", onWord, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (showBase) Text(q.askBase, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                speak(q.ask)
            }
            for (o in q.options) {
                val mark = when {
                    picked == null -> ""
                    o.ok -> "✓ "
                    o == picked -> "✗ "
                    else -> ""
                }
                val color = when {
                    picked == null -> MaterialTheme.colorScheme.surface
                    o.ok -> AlpineGreen.copy(alpha = 0.22f)
                    o == picked -> TriglavRed.copy(alpha = 0.18f)
                    else -> MaterialTheme.colorScheme.surface
                }
                Surface(onClick = { onPick(o) }, enabled = picked == null, color = color, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(mark + o.sl, style = MaterialTheme.typography.bodyLarge, fontWeight = if (mark.isNotEmpty()) FontWeight.Bold else null)
                        if (showBase && o.en.isNotBlank()) Text(o.en, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (picked != null) {
                val right = q.right(picked)
                Text(
                    if (right) "✓ ${bi("reading.right")}" else "✗ ${bi("reading.theAnswer")}: ${q.answer.sl}",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (right) AlpineGreen else TriglavRed,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (!right) picked.why?.takeIf { it.isNotBlank() }?.let { Text("💡 $it", style = MaterialTheme.typography.bodyMedium) }
                picked.reply?.let { r ->
                    val onReply = lookUpIn(vm, r.sl, r.en, Words.READING)
                    Column(Modifier.clearAndSetSemantics { contentDescription = "${r.sl} · ${r.en}"; lookUpWords(r.sl, onReply) }) {
                        WordText("»${r.sl}«", onReply, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
                        if (r.en.isNotBlank()) Text(r.en, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
