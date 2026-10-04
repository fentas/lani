package si.lanisce.lani.ui.notebook

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.R
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.Words
import si.lanisce.lani.game.render.book.Sketch
import si.lanisce.lani.game.scene.NoteBlock
import si.lanisce.lani.game.scene.NoteEvening
import si.lanisce.lani.game.scene.Notebook
import si.lanisce.lani.game.scene.NotebookEntry
import si.lanisce.lani.game.scene.StoryBooks
import si.lanisce.lani.game.scene.StoryPicture
import si.lanisce.lani.l10n.Dates
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.EmojiLabel
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.villagers.VillagerLogic
import si.lanisce.lani.ui.words.WordText
import si.lanisce.lani.ui.words.lookUpIn
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin

/** The notebook's handwriting: Kalam, Regular and Bold (its licence in assets/fonts/, CREDITS.md). */
val Hand = FontFamily(Font(R.font.kalam_regular, FontWeight.Normal), Font(R.font.kalam_bold, FontWeight.Bold))

/**
 * "📓 Moj zvezek zgodb · My story notebook" (companion/SCENES.md, "The story notebook"), over whatever is on screen: the
 * learner's handwritten notebook of the stories heard by the fire. Its first page the contents ("Zapisano ob ognju",
 * each story with the day it was first heard); then a page a story ([Notebook.entries]), turned by a swipe or ‹ ›: its
 * title, each evening under its day ("28. septembra, ob ognju"), the story written down in blue ink on ruled paper, the
 * translation under each paragraph in pencil (the dialogs' 👁 preference; hidden, a tap beside the words shows a
 * paragraph's), 🔊 a paragraph in the teller's voice, a word tapped for its card, its pictures as pencil sketches taped
 * or clipped in with a caption by hand, the evenings to come "🔒 Naslednji večer · Next evening"; at its end "🎯 Preveri
 * se · Test yourself" ([BookQuizSheet]). A story heard at two levels offers both. [at]: where it opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookSheet(vm: AppViewModel, at: NotebookAt) {
    val look = if (isSystemInDarkTheme()) NotebookLook.NIGHT else NotebookLook.DAY
    val ink = remember(look) { Ink(look) }
    val entries = rememberNotebook(vm)
    val start = remember(at, entries.size) {
        at.story?.let { id -> entries.indexOfFirst { e -> e.book.parts.any { it.story.id == id } } }?.takeIf { it >= 0 }?.plus(1) ?: 0
    }
    val pager = rememberPagerState(initialPage = start) { entries.size + 1 }
    val scope = rememberCoroutineScope()
    val r = rememberRules()
    var quiz by remember { mutableStateOf<Pair<NotebookEntry, String>?>(null) }
    ModalBottomSheet(
        onDismissRequest = vm::closeNotebook,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ink.paper,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f)) {
            Header(vm, ink)
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { if (it == 0) "contents" else entries[it - 1].id }) { page ->
                if (page == 0) ContentsPage(entries, ink, r) { i -> scope.launch { pager.animateScrollToPage(i + 1) } }
                else EntryPage(vm, entries[page - 1], ink, r) { e, lv -> quiz = e to lv }
            }
            PageBar(pager.currentPage, entries.size + 1, ink) { p -> scope.launch { pager.animateScrollToPage(p) } }
        }
    }
    quiz?.let { (e, lv) ->
        BookQuizSheet(
            vm, e, lv, reader = vm.villagers.byId(e.story.teller),
            onAnswered = { verdicts, minutes, looks -> vm.readings.bookRead(e.book, lv, verdicts, minutes, looks) },
            onDismiss = { quiz = null },
        )
    }
}

/** The village's notebook: the stories told by its fire the learner has heard ([Notebook.entries]). */
@Composable
fun rememberNotebook(vm: AppViewModel): List<NotebookEntry> {
    val scenes = vm.scenes.all
    val heard = vm.game.state?.stories.orEmpty()
    val pair = L10n.pair
    return remember(scenes, heard, pair) { Notebook.entries(StoryBooks.stories(scenes), heard) }
}

/** How many sketches the notebook holds: the pictures of the evenings heard. */
fun sketchesIn(entries: List<NotebookEntry>): Int = entries.sumOf { it.book.pictures }

/** The notebook's colours, for Compose. */
private class Ink(l: NotebookLook.Pal) {
    val paper = Color(l.paper)
    val rule = Color(l.rule)
    val margin = Color(l.margin)
    val ink = Color(l.ink)
    val pencil = Color(l.pencil)
    val tape = Color(l.tape)
    val shadow = Color(l.shadow)
    val clip = Color(l.clip)
}

