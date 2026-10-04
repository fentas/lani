package si.lanisce.lani.ui.words

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.game.WordForms
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget

/**
 * "📚 Oblike · Forms" on the card of a word the learner has (companion/GAME.md, "A word's forms"): its forms as the
 * grammar book has reached them (a verb's present by person, its past, its imperative; a noun's six cases by number; an
 * adjective's genders), the rest 🔒 with the page that opens each ("🔒 6. sklon · instrumental — Orodnik, A2": a tap opens
 * the page, which introduces it). The forms come from the bridge (GET /forms, asked once when the card opens); nothing
 * shows for a word without any, or from an older bridge.
 */
@Composable
fun WordFormsTable(vm: AppViewModel, lemma: String, pos: String?, gloss: List<String>) {
    LaunchedEffect(lemma) { vm.forms.want(listOf(lemma)) }
    val entry = vm.forms.entryOf(lemma, pos, gloss) ?: return
    if (entry.slots.size < 2) return
    val state = vm.game.state
    val pages = vm.grammar.pages
    val sections = remember(entry, state, pages) { WordForms.table(entry, vm.grammar.formLocks()) }
    if (sections.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("📚 ${bi("wordForms.title")}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        for (s in sections) Section(s)
        val locked = WordForms.locked(sections)
        for ((page, what) in locked) {
            val p = vm.grammar.page(page)
            val name = p?.target?.substringBefore(':')?.trim() ?: page
            val text = "🔒 ${what.joinToString(", ")} — $name${p?.level?.let { ", $it" }.orEmpty()}"
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .then(if (p != null) Modifier.clickable(role = Role.Button) { vm.grammar.show(page) } else Modifier)
                    .padding(vertical = 4.dp),
            )
        }
        if (locked.isNotEmpty()) Text(bi("wordForms.lockedNote"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (entry.table == "checked") Text("🤖 ${bi("words.machineWritten")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A part of the table: its title, then its rows; the columns (singular, plural, dual) with nothing reached in them left out. */
@Composable
private fun Section(s: WordForms.Section) {
    val columns = s.columns.indices.filter { c -> s.rows.any { (_, cells) -> cells.getOrNull(c)?.open == true } }
    val anyOpen = s.rows.any { (_, cells) -> cells.any { it?.open == true } }
    Text(
        s.title + if (anyOpen) "" else " 🔒",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
    if (!anyOpen) return
    if (s.columns.isEmpty()) {
        // one row of forms: "sedi! · sedite! · sedimo!"
        val cells = s.rows.flatMap { it.second }.filterNotNull()
        Text(cells.joinToString(" · ") { if (it.open) it.text else "🔒" }, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val labelled = s.rows.any { it.first.isNotBlank() }
    Row(Modifier.fillMaxWidth()) {
        if (labelled) Text("", Modifier.width(LABEL))
        for (c in columns) {
            val code = s.columns[c]
            Text(
                inTarget(NUMBERS.getValue(code)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).semantics { contentDescription = bi(NUMBERS.getValue(code)) },
            )
        }
    }
    for ((label, cells) in s.rows) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (labelled) Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(LABEL))
            for (c in columns) {
                val cell = cells.getOrNull(c)
                Text(
                    when {
                        cell == null -> "–"
                        cell.open -> cell.text
                        else -> "🔒"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The columns' heads: the numbers. */
private val NUMBERS = mapOf("sg" to "wordForms.singular", "pl" to "wordForms.plural", "du" to "wordForms.dual")

/** The width of a row's label ("5. pri kom? pri čem?"). */
private val LABEL = 104.dp
