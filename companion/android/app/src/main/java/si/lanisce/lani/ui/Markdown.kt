package si.lanisce.lani.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

// Markdown for tutor texts: paragraphs, # headings, > quotes, - bullets, 1. lists, --- rules, | tables |,
// **bold**, *italic*, `code`, and „quotes". Slovene in bold, italics or quotes can be tapped to hear it.
// The spans as a step of a unit's introduction reads them (IntroSpeech.segments): one regex for both.
private val inline = si.lanisce.lani.data.IntroSpeech.INLINE

/** Plays a Slovene span when tapped; null where nothing can speak. */
class SpeakSpans(val isSlovene: (String) -> Boolean, val say: (String) -> Unit)

/**
 * [s]'s inline Markdown. [speak]: a Slovene span plays when tapped; [look]: instead, each word of a Slovene span (one
 * without more Markdown in it) is looked up when tapped (the word, and the span it is in). [mark]: the segments being
 * read aloud (their indices in [si.lanisce.lani.data.IntroSpeech.segments]: the plain text and the spans, in order) are
 * drawn in [markStyle].
 */
fun inlineMarkdown(
    s: String,
    speak: SpeakSpans? = null,
    look: ((String, String) -> Unit)? = null,
    mark: IntRange? = null,
    markStyle: SpanStyle = MARK,
): AnnotatedString = buildAnnotatedString {
    var i = 0
    var k = 0
    /** The segment appended since [from] is the next one: marked when it's read now. */
    fun segment(from: Int) {
        if (mark != null && k in mark && length > from) addStyle(markStyle, from, length)
        k++
    }
    for (m in inline.findAll(s)) {
        if (m.range.first > i) {
            val from = length
            append(s.substring(i, m.range.first))
            segment(from)
        }
        val from = length
        val (bold, italic, code, quoted) = m.destructured
        val body = bold.ifEmpty { italic }.ifEmpty { quoted }
        val style = when {
            bold.isNotEmpty() -> SpanStyle(fontWeight = FontWeight.Bold)
            italic.isNotEmpty() -> SpanStyle(fontStyle = FontStyle.Italic)
            quoted.isNotEmpty() -> SpanStyle()
            else -> SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22888888))
        }
        // Recurse into the inner text only (the quotes stay outside), so nesting terminates.
        fun content(sp: SpeakSpans?) = buildAnnotatedString {
            if (quoted.isNotEmpty()) append("„")
            append(inlineMarkdown(body, sp))
            if (quoted.isNotEmpty()) append("“")
        }
        when {
            code.isNotEmpty() -> withStyle(style) { append(code) }
            look != null && speak != null && speak.isSlovene(body) && !body.any { it in "*`„" } -> {
                // each word a link of its own: a tap looks it up (a cheat sheet's card)
                if (quoted.isNotEmpty()) append("„")
                withStyle(style) {
                    var j = 0
                    for (w in WORD.findAll(body)) {
                        append(body.substring(j, w.range.first))
                        val link = LinkAnnotation.Clickable(
                            tag = "word",
                            styles = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline)),
                            linkInteractionListener = { look(w.value, body) },
                        )
                        withLink(link) { append(w.value) }
                        j = w.range.last + 1
                    }
                    append(body.substring(j))
                }
                if (quoted.isNotEmpty()) append("“")
            }
            speak != null && speak.isSlovene(body) -> {
                // Tap to hear the Slovene: underlined, with a small speaker.
                val link = LinkAnnotation.Clickable(
                    tag = "say",
                    styles = TextLinkStyles(style.merge(SpanStyle(textDecoration = TextDecoration.Underline))),
                    linkInteractionListener = { speak.say(body) },
                )
                withLink(link) { append(content(null)); append(" 🔈") }
            }
            else -> withStyle(style) { append(if (look != null) buildAnnotatedString { if (quoted.isNotEmpty()) append("„"); append(inlineMarkdown(body, speak, look)); if (quoted.isNotEmpty()) append("“") } else content(speak)) }
        }
        segment(from)
        i = m.range.last + 1
    }
    if (i < s.length) {
        val from = length
        append(s.substring(i))
        segment(from)
    }
}

/** How the words being read aloud are marked: a soft gold behind them. */
val MARK = SpanStyle(background = Color(0x66F2C14E))

private val numbered = Regex("""^(\d+)[.)]\s+(.*)""")
private val RULE = Regex("""^(-{3,}|\*{3,}|_{3,})$""")

/** A word of a span, for its own link. */
private val WORD = Regex("""[\p{L}\p{N}]+(?:['’][\p{L}\p{N}]+)*""")
private val tableSeparator = Regex("""^\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?$""")

