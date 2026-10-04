package si.lanisce.lani.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
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
import si.lanisce.lani.game.Peeks
import si.lanisce.lani.game.Readings
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.culture.ReadingFile
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.BigButton
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.minutesSince
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.ui.villagers.VillagerLogic
import si.lanisce.lani.ui.words.WordText
import si.lanisce.lani.ui.words.lookUpIn
import si.lanisce.lani.ui.words.lookUpWords

/**
 * "📖 Preberi · Read": what a thing in the chest holds to read (companion/GAME.md, "The chest"), Micka's recipe or
 * Janez's book, and a reading of the reading corner ("Branje · Reading"). Read first ("Read first, then answer"): the
 * title, a recipe's ingredients and numbered steps (or the page's lines, a proverb's meaning), every word to tap for its
 * card, 🔊 on each line in [reader]'s voice, the translation of each line shown or hidden (read it first, then open a
 * line's), for as long as the learner likes. "▶️ Začni · Start" hides the text, and its questions come one at a time (the
 * main idea, a detail, a word in context, true or false); "👁 Pokaži besedilo · Show the text" brings it back over the
 * question. Answering them all calls [onAnswered] with the verdicts, the minutes on screen, the looks back while a
 * question was open and what they cost at the learner's level ([Peeks.cost]), which pays the first time ([read]: it was
 * read before, and pays nothing now) and counts as reading practice every time. Its new words can be added to the
 * reviews; [onReadAloud]: "🎤 Beri na glas · Read aloud". TalkBack reads each line with its translation when it shows.
 */
