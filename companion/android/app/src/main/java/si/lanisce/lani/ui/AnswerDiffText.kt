package si.lanisce.lani.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import si.lanisce.lani.data.AnswerDiff
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.school.verdictColor
import si.lanisce.lani.ui.theme.AlpineGreen

/**
 * [pieces] ([AnswerDiff]) as text: what was typed and isn't right struck through in [wrong] (the verdict's colour) on a
 * tint of it, what is right instead green and bold on a tint of green (a single letter struck reads as another letter
 * without: c struck is an e), the rest as the right answer writes it, a word that may be left out in [quiet].
 */
fun diffText(pieces: List<AnswerDiff.Piece>, wrong: Color, quiet: Color): AnnotatedString = buildAnnotatedString {
    for (p in pieces) when (p.kind) {
        AnswerDiff.Kind.SAME, AnswerDiff.Kind.GAP -> append(p.text)
        AnswerDiff.Kind.TYPED -> withStyle(SpanStyle(color = wrong, textDecoration = TextDecoration.LineThrough, background = wrong.copy(alpha = TINT))) { append(p.text) }
        AnswerDiff.Kind.RIGHT -> withStyle(SpanStyle(color = AlpineGreen, fontWeight = FontWeight.Bold, background = AlpineGreen.copy(alpha = TINT))) { append(p.text) }
        AnswerDiff.Kind.GIVEN -> withStyle(SpanStyle(color = quiet)) { append(p.text) }
    }
}

/** How strong the tint under a typed or a right piece is. */
private const val TINT = 0.16f

/** What TalkBack reads for a correction: the right answer, then what was typed ("Prav je · Right: …. Tvoj odgovor · You wrote: …"). */
fun correctionSaid(typed: String, expected: String): String {
    val right = expected.trim()
    val end = if (right.lastOrNull()?.let { it in ".!?…" } == true) " " else ". "
    return "${bi("school.rightIs")}: $right" + (typed.trim().takeIf { it.isNotEmpty() }?.let { "$end${bi("playerScreen.wrote")}: $it" } ?: "")
}

/**
 * A correction where its answer was given (a worksheet's line, gap or cell, an exercise's feedback, a card's step): what
 * was typed ([typed]) set against what is right ([expected]; its first [optional] words may be left out) in one place,
 * "Spi~~ve~~**ta** v ~~s~~**š**otoru." ([AnswerDiff]), the struck letters in the [verdict]'s colour (red wrong, orange
 * almost). One line that wraps; TalkBack reads the right answer and what was typed as words ([correctionSaid]), not the
 * pieces run together.
 */
@Composable
fun AnswerDiffText(
    typed: String, expected: String, verdict: Verdict, modifier: Modifier = Modifier, optional: Int = 0,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val wrong = verdictColor(verdict)
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val pieces = remember(typed, expected, optional) { AnswerDiff.of(typed, expected, optional) }
    val said = correctionSaid(typed, expected)
    Text(remember(pieces, wrong, quiet) { diffText(pieces, wrong, quiet) }, style = style, modifier = modifier.clearAndSetSemantics { contentDescription = said })
}