private fun cells(row: String): List<String> =
    row.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

/**
 * [text] as plain text, as its bubble reads ([Markdown]'s blocks): what "📄 Kopiraj · Copy" and "↗️ Deli · Share" give
 * of a tutor message. Each line's inline marks out (bold, italics, code; „quotes“ kept, [inlineMarkdown]); a list item
 * "• …" or "1. …"; a table's rows a line each, their cells between " · " (empty ones and the header's rule left out); a
 * heading's # and a quote's > out; a --- rule a blank line; paragraphs a blank line apart, never more.
 */
fun markdownPlain(text: String): String {
    val lines = text.trim().lines()
    val out = mutableListOf<String>()
    fun flat(s: String) = inlineMarkdown(s).text
    var i = 0
    while (i < lines.size) {
        val t = lines[i].trimEnd()
        if (t.trimStart().startsWith("|")) {
            val table = lines.drop(i).takeWhile { it.trimStart().startsWith("|") }
            i += table.size
            val header = table.getOrNull(1)?.let { tableSeparator.matches(it.trim()) } == true
            table.filterIndexed { n, _ -> !(header && n == 1) }.forEach { row ->
                out += cells(row).map(::flat).filter { it.isNotBlank() }.joinToString(" · ")
            }
            continue
        }
        i++
        out += when {
            t.isBlank() || t.trim().matches(RULE) -> ""
            t.startsWith("#") -> flat(t.trimStart('#', ' '))
            t.startsWith(">") -> flat(t.removePrefix(">").trim())
            t.startsWith("- ") || t.startsWith("• ") || t.startsWith("* ") -> "• " + flat(t.drop(2))
            numbered.matches(t) -> numbered.find(t)!!.destructured.let { (n, rest) -> "$n. ${flat(rest)}" }
            else -> flat(t)
        }
    }
    return out.joinToString("\n").replace(Regex("""\n{3,}"""), "\n\n").trim()
}

/**
 * How a block of [Markdown] draws its text, instead of a plain Text with tap-to-hear Slovene: [styled] (its inline
 * markdown), in [style] (the block's colour and weight in it), with [modifier]. An exercise's explanation: its words to
 * look up.
 */
typealias BlockText = @Composable (styled: AnnotatedString, style: TextStyle, modifier: Modifier) -> Unit

/**
 * [text]'s Markdown. [marked]: what is being read aloud now, by the line of [text] it is on (its index in
 * `text.trim().lines()`): those segments of the line ([si.lanisce.lani.data.IntroSpeech.Run.segments]) drawn marked.
 */