/** The ruled line in whole pixels (so the lines don't drift), the same in dp, and where the handwriting sits on it. */
private class Rules(val line: Float, val lineDp: Dp, val base: Float)

@Composable
private fun rememberRules(): Rules {
    val d = LocalDensity.current
    return remember(d.density, d.fontScale) {
        with(d) {
            val line = NotebookLook.LINE.sp.toPx().roundToInt().toFloat()
            Rules(line, line.toDp(), (line * NotebookLook.BASELINE).roundToInt().toFloat())
        }
    }
}

/** Handwriting on the rules: [size] sp, a line apart (or [lines]), its glyphs centred in the line. */
@Composable
private fun hand(color: Color, size: Float, bold: Boolean = false, lines: Int = 1): TextStyle {
    val d = LocalDensity.current
    return TextStyle(
        fontFamily = Hand, color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        lineHeight = with(d) { (rememberRules().line * lines).toSp() },
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
}

/** A block as tall as whole ruled lines, so what follows it starts on a line. */
private fun Modifier.wholeLines(r: Rules): Modifier = layout { m, c ->
    val p = m.measure(c)
    val h = (ceil(p.height / r.line - 0.03f).coerceAtLeast(1f) * r.line).roundToInt()
    layout(p.width, h) { p.place(0, 0) }
}

/**
 * A text sitting on its ruled line: its first baseline moved onto the line's (the [lines]th of the block), its lines
 * the rules apart (its style's line height), the block as tall as its whole lines.
 */
private fun Modifier.onRule(r: Rules, lines: Int = 1): Modifier = layout { m, c ->
    val p = m.measure(c)
    val b = p[FirstBaseline]
    val want = r.base + r.line * (lines - 1)
    val dy = if (b == androidx.compose.ui.layout.AlignmentLine.Unspecified) 0 else (want - b).roundToInt()
    val h = (ceil(p.height / r.line - 0.03f).coerceAtLeast(lines.toFloat()) * r.line).roundToInt()
    layout(p.width, h) { p.place(0, dy) }
}

/** An empty ruled line (or a few). */
@Composable
private fun Blank(r: Rules, lines: Int = 1) = Spacer(Modifier.height(r.lineDp * lines))

/** The paper: faint blue rules a line apart under the writing, the red margin, a fibre here and there. */
private fun Modifier.ruled(ink: Ink, r: Rules): Modifier = drawBehind {
    drawRect(ink.paper)
    val rand = java.util.Random(11)
    repeat((size.height / 60f).toInt().coerceAtMost(200)) {
        val x = rand.nextFloat() * size.width; val y = rand.nextFloat() * size.height
        drawLine(ink.pencil.copy(alpha = 0.05f + rand.nextFloat() * 0.05f), Offset(x, y), Offset(x + 12f + rand.nextFloat() * 40f, y + rand.nextFloat() * 3f - 1.5f), strokeWidth = 1f)
    }
    val stroke = 1.dp.toPx()
    var y = r.line + r.base + stroke
    while (y < size.height) {
        drawLine(ink.rule, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
        y += r.line
    }
    val x = NotebookLook.MARGIN.dp.toPx()
    drawLine(ink.margin, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.4.dp.toPx())
}

/** A page of the notebook: ruled paper to its foot, the writing after the margin, scrolling. */
@Composable
private fun RuledPage(ink: Ink, r: Rules, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tall = maxHeight
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = tall).ruled(ink, r)
                .padding(start = (NotebookLook.MARGIN + NotebookLook.GUTTER).dp, end = NotebookLook.EDGE.dp, top = r.lineDp, bottom = r.lineDp * 2),
        ) { content() }
    }
}

