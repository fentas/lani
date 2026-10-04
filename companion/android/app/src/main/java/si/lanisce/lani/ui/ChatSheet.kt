package si.lanisce.lani.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.ChatArchive
import si.lanisce.lani.app.ChatMessage
import si.lanisce.lani.l10n.Dates
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget
import java.time.LocalDate
import java.time.ZoneId

/**
 * The tutor chat: today's conversation up front, the days before folded into "📦 Arhiv · Archive" (weeks, days, their
 * messages: [ChatArchive]); a long press on a message bookmarks it, notes why, or replies to it; "🔖" lists the
 * bookmarks, and a tap on one (or on a reply's quote) goes to the message, opening the archive to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSheet(vm: AppViewModel) {
    val chat = vm.chat
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by remember { mutableStateOf(TextFieldValue("")) }
    val zone = remember { ZoneId.systemDefault() }
    // the app's day: past midnight, today's conversation starts afresh, also while the chat is open
    val today by produceState(LocalDate.now(zone)) {
        while (true) {
            delay(30_000)
            value = LocalDate.now(zone)
        }
    }
    var open by remember { mutableStateOf(ChatArchive.Open()) }
    val timeline by remember { derivedStateOf { ChatArchive.timeline(chat.messages.toList(), today, zone) } }
    val rows by remember { derivedStateOf { ChatArchive.rows(timeline, open) } }
    val marked by remember { derivedStateOf { ChatArchive.bookmarks(chat.messages.toList()) } }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = rows.lastIndex.coerceAtLeast(0))
    var showBookmarks by remember { mutableStateOf(false) }
    var jump by remember { mutableStateOf<String?>(null) }
    var highlight by remember { mutableStateOf<String?>(null) }
    var noteFor by remember { mutableStateOf<ChatMessage?>(null) }
    val focus = remember { FocusRequester() }

    fun jumpTo(id: String) {
        showBookmarks = false
        open = ChatArchive.reveal(timeline, open, id)
        jump = id
    }

    fun reply(m: ChatMessage) {
        chat.replyTo = m
        showBookmarks = false
        runCatching { focus.requestFocus() }
    }

    LaunchedEffect(chat.messages.size, chat.typing) {
        val last = rows.size + if (chat.typing) 1 else 0
        if (last > 0 && !showBookmarks) list.animateScrollToItem(last - 1)
    }
    // a jump waits for its message's rows (the archive opened to it), then shows it with the row above (its day's title)
    LaunchedEffect(jump, rows) {
        val id = jump ?: return@LaunchedEffect
        val i = ChatArchive.indexOf(rows, id)
        if (i >= 0) {
            list.scrollToItem((i - 1).coerceAtLeast(0))
            highlight = id
        }
        jump = null
    }
    LaunchedEffect(highlight) {
        if (highlight != null) {
            delay(2_000)
            highlight = null
        }
    }

    ModalBottomSheet(onDismissRequest = { chat.close() }, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).imePadding().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💬 ${bi("common.yourTutor")}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { showBookmarks = !showBookmarks }) {
                    EmojiLabel("🔖", bi("chatSheet.bookmarks"))
                    if (marked.isNotEmpty()) Text(" ${marked.size}")
                }
                TextButton(onClick = vm::openTalk) { Text("🗣️ ${bi("common.talk")}") }
            }
            if (showBookmarks) {
                Bookmarks(
                    marked, today, zone, Modifier.weight(1f).padding(vertical = 12.dp),
                    onBack = { showBookmarks = false },
                    onOpen = { jumpTo(it.id) },
                    onReply = ::reply,
                    onRemove = { chat.bookmark(it.id, on = false) },
                )
            } else LazyColumn(
                Modifier.weight(1f).padding(vertical = 12.dp),
                state = list,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.key }, contentType = { it::class }) { row ->
                    when (row) {
                        is ChatArchive.ArchiveRow -> FoldRow("📦 ${bi("chatSheet.archive")}", row.open, row.count, depth = 0) { open = open.toggleArchive() }
                        is ChatArchive.UndatedRow ->
                            FoldRow("🕰️ ${bi("chatSheet.undated")}", row.open, row.count, depth = 1, hint = row.hint) { open = open.toggleUndated() }
                        is ChatArchive.WeekRow ->
                            FoldRow(ChatArchive.weekTitle(row.week, today), row.open, row.week.size, depth = 1) { open = open.toggleWeek(row.week.start) }
                        is ChatArchive.DayRow ->
                            FoldRow(ChatArchive.dayTitle(row.day.date, today), row.open, row.day.messages.size, depth = 2, hint = row.hint) {
                                open = open.toggleDay(row.day.date)
                            }
                        ChatArchive.TodayRow -> DayDivider(bi("common.today"))
                        is ChatArchive.SinceRow -> DayDivider(ChatArchive.sinceTitle(row.date, today))
                        ChatArchive.EmptyRow -> Text(
                            inTarget("chatSheet.askAnything") + "\n" + inBase("chatSheet.askAnything"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        is ChatArchive.MessageRow -> {
                            val m = row.message
                            ChatBubble(
                                m.text, m.fromMe, m.about,
                                bookmarked = m.bookmark != null,
                                quote = m.replyTo?.let { q ->
                                    BubbleQuote(whoAndWhen(q.fromMe, q.at, today, zone), q.text, onClick = chat.find(q.id)?.let { { jumpTo(q.id) } })
                                },
                                highlighted = highlight == m.id,
                                actions = listOf(
                                    BubbleAction("🔖 ${bi(if (m.bookmark == null) "chatSheet.bookmark" else "chatSheet.unbookmark")}") {
                                        chat.bookmark(m.id, on = m.bookmark == null)
                                    },
                                    BubbleAction("✏️ ${bi("chatSheet.note")}") { noteFor = m },
                                    BubbleAction("↩️ ${bi("chatSheet.reply")}") { reply(m) },
                                ),
                            )
                        }
                    }
                }
                if (chat.typing) item(key = "typing") { ChatBubble("…", fromMe = false) }
            }
            chat.attached?.let { ctx ->
                ContextCard(ctx.label, onClear = { chat.attached = null })
                Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (q in quickQuestions) {
                        SuggestionChip(onClick = { chat.send(q) }, label = { Text(q, style = MaterialTheme.typography.labelMedium) })
                    }
                }
            }
            chat.replyTo?.let { m -> ReplyCard(whoAndWhen(m.fromMe, m.at, today, zone), ChatArchive.quote(m).text, onClear = { chat.replyTo = null }) }
            DiacriticChips(input, { input = it })
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    placeholder = { Text(bi("chatSheet.write")) },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                )
                FilledIconButton(
                    onClick = { chat.send(input.text.trim()); input = TextFieldValue("") },
                    enabled = input.text.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp),
                ) { EmojiLabel("➤", Labels.SEND) }
            }
        }
    }
    noteFor?.let { m ->
        NoteDialog(m.bookmark?.note.orEmpty(), onDismiss = { noteFor = null }) {
            chat.note(m.id, it)
            noteFor = null
        }
    }
}

private val quickQuestions: List<String> get() = listOf(bi("chatSheet.why"), bi("chatSheet.anotherExample"), bi("chatSheet.explainGrammar"))

/** "Učitelj · Tutor" or "Ti · You", and the day when it wasn't today ("· 28. 9."). */
private fun whoAndWhen(fromMe: Boolean, at: Long?, today: LocalDate, zone: ZoneId): String {
    val who = bi(if (fromMe) "chatSheet.you" else "chatSheet.tutor")
    val day = ChatArchive.dayOf(at, zone)
    return if (day == null || day >= today) who else "$who · ${Dates.short(day, L10n.pair.target)}"
}

