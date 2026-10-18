package si.lanisce.lani.ui

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.FormsLook
import si.lanisce.lani.app.FormsView
import si.lanisce.lani.data.FormsCard
import si.lanisce.lani.data.FormsCell
import si.lanisce.lani.data.FormsCellState
import si.lanisce.lani.data.FormsSection
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.theme.AlpineGreen

// A word's whole table to read ("📋 Vse oblike · All forms", data/FormsCard.kt `overview`; companion/README.md "Forms
// cards"), never a test: every section (a verb's present, negative, future, past, imperative; a noun's cases; an adjective's
// genders and cases), the word's own form bold, the forms the learner practises quietly marked (a light green behind
// them: their page introduced, as a forms card asks them), the others plain and a little grey, each row's 🔊. Opened from a
// word's card (then "🃏 Vadi oblike · Practise the forms" makes it a card) or at a forms card's end (over the run).

/** The width of a row's label ("2. koga? česa?"). */
private val LABEL = 76.dp

/** The overview [v] in a sheet over any screen; closing it goes back to what it was opened from. */
@Composable
fun FormsOverviewSheet(vm: AppViewModel, v: FormsView) {
    LaniSheet(onDismissRequest = vm.formsCards::closeView) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("📋 ${bi("formsView.title")}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (val look = v.look) {
                FormsLook.Loading -> {
                    FormsHead(v.emoji, v.lemma, null, v.pos.orEmpty(), v.meaning)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(bi("words.lookingUp"), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                FormsLook.Offline -> {
                    FormsHead(v.emoji, v.lemma, null, v.pos.orEmpty(), v.meaning)
                    Text("📶 ${bi("formsCard.offline")}", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = vm.formsCards::retryView, modifier = Modifier.heightIn(min = 48.dp)) { Text("↻ ${bi("common.retry")}") }
                }
                FormsLook.None -> {
                    FormsHead(v.emoji, v.lemma, null, v.pos.orEmpty(), v.meaning)
                    Text("🤷 ${bi("formsCard.noTable")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is FormsLook.Ready -> Overview(vm, look)
            }
            if (!v.inRun && v.look !is FormsLook.None) PractiseForms(vm, v.lemma, v.itemId)
        }
    }
}

/** The table ready: the word, how many of its forms the learner practises, every section, what the marks mean. */
@Composable
private fun Overview(vm: AppViewModel, look: FormsLook.Ready) {
    val introduced = vm.formsCards.introduced()
    val pair = vm.langPair
    val o = remember(look.data, introduced, pair) { FormsCard.overview(look.data, introduced) }
    // QA reads the sections and how many forms are marked (a debug or qa build)
    if (si.lanisce.lani.BuildConfig.QA) LaunchedEffect(o) {
        android.util.Log.i("FormsView", "${o.lemma} sections ${o.sections.joinToString("|") { it.title }} practised ${o.practised}/${o.forms}")
    }
    FormsHead(o.emoji, o.lemma, o.gender, o.pos, o.meaning)
    Text(
        if (o.practised > 0) "✓ ${bi("formsView.practised", "n" to o.practised, "of" to o.forms)}" else "🌱 ${bi("formsView.noneYet")}",
        style = MaterialTheme.typography.bodyMedium,
        color = if (o.practised > 0) AlpineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
    for (s in o.sections) Section(vm, s)
    Text(bi("formsView.legend"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    if (o.checked) Text("🤖 ${bi("words.machineWritten")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    look.attribution?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A section: its title, its columns' heads, its rows (a label, its cells, the row's 🔊). */
@Composable
private fun Section(vm: AppViewModel, s: FormsSection) {
    Text(s.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
    val labelled = s.rows.any { it.label.isNotBlank() }
    Row(Modifier.fillMaxWidth()) {
        if (labelled) Spacer(Modifier.width(LABEL))
        for (c in s.columns) Text(c, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(40.dp))
    }
    for (r in s.rows) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (labelled) Text(r.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(LABEL))
            for (cell in r.cells) {
                Column(Modifier.weight(1f).padding(end = 4.dp)) {
                    if (cell == null) Text("–", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Cell(cell)
                }
            }
            Row(Modifier.width(40.dp)) { SpeakButton(vm.speaker, r.said) }
        }
    }
}

/**
 * A cell to read: the words before it small, its form; the word's own bold, one the learner practises on a light green
 * (TalkBack: "…, vadiš · you practise it"), one not introduced yet plain and a little grey.
 */
@Composable
private fun Cell(cell: FormsCell) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (cell.before.isNotBlank()) Text(cell.before, style = MaterialTheme.typography.labelSmall, color = muted)
    val text = cell.form + cell.after
    when (cell.state) {
        FormsCellState.GIVEN -> Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        FormsCellState.TESTED, FormsCellState.GAP -> Surface(
            color = AlpineGreen.copy(alpha = 0.14f), shape = MaterialTheme.shapes.small,
            modifier = Modifier.clearAndSetSemantics { contentDescription = "${cell.text}, ${bi("formsView.practisedCell")}" },
        ) { Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 4.dp)) }
        FormsCellState.PREVIEW -> Text(
            text, style = MaterialTheme.typography.bodyMedium, color = muted.copy(alpha = 0.8f),
            modifier = Modifier.semantics { contentDescription = "${cell.text}, ${bi("formsCard.preview")}" },
        )
    }
}
