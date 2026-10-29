package si.lanisce.lani.ui.words

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.CaseChoice
import si.lanisce.lani.data.CaseRead
import si.lanisce.lani.data.CaseRow
import si.lanisce.lani.game.Case
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget

/**
 * The word card's line («Kavo pijem brez mleka.») with what the case lens found in it: the tapped word in its case's
 * colour, the word that decided it underlined ("brez mleka"); as it was without a [read] or for a word without a case.
 * [line] is the line as sent (its offsets are the lens's).
 */
@Composable
fun caseLine(line: String, read: CaseRead?, row: CaseRow?): AnnotatedString {
    val colour = row?.case?.let { CaseLook.colour(it) }
    return buildAnnotatedString {
        append("«")
        append(line)
        append("»")
        if (read == null || row == null) return@buildAnnotatedString
        val fits = { s: Int, e: Int -> s in 0 until e && e <= line.length }
        if (fits(read.start, read.end)) addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Normal, color = colour ?: Color.Unspecified), read.start + 1, read.end + 1)
        // the word that decided it, underlined; an adjective's noun, dotted (agrees)
        read.trigger?.takeIf { row.state != CaseRow.State.AMBIGUOUS || row.kind == "preposition" }?.let { t ->
            if (fits(t.start, t.end)) addStyle(SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Normal), t.start + 1, t.end + 1)
        }
        read.agrees?.let { a -> if (fits(a.start, a.end)) addStyle(SpanStyle(color = colour ?: Color.Unspecified, fontStyle = FontStyle.Normal), a.start + 1, a.end + 1) }
        // a trigger tapped: the words it decides, each in its case's colour
        for ((ref, case) in row.governs) {
            val g = read.governs?.words?.firstOrNull { it.i == ref.i } ?: continue
            if (fits(g.start, g.end)) addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = CaseLook.colour(case, false)), g.start + 1, g.end + 1)
        }
    }
}

/**
 * "🧩 Sklon · Case" on a word card (docs/plans/05-built/case-lens.md): the case in its colour with its number (and an
 * adjective's gender), the school's question words, what decided it in one line (Slovene, then the learner's base), the
 * grammar page's link; a reading the line can't decide shows all its readings, the likely one first; a trigger tapped
 * says what it decides. TalkBack hears the case, the question and what decided it in one go.
 */