/** On top: the notebook's name, the translations' 👁 (the dialogs' preference, remembered), and ✕. */
@Composable
private fun Header(vm: AppViewModel, ink: Ink) {
    val prefs = vm.dialogPrefs
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            Text("📓 ${inTarget("notebook.title")}", style = TextStyle(fontFamily = Hand, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = ink.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (L10n.pair.base != L10n.pair.target) Text(inBase("notebook.title"), style = TextStyle(fontFamily = Hand, fontSize = 15.sp, color = ink.pencil), maxLines = 1)
        }
        FilterChip(
            selected = prefs.translations,
            onClick = { prefs.showTranslations(!prefs.translations) },
            label = { Text("👁 ${inTarget("reading.translation")}", modifier = Modifier.clearAndSetSemantics { contentDescription = bi("reading.translation") }) },
            leadingIcon = if (prefs.translations) ({ Text("✓", fontWeight = FontWeight.Bold, modifier = Modifier.clearAndSetSemantics { }) }) else null,
            colors = FilterChipDefaults.filterChipColors(labelColor = ink.ink, selectedLabelColor = ink.ink, selectedContainerColor = ink.rule.copy(alpha = 0.45f)),
        )
        IconButton(onClick = vm::closeNotebook) { EmojiLabel("✕", Labels.CLOSE) }
    }
}

/** At the foot: ‹ the page › (its number of all, for TalkBack in words). */
@Composable
private fun PageBar(page: Int, pages: Int, ink: Ink, onGo: (Int) -> Unit) {
    val pencil = TextStyle(fontFamily = Hand, fontSize = 20.sp, color = ink.pencil)
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { onGo(page - 1) }, enabled = page > 0) { EmojiLabel("‹", bi("notebook.previous"), style = pencil.copy(fontSize = 30.sp, color = if (page > 0) ink.ink else ink.pencil.copy(alpha = 0.3f))) }
        Text("${page + 1} / $pages", style = pencil, modifier = Modifier.semantics { contentDescription = bi("notebook.page", "n" to page + 1, "total" to pages) })
        IconButton(onClick = { onGo(page + 1) }, enabled = page < pages - 1) { EmojiLabel("›", bi("notebook.next"), style = pencil.copy(fontSize = 30.sp, color = if (page < pages - 1) ink.ink else ink.pencil.copy(alpha = 0.3f))) }
    }
}

/** The first page: "Zapisano ob ognju", and the contents: each story with the day it was first heard (a tap turns to it). */
@Composable
private fun ContentsPage(entries: List<NotebookEntry>, ink: Ink, r: Rules, onOpen: (Int) -> Unit) = RuledPage(ink, r) {
    val target = L10n.pair.target
    Title(inTarget("notebook.cover"), ink, r)
    if (L10n.pair.base != target) Text(inBase("notebook.cover"), style = hand(ink.pencil, NotebookLook.PENCIL), modifier = Modifier.onRule(r))
    Blank(r)
    Text(bi("notebook.contents"), style = hand(ink.ink, NotebookLook.HEADING, bold = true), modifier = Modifier.onRule(r).semantics { heading() })
    if (entries.isEmpty()) {
        Text(inTarget("notebook.empty"), style = hand(ink.ink, NotebookLook.TEXT), modifier = Modifier.onRule(r))
        if (L10n.pair.base != target) Text(inBase("notebook.empty"), style = hand(ink.pencil, NotebookLook.PENCIL), modifier = Modifier.onRule(r))
        return@RuledPage
    }
    entries.forEachIndexed { i, e ->
        val title = e.story.title
        val said = title.substringBefore(" · ")
        val meant = title.substringAfter(" · ", "").takeIf { it.isNotBlank() && it != said }
        val day = e.first?.let { Dates.short(it, target) }.orEmpty()
        val whole = e.book.parts.size
        val described = listOfNotNull("${i + 1}. $said", meant, e.first?.let { Dates.dayMonth(it, target) }, if (whole > 1) bi("books.evenings", "heard" to e.book.heard, "total" to whole) else null).joinToString(", ")
        Column(
            Modifier.fillMaxWidth().clickable(onClickLabel = said) { onOpen(i) }
                .clearAndSetSemantics { contentDescription = described; role = Role.Button; onClick { onOpen(i); true } },
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text("${i + 1}. $said", style = hand(ink.ink, NotebookLook.TEXT), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).onRule(r))
                // the dots to the day, as a contents page has them
                Box(Modifier.weight(1f).height(r.lineDp).drawBehind {
                    val y = r.base - 3.dp.toPx()
                    var x = 6.dp.toPx()
                    while (x < size.width - 6.dp.toPx()) { drawCircle(ink.pencil, 1.1.dp.toPx(), Offset(x, y)); x += 7.dp.toPx() }
                })
                Text(day + if (whole > 1 && e.book.heard < whole) "  (${e.book.heard}/$whole)" else "", style = hand(ink.ink, NotebookLook.DATE), modifier = Modifier.onRule(r))
            }
            if (meant != null) Text(meant, style = hand(ink.pencil, NotebookLook.PENCIL), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 22.dp).onRule(r))
        }
    }
    Blank(r)
    Text(
        "${inTarget("notebook.stories", "n" to entries.size)} · ${inTarget("notebook.sketches", "n" to sketchesIn(entries))}",
        style = hand(ink.pencil, NotebookLook.PENCIL), textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().onRule(r),
    )
}