@Composable
private fun ContextCard(label: String, onClear: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📎 $label", style = MaterialTheme.typography.bodySmall, maxLines = 2, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) { EmojiLabel("✕", Labels.REMOVE) }
        }
    }
}

/** The message the next one answers, above the input, as messengers quote it; ✕ lets it go. */
@Composable
private fun ReplyCard(who: String, text: String, onClear: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text("↩️ $who", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onClear) { EmojiLabel("✕", Labels.REMOVE) }
        }
    }
}

/** A fold of the archive: the archive itself ([depth] 0), a week or the undated messages (1), a day (2). */
@Composable
private fun FoldRow(title: String, open: Boolean, count: Int, depth: Int, hint: String? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (depth == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        // TalkBack says "expand" or "collapse" (the ▸ and ▾ are hidden from it)
        modifier = Modifier.fillMaxWidth().padding(start = (depth * 12).dp).semantics {
            if (open) collapse { onClick(); true } else expand { onClick(); true }
        },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (open) "▾" else "▸", modifier = Modifier.padding(end = 8.dp).clearAndSetSemantics { })
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = if (depth == 2) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                hint?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text("💬 $count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Danes · Today" between the archive and today's messages; "Včeraj · Yesterday" (or the day) over an earlier day's up front. */
@Composable
private fun DayDivider(label: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
        HorizontalDivider(Modifier.weight(1f))
    }
}

/** "🔖 Zaznamki · Bookmarks": newest first, each with its day and who wrote it; a tap goes to the message. */
@Composable
private fun Bookmarks(
    marked: List<ChatMessage>,
    today: LocalDate,
    zone: ZoneId,
    modifier: Modifier,
    onBack: () -> Unit,
    onOpen: (ChatMessage) -> Unit,
    onReply: (ChatMessage) -> Unit,
    onRemove: (ChatMessage) -> Unit,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← ${Labels.BACK}") }
            Text("🔖 ${bi("chatSheet.bookmarks")}", style = MaterialTheme.typography.titleMedium)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (marked.isEmpty()) item(key = "none") {
                Text(inTarget("chatSheet.noBookmarks") + "\n" + inBase("chatSheet.noBookmarks"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(marked, key = { it.id }) { m ->
                Surface(onClick = { onOpen(m) }, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${ChatArchive.dayLabel(m, today, zone)} · ${bi(if (m.fromMe) "chatSheet.you" else "chatSheet.tutor")}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(ChatArchive.plain(m.text).trim(), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            m.bookmark?.note?.let {
                                Text("✏️ $it", style = MaterialTheme.typography.labelMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                        TextButton(onClick = { onReply(m) }) { EmojiLabel("↩️", bi("chatSheet.reply")) }
                        TextButton(onClick = { onRemove(m) }) { EmojiLabel("✕", bi("chatSheet.unbookmark")) }
                    }
                }
            }
        }
    }
}

/** A bookmark's note: why it was kept ("Zakaj? · Why?"); saved blank, the note goes and the bookmark stays. */
@Composable
private fun NoteDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔖 ${bi("chatSheet.note")}") },
        text = { OutlinedTextField(text, { text = it.take(200) }, placeholder = { Text(bi("chatSheet.why")) }, maxLines = 3) },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(bi("chatSheet.save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(bi("chatSheet.cancel")) } },
    )
}

/** A long press's choice on a message ("🔖 Zaznamek · Bookmark"); TalkBack offers the same as actions. */
internal class BubbleAction(val label: String, val run: () -> Unit)

/** The message a reply answers, quoted on the reply's bubble: who and when ([label]), its start; a tap goes to it. */
internal class BubbleQuote(val label: String, val text: String, val onClick: (() -> Unit)?)

/**
 * A message: Jan's on the right, the tutor's (Markdown) on the left. Also the thread under a role-play's debrief.
 * [actions] come with a long press; [bookmarked] shows a 🔖 beside it, [highlighted] a frame (a jump arrived there).
 */
@Composable
internal fun ChatBubble(
    text: String,
    fromMe: Boolean,
    about: String? = null,
    bookmarked: Boolean = false,
    quote: BubbleQuote? = null,
    highlighted: Boolean = false,
    actions: List<BubbleAction> = emptyList(),
) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box(Modifier.fillMaxWidth(), contentAlignment = if (fromMe) Alignment.CenterEnd else Alignment.CenterStart) {
        Row(verticalAlignment = Alignment.Top) {
            if (fromMe && bookmarked) BookmarkMark()
            Box(Modifier.weight(1f, fill = false)) {
                Surface(
                    color = if (fromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(20.dp, 20.dp, if (fromMe) 4.dp else 20.dp, if (fromMe) 20.dp else 4.dp),
                    border = if (highlighted) BorderStroke(3.dp, MaterialTheme.colorScheme.tertiary) else null,
                    modifier = Modifier.widthIn(max = 320.dp).then(
                        if (actions.isEmpty()) Modifier
                        else Modifier
                            .pointerInput(Unit) {
                                detectTapGestures(onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menu = true
                                })
                            }
                            .semantics { customActions = actions.map { a -> CustomAccessibilityAction(a.label) { a.run(); true } } },
                    ),
                ) {
                    // onPrimary, not white: the dark theme's primary is a light blue.
                    if (fromMe) Column(Modifier.padding(12.dp)) {
                        quote?.let { QuoteBlock(it, MaterialTheme.colorScheme.onPrimary) }
                        about?.let { Text("📎 $it", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall) }
                        Text(text, color = MaterialTheme.colorScheme.onPrimary)
                    }
                    else if (quote == null) Markdown(text, Modifier.padding(12.dp))
                    else Column(Modifier.padding(12.dp)) {
                        QuoteBlock(quote, MaterialTheme.colorScheme.onSurfaceVariant)
                        Markdown(text)
                    }
                }
                if (actions.isNotEmpty()) DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    for (a in actions) DropdownMenuItem(text = { Text(a.label) }, onClick = { menu = false; a.run() })
                }
            }
            if (!fromMe && bookmarked) BookmarkMark()
        }
    }
}

@Composable
private fun BookmarkMark() {
    val label = bi("chatSheet.bookmark")
    Text("🔖", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp).clearAndSetSemantics { contentDescription = label })
}

@Composable
private fun QuoteBlock(q: BubbleQuote, color: Color) {
    Column(
        Modifier
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.14f))
            .then(if (q.onClick != null) Modifier.clickable(onClick = q.onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text("↩️ ${q.label}", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(q.text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