@Composable
fun CaseRowView(vm: AppViewModel, row: CaseRow, read: CaseRead) {
    val main = row.case
    val accent = main?.let { CaseLook.colour(it) } ?: MaterialTheme.colorScheme.primary
    val why = whyLine(row, read)
    val title = when (row.state) {
        CaseRow.State.GOVERNS -> null
        else -> main?.let { CaseLook.bi(it, row.number) + (CaseLook.genderKey(row.gender)?.let { g -> ", ${inTarget(g)}" } ?: "") }
    }
    val spoken = listOfNotNull(
        bi("caseLens.title"),
        when (row.state) {
            CaseRow.State.AMBIGUOUS -> "${inBase("caseLens.ambiguous")}: " + row.options.joinToString(", ") { spokenName(it) }
            CaseRow.State.GOVERNS -> row.governs.joinToString(", ") { (ref, c) -> governsText(c, wordAt(read, ref)) }
            else -> main?.let { "${spokenName(CaseChoice(it, row.number ?: "sg", row.gender))}, ${CaseLook.question(it)}" + if (row.state == CaseRow.State.LIKELY) " (${inBase("caseLens.likely")})" else "" }
        },
        why?.let { "${it.first} ${it.second}" },
    ).joinToString(". ")
    Surface(
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = spoken },
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧩 ${bi("caseLens.title")}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).semantics { heading() })
                if (row.state == CaseRow.State.LIKELY) Text(bi("caseLens.likely"), style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
            }
            when (row.state) {
                CaseRow.State.AMBIGUOUS -> {
                    Text(bi("caseLens.ambiguous"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    for ((k, o) in row.options.withIndex()) ChoiceLine(o, likely = k == 0 && row.options.size > 1)
                }
                CaseRow.State.GOVERNS -> for ((ref, c) in row.governs) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(CaseLook.colour(c))
                        Spacer(Modifier.width(8.dp))
                        Text(governsText(c, wordAt(read, ref)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = CaseLook.colour(c))
                    }
                }
                else -> if (main != null && title != null) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
                    Text(CaseLook.question(main), style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
                    if (row.state == CaseRow.State.LIKELY) for (o in row.options.drop(1)) ChoiceLine(o, likely = false)
                }
            }
            why?.let { (target, base) ->
                Text(target, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                if (base != target) Text(base, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            row.agrees?.let { a ->
                Text("🔗 ${agreesText(a.word)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (row.derived) Text("〰 ${bi("caseLens.derived")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (row.others.isNotEmpty() && row.state != CaseRow.State.GOVERNS) {
                Text(
                    "${bi("caseLens.alsoForm")}: " + row.others.joinToString(", ") { CaseLook.name(it.case, it.number, L10n.pair.target) },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val page = row.rule?.let { vm.grammar.page(it) }
            if (page != null) {
                TextButton(onClick = { vm.words.close(); vm.grammar.show(row.rule) }, modifier = Modifier.heightIn(min = 48.dp).padding(top = 2.dp)) {
                    Text("📖 ${page.titleShown(L10n.pair)} ›", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** One reading the line leaves: its colour, case and number, question words; the likely one says so. */
@Composable
private fun ChoiceLine(o: CaseChoice, likely: Boolean) {
    val c = CaseLook.colour(o.case)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics { }) {
        Dot(c)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                CaseLook.bi(o.case, o.number) + (CaseLook.genderKey(o.gender)?.let { g -> ", ${inTarget(g)}" } ?: "") + if (likely) "  · ${bi("caseLens.likely")}" else "",
                style = MaterialTheme.typography.titleSmall, fontWeight = if (likely) FontWeight.Bold else FontWeight.Medium, color = c,
            )
            Text(CaseLook.question(o.case), style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
        }
    }
}

@Composable
private fun Dot(c: Color) = Box(Modifier.size(12.dp).clip(CircleShape).background(c))

/** The word at [ref]'s place in the lens's line (a trigger's governed word). */
private fun wordAt(read: CaseRead, ref: si.lanisce.lani.data.CaseRef): String =
    read.governs?.words?.firstOrNull { it.i == ref.i }?.word?.takeIf { it.isNotBlank() } ?: ref.word

private fun spokenName(o: CaseChoice): String {
    val base = L10n.pair.base
    return CaseLook.name(o.case, o.number, L10n.pair.target, short = false) + " · " + CaseLook.name(o.case, o.number, base, short = false)
}

/** "Zahteva rodilnik: «mleka» · Takes the genitive: «mleka»". */
private fun governsText(c: Case, word: String): String {
    val pair = L10n.pair
    val t = L10n.text(pair.target, "caseLens.governs", mapOf("case" to L10n.text(pair.target, c.key), "word" to word))
    val b = L10n.text(pair.base, "caseLens.governs", mapOf("case" to L10n.text(pair.base, c.key), "word" to word))
    return if (t == b) t else "$t · $b"
}

/** "Ujema se z «hišo» · Agrees with «hišo»": z or s by the word's first sound (s before p, t, k, f, h, c, č, s, š). */
private fun agreesText(word: String): String = bi("caseLens.agrees", "word" to word, "zs" to if (word.lowercase().firstOrNull()?.let { it in "ptkfhcčsš" } == true) "s" else "z")

/**
 * What decided the case, in one line, the target's then the base's: a preposition (with its sense: kam? kje? kdaj?),
 * a negation, a quantity or a number from five, a verb or a word of feeling, a copula, the subject's or the object's
 * verb, a call, the noun it belongs to, a time, a phrase, a fused pronoun, its ending alone. None when nothing can say.
 */
private fun whyLine(row: CaseRow, read: CaseRead): Pair<String, String>? {
    if (row.state == CaseRow.State.GOVERNS) return null
    val word = read.trigger?.word.orEmpty()
    val case = row.case
    val key = when (row.kind) {
        "preposition" -> "caseLens.whyPreposition"
        "negation" -> "caseLens.whyNegation"
        "quantity" -> "caseLens.whyQuantity"
        "number" -> "caseLens.whyNumber"
        "verb" -> if (read.trigger?.lemma != null) "caseLens.whyVerb" else "caseLens.whyVerbForm"
        "predicate" -> "caseLens.whyPredicate"
        "copula" -> "caseLens.whyCopula"
        "predicative" -> "caseLens.whyPredicative"
        "subject" -> if (word.isNotEmpty()) "caseLens.whySubject" else "caseLens.whySubjectAlone"
        "object" -> if (word.isNotEmpty()) "caseLens.whyObject" else "caseLens.whyObjectAlone"
        "address" -> "caseLens.whyAddress"
        "attribute" -> "caseLens.whyAttribute"
        "time" -> "caseLens.whyTime"
        "phrase" -> "caseLens.whyPhrase"
        "fused" -> "caseLens.whyFused"
        else -> when (case) {
            Case.DAT -> "caseLens.whyFormDative"
            Case.GEN -> "caseLens.whyFormGenitive"
            Case.NOM -> "caseLens.whyFormNominative"
            else -> "caseLens.whyForm"
        }
    }
    if (row.state == CaseRow.State.AMBIGUOUS && row.kind != "preposition" && row.kind != "negation") return null
    val pair = L10n.pair
    val say = { lang: Lang ->
        val args = mapOf("word" to word, "lemma" to (read.trigger?.lemma ?: ""), "case" to (case?.let { L10n.text(lang, it.key) } ?: ""))
        val sense = row.sense?.let { senseKey(it) }?.let { " (${L10n.text(lang, it)})" }.orEmpty()
        L10n.text(lang, key, args) + if (row.kind == "preposition") sense else ""
    }
    return say(pair.target) to say(pair.base)
}

/** A preposition's sense, said as the school asks it: kam?, kje?, kdaj? … */
private fun senseKey(sense: String): String? = when (sense) {
    "motion" -> "caseLens.senseMotion"
    "rest" -> "caseLens.senseRest"
    "time" -> "caseLens.senseTime"
    "with" -> "caseLens.senseWith"
    "from" -> "caseLens.senseFrom"
    "for" -> "caseLens.senseFor"
    "about" -> "caseLens.senseAbout"
    "after" -> "caseLens.senseAfter"
    "towards" -> "caseLens.senseTowards"
    "fetch" -> "caseLens.senseFetch"
    else -> null
}

/** The colours' legend, under the toggle: each case's colour, name and questions; dotted for a word the line can't tell. */
@Composable
fun CaseLegend(modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (c in Case.entries) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { }) {
                Box(Modifier.width(14.dp).size(width = 14.dp, height = 3.dp).background(CaseLook.colour(c)))
                Spacer(Modifier.width(4.dp))
                Text("${c.n}. ${inTarget(c.key)}", style = MaterialTheme.typography.labelSmall, color = CaseLook.colour(c), fontWeight = FontWeight.SemiBold)
            }
        }
        Text("⋯ ${bi("caseLens.legendUnsure")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
