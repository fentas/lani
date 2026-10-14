package si.lanisce.lani.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.ChatArchive
import si.lanisce.lani.app.ChatMessage
import si.lanisce.lani.app.CheatOpen
import si.lanisce.lani.app.WordQuery
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.GrammarTable
import si.lanisce.lani.data.Words
import si.lanisce.lani.game.CardFace
import si.lanisce.lani.game.CheatSheet
import si.lanisce.lani.game.PinnedCard
import si.lanisce.lani.game.SheetCard
import si.lanisce.lani.game.SheetKind
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.words.WordText
import si.lanisce.lani.ui.words.lookUpIn

/**
 * "📋 Plonk listek · Cheat sheet" over any screen (companion/README.md "Cheat sheet"): the unlocked cards in order of
 * relevance for what it was opened from ([at]: an exercise's pages first, its own card open at once), a filter by level,
 * each card's head with its rule line (a tap opens the whole card and counts it), "✨ Novo" on a card never opened, and
 * how many more cards come as the learner goes. Opening it is help, not a hint: over an exercise not answered yet, a right
 * answer pays in full but is right with help (app/CheatSheetController.watch), and the cards opened go to the tutor with
 * it. A card's table may hold the form an open exercise asks for: it's the rule's table, as a school's cheat sheet would
 * have it (README "Cheat sheet").
 */