/** A story's page: its title, the level it's read at, its evenings, and "🎯 Preveri se" at its end. */
@Composable
private fun EntryPage(vm: AppViewModel, e: NotebookEntry, ink: Ink, r: Rules, onQuiz: (NotebookEntry, String) -> Unit) = RuledPage(ink, r) {
    val levels = e.book.levels
    var level by remember(e.id) { mutableStateOf(Notebook.openAt(e, vm.levelIn(e.story.language)) ?: levels.firstOrNull().orEmpty()) }
    val base = L10n.pair.base.code
    val evenings = remember(e, level, base) { Notebook.evenings(e, level, base) }
    val reader = vm.villagers.byId(e.story.teller)
    val shows = vm.dialogPrefs.translations
    val speak: @Composable (String) -> Unit = { t ->
        SpeakButton(vm.speaker, t, voiceName = reader?.speakerVoice ?: Clips.MALE, fallback = reader?.let { VillagerLogic.voiceOf(it) }, person = reader?.id)
    }
    val title = e.story.title
    val said = title.substringBefore(" · ")
    val meant = title.substringAfter(" · ", "").takeIf { it.isNotBlank() && it != said }
    Box {
        // the story's emoji in the margin, as a sticker
        Text(e.story.emoji, fontSize = 22.sp, modifier = Modifier.offset(x = -(NotebookLook.MARGIN + NotebookLook.GUTTER - 8f).dp, y = (r.lineDp * 0.9f)).clearAndSetSemantics { })
        Title(said, ink, r)
    }
    if (meant != null) Text(meant, style = hand(ink.pencil, NotebookLook.PENCIL), modifier = Modifier.onRule(r))
    if (levels.size > 1) Row(Modifier.wholeLines(r), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(inTarget("books.heardAt"), style = hand(ink.pencil, NotebookLook.PENCIL), modifier = Modifier.clearAndSetSemantics { })
        for (l in levels) LevelTag(l, l == level, ink) { level = l }
    }
    for (ev in evenings) Evening(vm, ev, shows, ink, r, speak)
    Blank(r)
    val questions = remember(e, level) { StoryBooks.questions(e.book, level) }
    if (questions.isNotEmpty()) {
        val done = vm.readings.bookDone(e.id, level)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).wholeLines(r).clickable(role = Role.Button) { onQuiz(e, level) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🎯 ${bi("notebook.test")}" + if (done) "  ✓" else "", style = hand(ink.ink, NotebookLook.DATE, bold = true), modifier = Modifier.onRule(r))
        }
    }
}

/** The story's title, big, across two lines, underlined by hand. */
@Composable
private fun Title(text: String, ink: Ink, r: Rules) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text, style = hand(ink.ink, NotebookLook.TITLE, bold = true, lines = 2), onTextLayout = { layout = it },
        modifier = Modifier.onRule(r, 2).semantics { heading() }.drawBehind {
            val l = layout ?: return@drawBehind
            val y = l.getLineBaseline(0) + 7.dp.toPx()
            val w = l.getLineRight(0)
            val p = Path()
            p.moveTo(0f, y)
            var x = 0f
            while (x < w) { x += 6f; p.lineTo(x, y + sin(x / 23f) * 1.6f) }
            drawPath(p, ink.ink.copy(alpha = 0.75f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
        },
    )
}

/** "A1", "A2": the level the story is read at, a pencil tag. */
@Composable
private fun LevelTag(level: String, on: Boolean, ink: Ink, onPick: () -> Unit) {
    Surface(
        onClick = onPick, shape = RoundedCornerShape(50), color = if (on) ink.rule.copy(alpha = 0.55f) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (on) ink.ink else ink.pencil),
        modifier = Modifier.heightIn(min = 40.dp).semantics { role = Role.Tab; contentDescription = level + if (on) " ✓" else "" },
    ) {
        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(level, style = TextStyle(fontFamily = Hand, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (on) ink.ink else ink.pencil))
        }
    }
}

