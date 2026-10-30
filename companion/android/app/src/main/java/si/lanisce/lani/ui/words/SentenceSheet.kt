package si.lanisce.lani.ui.words

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.SentenceCard
import si.lanisce.lani.app.WordQuery
import si.lanisce.lani.data.CaseLens
import si.lanisce.lani.data.CaseRow
import si.lanisce.lani.data.Words
import si.lanisce.lani.game.SentenceGrammar
import si.lanisce.lani.game.TurnHints
import si.lanisce.lani.game.WordRow
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.LaniSheet
import si.lanisce.lani.ui.Markdown
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.cases.CaseNames
import si.lanisce.lani.ui.cases.color

/**
 * "🔍 Slovnica stavka · The sentence's grammar" (a long press on a dialog's line; companion/SCENES.md): the line with 🔊
 * and its translation, then each word: as written, its dictionary form and reading (a tap opens its card), its case as
 * the case lens reads it (the word card's row: "Kam? → 4. tožilnik · Po predlogu «v»: tožilnik"), and its page (📖, on a
 * sheet over this one); the tutor's
 * explanation kept from an earlier question, and "💬 Vprašaj učitelja · Ask your tutor".
 */
@Composable
fun SentenceSheet(vm: AppViewModel, card: SentenceCard) {
    val q = card.query
    LaniSheet(onDismissRequest = vm.sentences::close) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "🔍 ${bi("sentence.title")}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(q.sentence, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                SpeakButton(vm.speaker, q.sentence, Modifier.padding(start = 8.dp))
            }
            if (q.en.isNotBlank()) Text(q.en, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                card.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(bi("words.lookingUp"), style = MaterialTheme.typography.bodyMedium)
                }
                card.offline -> Text("📶 ${bi("sentence.offline")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            for (row in card.rows) WordLine(vm, row, onWord = { vm.words.open(WordQuery(row.word, q.sentence, q.en.takeIf { it.isNotBlank() }, Words.SCENE)) })
            card.note?.let { note ->
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("🧑‍🏫 ${bi("sentence.tutorNote")}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Markdown(note, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
            FilledTonalButton(onClick = vm.sentences::ask, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("💬 ${bi("common.askTutor")}") }
        }
    }
}

/**
 * One word: as written, and its dictionary form, part of speech and reading ("krav → krava · samostalnik · noun —
 * rodilnik, mn. · genitive, pl."; a tap opens its card). A word with a case shows what the case lens says of it, as
 * its card's "🧩 Sklon · Case" row does: the case in its colour (likely, or the readings the line leaves), the question,
 * what decided it (Slovene, then the base); a preposition what it decides; a verb who does it or its tense. A 📖 to its
 * page.
 */
@Composable
private fun WordLine(vm: AppViewModel, row: WordRow, onWord: () -> Unit) {
    val lens = row.why?.lens
    val read = row.why?.read
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.clickable(onClick = onWord).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val form = row.lemma?.takeIf { !it.equals(row.word, ignoreCase = true) }?.let { " → $it" }.orEmpty()
            Text(row.word + form, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val pos = row.pos?.takeIf { it.isNotBlank() && it != "word" }?.let(::posLabel)
            val case = lens?.case?.takeIf { lens.state == CaseRow.State.DECIDED || lens.state == CaseRow.State.LIKELY }
            when {
                case != null -> Text(
                    listOfNotNull(pos, CaseNames.bi(case, lens.number, gender = lens.gender) + if (lens.state == CaseRow.State.LIKELY) " · ${bi("caseLens.likely")}" else "").joinToString(" — "),
                    style = MaterialTheme.typography.labelLarge, color = case.color(), fontWeight = FontWeight.SemiBold,
                )
                lens?.state == CaseRow.State.AMBIGUOUS -> Text(
                    listOfNotNull(pos, "${bi("caseLens.ambiguous")}: " + lens.options.joinToString(", ") { CaseNames.bi(it.case, it.number, gender = it.gender) }).joinToString(" — "),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                )
                else -> listOfNotNull(pos, row.reading?.takeIf { it.isNotBlank() }).joinToString(" — ").takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            val why = if (lens != null && read != null) CaseLens.why(lens, read) else null
            when {
                lens?.state == CaseRow.State.GOVERNS -> for ((ref, c) in lens.governs) Text("👉 ${CaseLens.governs(c, ref.word)}", style = MaterialTheme.typography.bodyMedium, color = c.color())
                lens != null -> {
                    row.why?.line?.let { Text("👉 ${TurnHints.caseLine(it)}", style = MaterialTheme.typography.bodyMedium) }
                    why?.let { (target, base) ->
                        Text(target, style = MaterialTheme.typography.bodyMedium)
                        if (base != target) Text(base, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> row.why?.let(SentenceGrammar::why)?.let { Text("👉 $it", style = MaterialTheme.typography.bodyMedium) }
            }
            row.page?.let { id -> vm.grammar.page(id) }?.let { p ->
                AssistChip(
                    onClick = { vm.grammar.show(p.id) },
                    label = { Text("📖 ${p.titleShown(L10n.pair)}", style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}
