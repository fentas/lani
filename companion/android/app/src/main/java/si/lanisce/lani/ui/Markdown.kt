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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
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
private val inline = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`|„(.+?)[“”]""")

/** Plays a Slovene span when tapped; null where nothing can speak. */
class SpeakSpans(val isSlovene: (String) -> Boolean, val say: (String) -> Unit)

fun inlineMarkdown(s: String, speak: SpeakSpans? = null): AnnotatedString = buildAnnotatedString {
    var i = 0
    for (m in inline.findAll(s)) {
        append(s.substring(i, m.range.first))
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
            speak != null && speak.isSlovene(body) -> {
                // Tap to hear the Slovene: underlined, with a small speaker.
                val link = LinkAnnotation.Clickable(
                    tag = "say",
                    styles = TextLinkStyles(style.merge(SpanStyle(textDecoration = TextDecoration.Underline))),
                    linkInteractionListener = { speak.say(body) },
                )
                withLink(link) { append(content(null)); append(" 🔈") }
            }
            else -> withStyle(style) { append(content(speak)) }
        }
        i = m.range.last + 1
    }
    append(s.substring(i))
}

private val numbered = Regex("""^(\d+)[.)]\s+(.*)""")
private val tableSeparator = Regex("""^\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?$""")

private fun cells(row: String): List<String> =
    row.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

@Composable
fun Markdown(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val speaker = LocalSpeaker.current
    val speak = speaker?.takeIf { it.canSay }?.let { s -> SpeakSpans(s::isSlovene) { t -> s.say(t) } }
    val lines = text.trim().lines()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var i = 0
        while (i < lines.size) {
            val t = lines[i].trimEnd()
            // A table: a block of lines starting with "|", optionally with a --- separator after the header.
            if (t.trimStart().startsWith("|")) {
                val block = lines.drop(i).takeWhile { it.trimStart().startsWith("|") }
                i += block.size
                val header = block.getOrNull(1)?.let { tableSeparator.matches(it.trim()) } == true
                val rows = block.filterIndexed { n, _ -> !(header && n == 1) }.map(::cells)
                MarkdownTable(rows, header, color, speak)
                continue
            }
            i++
            when {
                t.isBlank() -> Unit
                t.trim().matches(Regex("""^(-{3,}|\*{3,}|_{3,})$""")) -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
                t.startsWith("#") -> Text(
                    inlineMarkdown(t.trimStart('#', ' '), speak),
                    color = color,
                    style = if (t.startsWith("# ")) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                t.startsWith(">") -> {
                    val bar = MaterialTheme.colorScheme.primary
                    Text(
                        inlineMarkdown(t.removePrefix(">").trim(), speak),
                        Modifier
                            .fillMaxWidth()
                            .background(bar.copy(alpha = 0.08f), MaterialTheme.shapes.small)
                            .drawBehind { drawRect(bar, size = Size(4.dp.toPx(), size.height)) }
                            .padding(start = 14.dp, top = 8.dp, end = 10.dp, bottom = 8.dp),
                        color = color,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                t.startsWith("- ") || t.startsWith("• ") || t.startsWith("* ") -> Row {
                    Text("•  ", color = MaterialTheme.colorScheme.primary)
                    Text(inlineMarkdown(t.drop(2), speak), color = color, style = MaterialTheme.typography.bodyMedium)
                }
                numbered.matches(t) -> {
                    val (n, rest) = numbered.find(t)!!.destructured
                    Row {
                        Text("$n.  ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(inlineMarkdown(rest, speak), color = color, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                else -> Text(inlineMarkdown(t, speak), color = color, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** A bordered grid; scrolls sideways when wider than the bubble instead of squeezing the cells. */
@Composable
private fun MarkdownTable(rows: List<List<String>>, header: Boolean, color: Color, speak: SpeakSpans?) {
    val columns = rows.maxOfOrNull { it.size } ?: return
    // One width per column (from its longest cell), so the columns line up across rows.
    val widths = (0 until columns).map { c ->
        (rows.maxOf { it.getOrElse(c) { "" }.length } * 7.5f + 20f).coerceIn(64f, 200f).dp
    }
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .horizontalScroll(rememberScrollState())
            .border(1.dp, line, MaterialTheme.shapes.small),
    ) {
        rows.forEachIndexed { r, row ->
            val head = header && r == 0
            Row(
                Modifier
                    .height(IntrinsicSize.Min)
                    .then(if (head) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)) else Modifier),
            ) {
                for (c in 0 until columns) {
                    val cell = row.getOrElse(c) { "" }
                    Text(
                        inlineMarkdown(cell, speak),
                        Modifier
                            .width(widths[c])
                            .then(if (c > 0) Modifier.drawBehind { drawRect(line, size = Size(1.dp.toPx(), size.height)) } else Modifier)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        color = color,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (head) FontWeight.Bold else null,
                    )
                }
            }
            if (r < rows.lastIndex) HorizontalDivider(color = line)
        }
    }
}