@Composable
fun CheatSheetSheet(vm: AppViewModel, at: CheatOpen) {
    val c = vm.cheat
    val state = vm.game.state
    // the order as the sheet opened: a card opened (counted) doesn't move while it's open
    val sheet = remember(at, vm.grammar.pages, c.deck, state?.sheet?.pinned, state?.grammar) { c.sheet(at.context, at.focus) }
    val uses = state?.sheet?.opens.orEmpty()
    var level by remember(at) { mutableStateOf<String?>(null) }
    var expanded by remember(at) { mutableStateOf(emptySet<String>()) }
    var renaming by remember { mutableStateOf<PinnedCard?>(null) }
    // from an exercise: its topic's card is open at once (and so it's opened); from a pinned message's chip, that card
    LaunchedEffect(at) {
        sheet.cards.firstOrNull()?.takeIf { it.key in sheet.context || it.key == at.focus }?.let {
            expanded = setOf(it.key)
            c.opened(it.key)
        }
    }
    val shown = sheet.cards.filter { level == null || it.level == level }
    val base = L10n.pair.base.code

    LaniSheet(onDismissRequest = c::close) {
        CompositionLocalProvider(LocalSpeaker provides vm.speaker) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 16.dp)) {
                Text("📋 ${bi("cheatSheet.title")}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(
                    bi(if (sheet.context.isNotEmpty()) "cheatSheet.forExercise" else "cheatSheet.about"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (sheet.levels.size > 1) Row(
                    Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = level == null, onClick = { level = null }, label = { Text(bi("cheatSheet.all")) })
                    for (l in sheet.levels) FilterChip(selected = level == l, onClick = { level = if (level == l) null else l }, label = { Text(l) })
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (shown.isEmpty()) item("empty") {
                        Text(bi("cheatSheet.empty"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    items(shown, key = { it.key }) { card ->
                        val pin = if (card.kind == SheetKind.PINNED) c.pinned.firstOrNull { CheatSheet.PIN + it.id == card.key } else null
                        CardItem(
                            vm, card, base,
                            open = card.key in expanded,
                            isNew = CheatSheet.useKey(c.language, card.key) !in uses,
                            here = card.key in sheet.context,
                            onToggle = {
                                if (card.key in expanded) expanded = expanded - card.key
                                else {
                                    expanded = expanded + card.key
                                    c.opened(card.key)
                                }
                            },
                            onRename = pin?.let { { renaming = it } },
                            onUnpin = pin?.let { { c.unpin(it.id) } },
                        )
                    }
                    if (sheet.locked > 0) item("locked") {
                        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text("🔒 ${bi("cheatSheet.more", "n" to sheet.locked)}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(bi("grammar.unlockWhenMet"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    renaming?.let { p ->
        RenameDialog(p.title, onDismiss = { renaming = null }) {
            c.rename(p.id, it)
            renaming = null
        }
    }
}

/**
 * One card: its head (emoji, title in the learned language, in the base, its level, where it's from, "✨ Novo"), its rule
 * line; open, the whole card (table, examples with 🔊, the tip; a tutor's Markdown), the way to its page in the book,
 * and for a pinned one renaming and removing. [here]: a card of the exercise it was opened from.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardItem(
    vm: AppViewModel, card: SheetCard, base: String, open: Boolean, isNew: Boolean, here: Boolean,
    onToggle: () -> Unit, onRename: (() -> Unit)?, onUnpin: (() -> Unit)?,
) {
    val look: (String, String) -> Unit = { word, line -> vm.words.open(WordQuery(word, line, null, Words.CHAT)) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (here) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // the head: a tap opens or closes the card (TalkBack: expand, collapse)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onToggle)
                    .semantics(mergeDescendants = true) {
                        heading()
                        if (open) collapse { onToggle(); true } else expand { onToggle(); true }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(card.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val from = when (card.kind) {
                        SheetKind.TUTOR -> "🧑‍🏫 ${bi("townScroll.fromTutor")}"
                        SheetKind.PINNED -> "📌"
                        else -> null
                    }
                    val sub = listOfNotNull(card.titleBase, card.level, from).joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isNew) Text(
                    "✨ ${bi("cheatSheet.new")}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(start = 6.dp),
                )
                Text(if (open) "▾" else "▸", modifier = Modifier.padding(start = 8.dp).clearAndSetSemantics { })
            }
            when (val f = card.face) {
                is CardFace.Rule -> {
                    f.rule?.let { Markdown(it, onWord = look) }
                    if (open) {
                        f.table?.let { CardTable(vm, it, base) }
                        for (e in f.examples) {
                            val line = e.target(L10n.pair.target.code).ifEmpty { e.texts.values.first() }
                            val meaning = e.meaning(base)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    WordText(line, lookUpIn(vm, line, meaning, Words.CHAT), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                                    Text(meaning, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                }
                                SpeakButton(vm.speaker, line, Modifier.padding(start = 6.dp))
                            }
                        }
                        f.tip?.let { Markdown(if (it.trimStart().startsWith("💡")) it else "💡 $it", onWord = look) }
                    }
                }
                is CardFace.Text ->
                    if (open) Markdown(f.markdown, onWord = look)
                    else Text(
                        ChatArchive.plain(f.markdown).lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("|") }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
            }
            if (open) {
                // its pages in the book (those met: the book opens them), renaming and removing a pinned one
                val pages = card.pages.filter { vm.grammar.met(it) }.mapNotNull { vm.grammar.page(it) }
                if (pages.isNotEmpty() || onRename != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (p in pages) AssistChip(
                        onClick = { vm.grammar.show(p.id) },
                        label = { Text("📖 ${bi("grammar.toBook")}: ${p.target}", style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                    onRename?.let { AssistChip(onClick = it, label = { Text("✏️ ${bi("cheatSheet.rename")}") }, modifier = Modifier.heightIn(min = 48.dp)) }
                    onUnpin?.let { AssistChip(onClick = it, label = { Text("✕ ${Labels.REMOVE}") }, modifier = Modifier.heightIn(min = 48.dp)) }
                }
                if (card.machineWritten) Text(
                    listOfNotNull("🤖 ${bi("grammar.machineWritten")}", card.review).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A card's table: the head in bold, then the rows; words of the learned language are looked up on a tap. TalkBack reads
 * it row by row: the head, then each row as its first cell and every other with its column's name ("ti, imeti: imaš").
 */
@Composable
private fun CardTable(vm: AppViewModel, t: GrammarTable, base: String) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val heads = t.head.map { it.shown(base) }
    Column(Modifier.fillMaxWidth().border(1.dp, line, MaterialTheme.shapes.small).clip(MaterialTheme.shapes.small)) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                .clearAndSetSemantics { contentDescription = heads.filter { it.isNotBlank() }.joinToString(", ") },
        ) {
            for (h in heads) Text(
                h, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
        for (r in t.rows) {
            HorizontalDivider(color = line)
            val cells = r.map { it.shown(base) }
            val said = cells.mapIndexed { i, s -> val h = heads.getOrElse(i) { "" }; if (i == 0 || h.isBlank()) s else "$h: $s" }.joinToString(", ")
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = said }) {
                for ((i, cell) in r.withIndex()) {
                    val m = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 5.dp)
                    val text = cells[i]
                    if (cell.isWords) WordText(text, lookUpIn(vm, text, null, Words.CHAT), modifier = m, style = MaterialTheme.typography.bodyMedium)
                    else Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = m)
                }
            }
        }
    }
}

/** A pinned card's name, as the learner wants it ("Ime kartice · The card's name"); saved blank, it stays as it was. */
@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("✏️ ${bi("cheatSheet.rename")}") },
        text = { OutlinedTextField(text, { text = it.take(CheatSheet.MAX_TITLE) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(bi("chatSheet.save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(bi("chatSheet.cancel")) } },
    )
}

/**
 * "📋" in an exercise's top bar, by the tutor's 💬: the cheat sheet, the exercise's cards first ([context]). Help, not a
 * hint: a right answer after it pays in full, its review quality at most 4 (the exercise's run watches: ExerciseRun).
 */
@Composable
fun CheatButton(vm: AppViewModel, context: () -> List<String>) {
    IconButton(onClick = { vm.cheat.show(context()) }) { EmojiLabel("📋", bi("cheatSheet.title")) }
}

/**
 * The pages an exercise run's cheat sheet puts first: the exercise's own rule ([rule]), its run's other exercises' and
 * cards' ([exercises], [rules]), then the module's pages (those that name it among their modules).
 */
fun cheatContext(vm: AppViewModel, rule: String?, exercises: List<Exercise>, rules: List<String?>?, moduleId: String?): List<String> =
    (listOfNotNull(rule) + exercises.mapNotNull { it.grammar } + rules.orEmpty().filterNotNull() +
        moduleId?.let { m -> Grammar.ofModule(m, vm.grammar.pages).map { it.id } }.orEmpty()).distinct()

/** The pages the chat's cheat sheet puts first: the page or the exercise's module the chat is about now (its attachment). */
fun chatCheatContext(vm: AppViewModel): List<String> {
    val a = vm.chat.attached ?: return emptyList()
    val str = { k: String -> (a.data[k] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content }
    return when (a.key) {
        "about_grammar" -> listOfNotNull(str("id"))
        else -> str("module_id")?.let { m -> Grammar.ofModule(m, vm.grammar.pages).map { it.id } }.orEmpty()
    }
}

/**
 * A long press's choice on a tutor message with a table or a list (as a bookmark): "📌 Na plonk listek · Pin to cheat
 * sheet", or once pinned "📋 Na plonk listku · On the cheat sheet", which opens the sheet at its card. Null: nothing to pin.
 */
internal fun pinAction(vm: AppViewModel, m: ChatMessage): BubbleAction? {
    if (!vm.cheat.pinnable(m)) return null
    val pinned = vm.cheat.pinnedFrom(m.id)
    return if (pinned != null) BubbleAction("📋 ${bi("cheatSheet.onSheet")}") { vm.cheat.show(focus = CheatSheet.PIN + pinned.id) }
    else BubbleAction("📌 ${bi("cheatSheet.pin")}") { vm.cheat.pin(m) }
}