/** An evening: its day, its title (a story of several), then what was written down and sketched, or "Next evening". */
@Composable
private fun Evening(vm: AppViewModel, ev: NoteEvening, shows: Boolean, ink: Ink, r: Rules, speak: @Composable (String) -> Unit) {
    val target = L10n.pair.target
    Blank(r)
    val day = ev.day
    val date = if (day != null) inTarget("notebook.evening", "date" to Dates.dayMonth(day)) else inTarget("notebook.byFire")
    Text(
        date, style = hand(ink.ink, NotebookLook.DATE), textAlign = TextAlign.End,
        modifier = Modifier.fillMaxWidth().onRule(r).semantics { contentDescription = if (day != null) bi("notebook.evening", "date" to Dates.dayMonth(day)) else bi("notebook.byFire") },
    )
    ev.title?.let { t ->
        val said = t.substringBefore(" · ")
        Text(said, style = hand(ink.ink, NotebookLook.HEADING, bold = true), modifier = Modifier.onRule(r).semantics { heading() })
        t.substringAfter(" · ", "").takeIf { it.isNotBlank() && it != said && L10n.pair.base != target }?.let {
            Text(it, style = hand(ink.pencil, NotebookLook.PENCIL), modifier = Modifier.onRule(r))
        }
    }
    if (ev.locked) {
        Text("🔒 ${bi("books.nextEvening")}", style = hand(ink.pencil, NotebookLook.PENCIL), modifier = Modifier.onRule(r))
        return
    }
    for (b in ev.blocks) when (b) {
        is NoteBlock.Text -> {
            Paragraph(vm, b, shows, ink, r, speak)
            Blank(r)
        }
        is NoteBlock.Sketch -> SketchBlock(b.picture, shows, ink, r)
    }
}

/**
 * A paragraph written down: in ink, its words to tap; the translation under it in pencil (shown, or hidden with a tap
 * beside the words to show it); 🔊 on the right, level with its first line.
 */
@Composable
private fun Paragraph(vm: AppViewModel, t: NoteBlock.Text, translations: Boolean, ink: Ink, r: Rules, speak: @Composable (String) -> Unit) {
    var open by remember(t, translations) { mutableStateOf(false) }
    val shows = (translations || open) && t.meant.isNotBlank() && t.meant != t.said
    val onWord = lookUpIn(vm, t.said, t.meant, Words.READING)
    val tools = NotebookLook.TOOLS.dp
    Layout(
        content = {
            Column {
                WordText(t.said, onWord, modifier = Modifier.onRule(r), style = hand(ink.ink, NotebookLook.TEXT), onElse = if (!shows) ({ open = true }) else null)
                if (shows) Text(t.meant, style = hand(ink.pencil, NotebookLook.PENCIL), modifier = Modifier.onRule(r))
            }
            Box { speak(t.said) }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { ms, c ->
        val room = tools.roundToPx()
        val text = ms[0].measure(c.copy(minWidth = 0, maxWidth = (c.maxWidth - room).coerceAtLeast(0)))
        val tool = ms[1].measure(Constraints())
        layout(c.maxWidth, text.height) {
            text.place(0, 0)
            tool.place(c.maxWidth - tool.width + 6.dp.roundToPx(), ((r.line - tool.height) / 2f).roundToInt())
        }
    }
}

/**
 * A sketch pinned in: the picture as a pencil sketch on its card ([Sketch]), a little askew, taped at its top corners or
 * clipped, with a soft shadow; its caption by hand under it (the translation in pencil). TalkBack reads the caption.
 */
@Composable
private fun SketchBlock(p: StoryPicture, translations: Boolean, ink: Ink, r: Rules) {
    val pin = remember(p) { NotebookLook.pin(p) }
    val image by produceState(SketchCache.get(p), p) { if (value == null) value = withContext(Dispatchers.Default) { SketchCache.render(p) } }
    val said = p.caption?.sl.orEmpty()
    val meant = p.caption?.en.orEmpty()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().wholeLines(r).padding(top = 12.dp, bottom = 10.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxWidth(NotebookLook.CARD).aspectRatio(Sketch.W.toFloat() / Sketch.H)
                    .graphicsLayer { rotationZ = pin.tilt }
                    .drawBehind { drawRect(ink.shadow, topLeft = Offset(3.dp.toPx(), 4.dp.toPx()), size = size) }
                    .drawWithContent { drawContent(); fastening(pin, ink) }
                    .clearAndSetSemantics { contentDescription = listOf(said, meant).filter { it.isNotBlank() }.distinct().joinToString(" · ") },
            ) {
                val img = image
                if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Medium)
                else Box(Modifier.fillMaxSize().drawBehind { drawRect(Color(Sketch.CARD)) })
            }
        }
        if (said.isNotBlank()) Text(said, style = hand(ink.ink, NotebookLook.DATE), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().onRule(r).clearAndSetSemantics { })
        if (translations && meant.isNotBlank() && meant != said) Text(meant, style = hand(ink.pencil, NotebookLook.PENCIL), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().onRule(r).clearAndSetSemantics { })
        Blank(r)
    }
}

