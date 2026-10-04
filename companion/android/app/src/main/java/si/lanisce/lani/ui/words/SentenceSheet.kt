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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import si.lanisce.lani.data.Words
import si.lanisce.lani.game.SentenceGrammar
import si.lanisce.lani.game.WordRow
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.Markdown
import si.lanisce.lani.ui.SpeakButton

/**
 * "🔍 Slovnica stavka · The sentence's grammar" (a long press on a dialog's line; companion/SCENES.md): the line with 🔊
 * and its translation, then each word: as written, its dictionary form and reading (a tap opens its card), why that form
 * where a rule knows ("after «v»: Kam? → 4. tožilnik"), and its page (📖, on a sheet over this one); the tutor's
 * explanation kept from an earlier question, and "💬 Vprašaj učitelja · Ask your tutor".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentenceSheet(vm: AppViewModel, card: SentenceCard) {
    val q = card.query
    ModalBottomSheet(onDismissRequest = vm.sentences::close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
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
 * genitive plural"; a tap opens its card); why that form, and a 📖 to its page.
 */
@Composable
private fun WordLine(vm: AppViewModel, row: WordRow, onWord: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.clickable(onClick = onWord).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val form = row.lemma?.takeIf { !it.equals(row.word, ignoreCase = true) }?.let { " → $it" }.orEmpty()
            Text(row.word + form, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val parts = listOfNotNull(row.pos?.takeIf { it.isNotBlank() && it != "word" }?.let(::posLabel), row.reading?.takeIf { it.isNotBlank() }).joinToString(" — ")
            if (parts.isNotEmpty()) Text(parts, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            row.why?.let(SentenceGrammar::why)?.let { Text("👉 $it", style = MaterialTheme.typography.bodyMedium) }
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