@Composable
fun ReadingSheet(
    vm: AppViewModel,
    readable: Readings.Readable,
    reader: Villager?,
    read: Boolean,
    onAnswered: (verdicts: List<Verdict>, minutes: Int, looks: Int, cost: Int) -> Map<Res, Int>,
    onDismiss: () -> Unit,
    onReadAloud: (() -> Unit)? = null,
) {
    val r = readable.reading
    val pair = L10n.pair
    val lines = remember(r, pair) { ReadingLogic.lines(r) }
    val questions = remember(r, pair) { ReadingLogic.questions(r) }
    val words = remember(r, pair) { ReadingLogic.words(r) }
    var view by remember(r.id) { mutableStateOf(ReadingView()) }
    var answers by remember(r.id) { mutableStateOf(ReadAnswers()) }
    var run by remember(r.id) { mutableStateOf(ReadRun()) }
    var paid by remember(r.id) { mutableStateOf<Map<Res, Int>?>(null) }
    val readBefore = remember(r.id) { read }
    // the learner's level in the village's language: how much looking back costs
    val level = vm.levelIn(pair.target.code)
    // what counts as the reading's time: on screen from opening it to the last answer (ScreenClock)
    val started = remember(r.id) { ScreenClock.app.now() }
    val speak: @Composable (String) -> Unit = { text ->
        SpeakButton(
            vm.speaker, text, Modifier.padding(start = 4.dp),
            voiceName = reader?.speakerVoice ?: Clips.FEMALE, fallback = reader?.let { VillagerLogic.voiceOf(it) }, person = reader?.id,
        )
    }
    val textBlock: @Composable () -> Unit = {
        TranslationSwitch(view.showAll) { view = view.toggle() }
        ReadingText(vm, r, lines, view, onReveal = { i -> view = view.reveal(i) }, speak = speak)
    }
    Sheet(onDismiss) {
        SheetHeader(readable.item.emoji, r.title.target, r.title.base)
        Column(Modifier.heightIn(max = 680.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "📖 ${bi(ReadingLogic.kindKey(r.kind))} · ${r.level}" + if (readBefore) "  ✓ ${bi("reading.readBefore")}" else "",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            )
            if (r.source == "tutor") Text(
                "🧑‍🏫 ${bi("reading.byTutor")}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val q = run.at.coerceIn(0, (questions.size - 1).coerceAtLeast(0))
            val asking = run.asking && questions.isNotEmpty()
            if (!asking) {
                // read first, as long as it takes
                onReadAloud?.let { go ->
                    FilledTonalButton(onClick = go, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("🎤 ${bi("readAloud.title")}") }
                }
                textBlock()
                if (questions.isNotEmpty()) {
                    ReadFirstNote(peekRuleText(level, pays = !readBefore))
                    StartReadingButton { run = run.start() }
                }
            } else {
                // then its questions, one at a time, the text hidden; "👁" brings it back over the question
                PeekButton(run.peeking) { run = run.peek(open = !answers.complete(questions) && q !in answers.picked) }
            }
            if (asking && run.peeking) Paper { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { textBlock() } }
            else if (asking) {
                Heading("❓ ${bi("reading.questionOf", "n" to q + 1, "total" to questions.size)}")
                QuestionCard(vm, q + 1, questions[q], answers.picked[q], showBase = view.showAll, speak = speak) { choice ->
                    val next = answers.pick(q, choice)
                    answers = next
                    if (paid == null && next.complete(questions)) {
                        val cost = if (readBefore) 0 else Peeks.cost(level, run.looks)
                        paid = onAnswered(next.verdicts(questions), minutesSince(started), run.looks, cost)
                    }
                }
                if (q in answers.picked && q < questions.lastIndex) {
                    BigButton("${bi("common.next")} →", onClick = { run = run.next() })
                }
            }
            paid?.let { got ->
                val score = answers.score(questions)
                val pay = got.entries.joinToString(" ") { (res, n) -> "+$n ${res.emoji}" }
                val looked = looksText(run.looks, if (readBefore) 0 else Peeks.cost(level, run.looks), pays = !readBefore && pay.isNotEmpty())
                Surface(color = XpGold.copy(alpha = 0.16f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "✓ ${bi("reading.score", "right" to score, "total" to questions.size)}" +
                            (looked?.let { "\n$it" } ?: "") +
                            when {
                                readBefore -> "\n${bi("reading.noPayAgain")}"
                                pay.isNotEmpty() -> "\n${bi("reading.paid")}: $pay"
                                else -> ""
                            },
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (words.isNotEmpty()) {
                    Heading("📝 ${bi("reading.newWords")}")
                    Text(bi("reading.newWordsAbout"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (w in words) NewWordRow(w, added = "${r.id}/${w.word}" in vm.readings.added) { vm.readings.addWord(r, w) }
                }
            }
        }
    }
}

/**
 * A reading's text as it's read: a recipe's ingredients and steps under their headings, a part's name, the servings, and
 * every other line with its words to tap, its 🔊 and its translation (shown per [view]; 👁 on a line opens it: [onReveal]).
 */
@Composable
private fun ReadingText(vm: AppViewModel, r: ReadingFile, lines: List<ReadLine>, view: ReadingView, onReveal: (Int) -> Unit, speak: @Composable (String) -> Unit) {
    val recipe = r.kind == "recipe"
    var stepsHeaded = false
    var ingredientsHeaded = false
    lines.forEachIndexed { i, l ->
        if (recipe && !ingredientsHeaded && (l.kind == ReadLine.Kind.PART || l.kind == ReadLine.Kind.INGREDIENT)) {
            ingredientsHeaded = true
            Heading("🧺 ${bi("reading.ingredients")}")
        }
        if (l.kind == ReadLine.Kind.STEP && !stepsHeaded) {
            stepsHeaded = true
            Heading("🥣 ${bi("reading.steps")}")
        }
        when (l.kind) {
            ReadLine.Kind.PART -> Text(
                "${l.target} · ${l.base}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp).semantics { heading() },
            )
            ReadLine.Kind.SERVINGS -> Text("👥 ${l.target} · ${l.base}", style = MaterialTheme.typography.labelLarge)
            else -> LineRow(vm, l, view.shows(i), onReveal = { onReveal(i) }, speak = speak)
        }
    }
}

/** A new word of the reading: the word, what it means, and "➕" to add it to the reviews (✓ once added). */
@Composable
private fun NewWordRow(w: ReadWord, added: Boolean, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
            Text(w.word + if (w.form != w.word) "  (${w.form})" else "", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(w.means, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (added) Text("✓ ${bi("reading.added")}", style = MaterialTheme.typography.labelLarge, color = AlpineGreen, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        else FilledTonalButton(onClick = onAdd) { Text("➕ ${bi("reading.addWord")}") }
    }
}

@Composable
internal fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp).semantics { heading() })
}

/** "Prevod · Translation": every line's shown, or hidden to read first. */
@Composable
internal fun TranslationSwitch(on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("👁 ${bi("reading.translation")}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(bi("reading.hideToPractise"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = null)
    }
}

/**
 * One line: a step's number or an ingredient's bullet, the text (tap a word for its card), a proverb's meaning, the
 * translation (or 👁 to show it), 🔊.
 */
@Composable
internal fun LineRow(vm: AppViewModel, l: ReadLine, shown: Boolean, onReveal: () -> Unit, speak: @Composable (String) -> Unit) {
    val onWord = lookUpIn(vm, l.target, l.base, Words.READING)
    val means = l.meansTarget?.let { lookUpIn(vm, it, l.meansBase, Words.READING) }
    val lead = when (l.kind) {
        ReadLine.Kind.STEP -> "${l.number}."
        ReadLine.Kind.INGREDIENT -> "•"
        else -> null
    }
    // TalkBack: the line (a step's number first), its meaning, and the translation while it shows; its words to look up
    val described = listOfNotNull(
        lead?.takeIf { l.kind == ReadLine.Kind.STEP }?.let { "$it ${l.target}" } ?: l.target,
        l.meansTarget?.let { "💡 $it" },
        if (shown) l.base else null,
        if (shown) l.meansBase else null,
    ).joinToString(" · ")
    Row(verticalAlignment = Alignment.Top) {
        lead?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.widthIn(min = 24.dp).clearAndSetSemantics { })
            Spacer(Modifier.width(6.dp))
        }
        Column(
            Modifier.weight(1f).clearAndSetSemantics {
                contentDescription = described
                lookUpWords(l.target, onWord)
            },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            WordText(
                l.target, onWord,
                style = if (l.kind == ReadLine.Kind.INTRO) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                fontStyle = if (l.kind == ReadLine.Kind.INTRO) FontStyle.Italic else null,
            )
            l.meansTarget?.let { m ->
                Row {
                    Text("💡 ", style = MaterialTheme.typography.bodyMedium)
                    WordText(m, means, style = MaterialTheme.typography.bodyMedium, color = AlpineGreen)
                }
            }
            if (shown) {
                Text(l.base, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                l.meansBase?.let { Text("💡 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (!shown) {
            FilledTonalIconButton(onClick = onReveal, modifier = Modifier.padding(start = 4.dp)) { EmojiLabel("👁", bi("reading.showTranslation")) }
        }
        speak(l.target)
    }
}

/** A closing question: the question (tap its words, 🔊), its options, and once picked, right or not and why. */
@Composable
private fun QuestionCard(vm: AppViewModel, n: Int, q: ReadQuestion, picked: String?, showBase: Boolean, speak: @Composable (String) -> Unit, onPick: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReadingLogic.typeKey(q.type)?.let { key ->
                Text(bi(key), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    o == q.answer -> "✓ "
                    o == picked -> "✗ "
                    else -> ""
                }
                val color = when {
                    picked == null -> MaterialTheme.colorScheme.surface
                    o == q.answer -> AlpineGreen.copy(alpha = 0.22f)
                    o == picked -> TriglavRed.copy(alpha = 0.18f)
                    else -> MaterialTheme.colorScheme.surface
                }
                Surface(
                    onClick = { onPick(o) }, enabled = picked == null, color = color, shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(mark + o, style = MaterialTheme.typography.bodyLarge, fontWeight = if (mark.isNotEmpty()) FontWeight.Bold else null)
                    }
                }
            }
            if (picked != null) {
                val right = picked == q.answer
                Text(
                    if (right) "✓ ${bi("reading.right")}" else "✗ ${bi("reading.theAnswer")}: ${q.answer}",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (right) AlpineGreen else TriglavRed,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                val onWhy = lookUpIn(vm, q.explain, q.explainBase, Words.READING)
                Column(Modifier.clearAndSetSemantics { contentDescription = "${q.explain} · ${q.explainBase}"; lookUpWords(q.explain, onWhy) }) {
                    WordText(q.explain, onWhy, style = MaterialTheme.typography.bodyMedium)
                    Text(q.explainBase, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