/** Two strips of tape over the card's top corners, or a paper clip on its top edge. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.fastening(pin: NotebookLook.Pin, ink: Ink) {
    when (pin.fastening) {
        NotebookLook.Fastening.TAPE -> {
            val w = 64.dp.toPx(); val h = 20.dp.toPx()
            for ((cx, angle) in listOf(0f to -38f, size.width to 38f)) {
                rotate(angle, pivot = Offset(cx, 0f)) {
                    drawRoundRect(ink.tape, topLeft = Offset(cx - w / 2, -h / 2), size = Size(w, h), cornerRadius = CornerRadius(2.dp.toPx()))
                    // its torn ends
                    for (k in 0 until 5) {
                        val y = -h / 2 + h * k / 5f
                        drawLine(ink.paper.copy(alpha = 0.5f), Offset(cx - w / 2, y), Offset(cx - w / 2 + 2.dp.toPx(), y + h / 10f), strokeWidth = 1.dp.toPx())
                        drawLine(ink.paper.copy(alpha = 0.5f), Offset(cx + w / 2, y), Offset(cx + w / 2 - 2.dp.toPx(), y + h / 10f), strokeWidth = 1.dp.toPx())
                    }
                }
            }
        }
        NotebookLook.Fastening.CLIP -> {
            val x = size.width * pin.at
            val s = 1.dp.toPx()
            val wire = Stroke(2.2f * s, cap = StrokeCap.Round)
            // a paper clip: an outer loop round an inner one, over the card's top edge
            val outer = Path().apply {
                moveTo(x - 5 * s, 30 * s); lineTo(x - 5 * s, -12 * s)
                cubicTo(x - 5 * s, -20 * s, x + 7 * s, -20 * s, x + 7 * s, -12 * s)
                lineTo(x + 7 * s, 22 * s)
                cubicTo(x + 7 * s, 27 * s, x - 1 * s, 27 * s, x - 1 * s, 22 * s)
                lineTo(x - 1 * s, -7 * s)
            }
            drawPath(outer, ink.shadow, style = Stroke(3f * s, cap = StrokeCap.Round))
            drawPath(outer, ink.clip, style = wire)
        }
    }
}

/** The sketches drawn, the last few kept: a sketch is drawn off the main thread once, and shown again at once. */
private object SketchCache {
    private val kept = object : LinkedHashMap<Int, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ImageBitmap>?): Boolean = size > 16
    }

    fun get(p: StoryPicture): ImageBitmap? = synchronized(kept) { kept[key(p)] }

    fun render(p: StoryPicture): ImageBitmap {
        get(p)?.let { return it }
        val c = Sketch.render(p)
        val img = Bitmap.createBitmap(c.width, c.height, Bitmap.Config.ARGB_8888).apply { setPixels(c.pixels, 0, c.width, 0, 0, c.width, c.height) }.asImageBitmap()
        synchronized(kept) { kept[key(p)] = img }
        return img
    }

    private fun key(p: StoryPicture): Int = p.hashCode()
}

/** A notebook's row where it is listed (the chest, the reading corner, the storyteller): its name, how many stories and sketches. */
@Composable
fun NotebookRow(entries: List<NotebookEntry>, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val look = if (isSystemInDarkTheme()) NotebookLook.NIGHT else NotebookLook.DAY
    val ink = remember(look) { Ink(look) }
    Surface(onClick = onClick, color = ink.paper, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, ink.rule), modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📓", fontSize = 26.sp, modifier = Modifier.clearAndSetSemantics { })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(bi("notebook.title"), style = TextStyle(fontFamily = Hand, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = ink.ink))
                Text(
                    if (entries.isEmpty()) bi("notebook.about")
                    else "${inTarget("notebook.stories", "n" to entries.size)} · ${inTarget("notebook.sketches", "n" to sketchesIn(entries))}",
                    style = TextStyle(fontFamily = Hand, fontSize = 15.sp, color = ink.pencil),
                )
            }
            Text("›", fontSize = 24.sp, color = ink.ink, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}