@Composable
fun Markdown(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    block: BlockText? = null,
    onWord: ((String, String) -> Unit)? = null,
    marked: ((line: Int) -> IntRange?)? = null,
    onMarked: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    val speaker = LocalSpeaker.current
    // a lookup ([onWord]: a cheat sheet's card) needs to know what's Slovene, not a voice to say it
    val speak = if (onWord != null) speaker?.let { s -> SpeakSpans(s::isSlovene) { t -> if (s.canSay) s.say(t) } }
    else speaker?.takeIf { it.canSay }?.let { s -> SpeakSpans(s::isSlovene) { t -> s.say(t) } }
    val look = onWord
    val lines = text.trim().lines()
    /** The source line drawn now (-1: a table's cell), for what is marked. */
    var at = -1
    /**
     * A block's text: [block]'s, else a Text whose Slovene in bold, italics or quotes is heard when tapped (or, with
     * [onWord], whose words are looked up).
     */
    val line: @Composable (String, TextStyle, FontWeight?, Modifier) -> Unit = { s, style, weight, m ->
        val mark = if (at >= 0) marked?.invoke(at) else null
        if (block != null) block(inlineMarkdown(s, mark = mark), style.merge(TextStyle(color = color, fontWeight = weight)), m)
        else if (mark != null && onMarked != null) MarkedText(inlineMarkdown(s, speak, look, mark = mark), m, color, style, weight, onMarked)
        else Text(inlineMarkdown(s, speak, look, mark = mark), m, color = color, style = style, fontWeight = weight)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var i = 0
        while (i < lines.size) {
            val t = lines[i].trimEnd()
            // A table: a block of lines starting with "|", optionally with a --- separator after the header.
            if (t.trimStart().startsWith("|")) {
                val table = lines.drop(i).takeWhile { it.trimStart().startsWith("|") }
                i += table.size
                val header = table.getOrNull(1)?.let { tableSeparator.matches(it.trim()) } == true
                val rows = table.filterIndexed { n, _ -> !(header && n == 1) }.map(::cells)
                at = -1
                MarkdownTable(rows, header, line)
                continue
            }
            at = i
            i++
            when {
                t.isBlank() -> Unit
                t.trim().matches(RULE) -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
                t.startsWith("#") -> line(
                    t.trimStart('#', ' '),
                    if (t.startsWith("# ")) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    FontWeight.Bold,
                    Modifier,
                )
                t.startsWith(">") -> {
                    val bar = MaterialTheme.colorScheme.primary
                    line(
                        t.removePrefix(">").trim(),
                        MaterialTheme.typography.bodyMedium,
                        null,
                        Modifier
                            .fillMaxWidth()
                            .background(bar.copy(alpha = 0.08f), MaterialTheme.shapes.small)
                            .drawBehind { drawRect(bar, size = Size(4.dp.toPx(), size.height)) }
                            .padding(start = 14.dp, top = 8.dp, end = 10.dp, bottom = 8.dp),
                    )
                }
                t.startsWith("- ") || t.startsWith("• ") || t.startsWith("* ") -> Row {
                    Text("•  ", color = MaterialTheme.colorScheme.primary)
                    line(t.drop(2), MaterialTheme.typography.bodyMedium, null, Modifier)
                }
                numbered.matches(t) -> {
                    val (n, rest) = numbered.find(t)!!.destructured
                    Row {
                        Text("$n.  ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        line(rest, MaterialTheme.typography.bodyMedium, null, Modifier)
                    }
                }
                else -> line(t, MaterialTheme.typography.bodyMedium, null, Modifier)
            }
        }
    }
}

/**
 * A line with a part marked ([MARK]): where that part is on the screen goes to [onMarked] (root px, as much of it as
 * shows), for the teacher's pointer on the school's stage.
 */
@Composable
private fun MarkedText(styled: AnnotatedString, modifier: Modifier, color: Color, style: TextStyle, weight: FontWeight?, onMarked: (androidx.compose.ui.geometry.Rect) -> Unit) {
    val laid = androidx.compose.runtime.remember { arrayOfNulls<androidx.compose.ui.text.TextLayoutResult>(1) }
    Text(
        styled,
        modifier.onGloballyPositioned { c ->
            val l = laid[0] ?: return@onGloballyPositioned
            val spans = styled.spanStyles.filter { it.item == MARK }
            if (spans.isEmpty()) return@onGloballyPositioned
            val r = l.getPathForRange(spans.minOf { it.start }, spans.maxOf { it.end }.coerceAtMost(styled.length)).getBounds()
            val seen = c.boundsInRoot()
            val at = androidx.compose.ui.geometry.Rect(c.localToRoot(r.topLeft), c.localToRoot(r.bottomRight))
            onMarked(if (at.overlaps(seen)) at.intersect(seen) else androidx.compose.ui.geometry.Rect.Zero)
        },
        color = color, style = style, fontWeight = weight, onTextLayout = { laid[0] = it },
    )
}

/**
 * A bordered grid; scrolls sideways when wider than the bubble instead of squeezing the cells. One that fits takes no
 * sideways drag, so a swipe on it is the chat bubble's ([SwipeRow]).
 */
@Composable
private fun MarkdownTable(rows: List<List<String>>, header: Boolean, text: @Composable (String, TextStyle, FontWeight?, Modifier) -> Unit) {
    val columns = rows.maxOfOrNull { it.size } ?: return
    // One width per column (from its longest cell), so the columns line up across rows.
    val widths = (0 until columns).map { c ->
        (rows.maxOf { it.getOrElse(c) { "" }.length } * 7.5f + 20f).coerceIn(64f, 200f).dp
    }
    val line = MaterialTheme.colorScheme.outlineVariant
    val scroll = rememberScrollState()
    Column(
        Modifier
            .horizontalScroll(scroll, enabled = scroll.maxValue > 0)
            .border(1.dp, line, MaterialTheme.shapes.small),
    ) {
        rows.forEachIndexed { r, row ->
            val head = header && r == 0
            Row(
                Modifier
                    .height(IntrinsicSize.Min)
                    // TalkBack reads a table row by row, its cells in one go
                    .semantics(mergeDescendants = true) { }
                    .then(if (head) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)) else Modifier),
            ) {
                for (c in 0 until columns) {
                    val cell = row.getOrElse(c) { "" }
                    text(
                        cell,
                        MaterialTheme.typography.bodySmall,
                        if (head) FontWeight.Bold else null,
                        Modifier
                            .width(widths[c])
                            .then(if (c > 0) Modifier.drawBehind { drawRect(line, size = Size(1.dp.toPx(), size.height)) } else Modifier)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            if (r < rows.lastIndex) HorizontalDivider(color = line)
        }
    }
}
