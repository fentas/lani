package si.lanisce.lani.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.RuleView
import si.lanisce.lani.data.GrammarCell
import si.lanisce.lani.data.GrammarPage
import si.lanisce.lani.data.Words
import si.lanisce.lani.game.GrammarBook
import si.lanisce.lani.game.Mastery
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.Labels
import si.lanisce.lani.ui.LaniSheet
import si.lanisce.lani.ui.Markdown
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.words.WordText
import si.lanisce.lani.ui.words.lookUpIn

/** The learner's language pair's base: what the rules are explained in. */
private val base: String get() = L10n.pair.base.code

/** "★★★☆☆" for a meter of 3. */
internal fun stars(n: Int): String = "★".repeat(n.coerceIn(0, 5)) + "☆".repeat(5 - n.coerceIn(0, 5))

/** How the rule's turns are asked now ([Mastery]): "🌱 Se učiš: izbiraš med odgovori · Learning: …"; none while new. */
internal fun masteryText(m: Mastery): String? = when (m) {
    Mastery.NOT_YET -> "🌱 ${bi("grammar.masteryNotYet")}"
    Mastery.NEW -> null
    Mastery.LEARNING -> "🌱 ${bi("grammar.masteryLearning")}"
    Mastery.SECURE -> "✍️ ${bi("grammar.masterySecure")}"
    Mastery.MASTERED -> "🎤 ${bi("grammar.masteryMastered")}"
}

/**
 * One rule's page (in [GrammarSheet]): who wrote it and its level, the rule, the tutor's addition, the
 * table, the examples (🔊, word lookup), the learner's own examples and mistakes on it, how well it sits, "🎯 Vadi" and
 * "💬 Vprašaj", its overview to print when it has one (skloni: "🖨️ Pregled sklonov"), the related pages met ([onSee]),
 * and whether a machine wrote it.
 */
@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.rulePage(view: RuleView, vm: AppViewModel, onSee: (String) -> Unit) {
    val p = view.page
    item("rule:meta") {
        Text(
            listOfNotNull(
                p.level,
                if (p.byTutor) "🧑‍🏫 ${bi("townScroll.fromTutor")}" else null,
                if (p.extended) "🧑‍🏫 ${bi("grammar.tutorAdded")}" else null,
            ).joinToString("  ·  "),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium,
        )
    }
    item("rule:rule") { Markdown(p.ruleIn(base), color = MaterialTheme.colorScheme.onSurface) }
    p.moreIn(base)?.let { more ->
        item("rule:more") {
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("🧑‍🏫 ${bi("grammar.tutorAdds")}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                Markdown(more, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    p.table?.let { t -> item("rule:table") { RuleTable(t.head, t.rows, vm) } }

    item("rule:examplesTitle") { SectionTitle("✏️ ${bi("grammar.examples")}") }
    for ((i, e) in p.examples.withIndex()) item("rule:ex:$i") {
        val line = e.target(p.language)
        val meaning = e.meaning(base)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                WordText(line, lookUpIn(vm, line, meaning, Words.CHAT), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text((if (e.byTutor) "🧑‍🏫 " else "") + meaning, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                e.noteIn(base)?.let { Text("💡 $it", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            }
            SpeakButton(vm.speaker, line, Modifier.padding(start = 6.dp))
        }
    }

    // what the learner said right on it, and their grammar cards on it
    val said = view.record?.said.orEmpty().reversed()
    item("rule:mineTitle") { SectionTitle("🙋 ${bi("grammar.yourExamples")}") }
    if (said.isEmpty() && view.cards.isEmpty()) item("rule:mineNone") { Quiet(bi("grammar.noExamplesYet")) }
    for ((i, s) in said.withIndex()) item("rule:said:$i") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✓ ", color = green(), fontWeight = FontWeight.Bold)
            WordText(s, lookUpIn(vm, s, null, Words.CHAT), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            SpeakButton(vm.speaker, s, Modifier.padding(start = 6.dp))
        }
    }
    for (c in view.cards.take(6)) item("rule:card:${c.id}") {
        Column {
            Text("🗂️ ${c.front}", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            Text(c.back, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }

    // the mistakes on it: the tutor's record (mistakes-db) and the slips answered here
    val slips = view.record?.slips.orEmpty().reversed().map { it.answer to it.correct }
    val mistakes = (view.mistakes.flatMap { it.examples.takeLast(2) } + slips).distinct().take(6)
    item("rule:mistakesTitle") { SectionTitle("🩹 ${bi("grammar.yourMistakes")}") }
    if (mistakes.isEmpty()) item("rule:mistakesNone") { Quiet(bi("grammar.noMistakes")) }
    for ((i, m) in mistakes.withIndex()) item("rule:mistake:$i") {
        Column {
            Text("✗ ${m.first}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✓ ${m.second}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                SpeakButton(vm.speaker, m.second, Modifier.padding(start = 6.dp))
            }
        }
    }

    item("rule:meter") {
        val r = view.record
        val answers = (r?.right ?: 0) + (r?.wrong ?: 0)
        val meter = view.meter
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 6.dp)) {
            Text(
                "📈 ${bi("grammar.howWell")}: " + if (meter != null) stars(meter) else bi("grammar.notPractisedYet"),
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { contentDescription = "${bi("grammar.howWell")}: ${meter?.let { "$it / 5" } ?: bi("grammar.notPractisedYet")}" },
            )
            if (answers > 0) Text(bi("grammar.answers", "right" to (r?.right ?: 0), "total" to answers), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            // how often the dialogs said it before it was introduced
            view.meetings?.takeIf { it.times > 0 }?.let {
                Text("🌱 ${bi("grammar.metInDialogs", "n" to it.times)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            // how its turns are asked now: choosing, typing the missing word, saying the whole sentence
            masteryText(view.mastery)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }
    }
    item("rule:actions") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            // not in a run: leaving it would lose the answers
            // not over a run with answers (it would cut it short); on its intro or its end it takes the run's place
            if (!vm.runBusy) PageButton("🎯 ${bi("grammar.practise")}", Modifier.weight(1f)) { vm.practiseGrammar(p.id) }
            PageButton("💬 ${bi("grammar.ask")}", Modifier.weight(1f)) { vm.grammar.ask(p.id) }
        }
    }
    // the page's overview on paper (companion/COURSE.md, "The cases overview"): skloni's six cases on three pages
    si.lanisce.lani.data.Prints.overview(p.language, p.id)?.let { job ->
        item("rule:print") { si.lanisce.lani.ui.school.PrintButton(vm, job, bi("school.printOverview")) }
    }
    val related = p.see.mapNotNull { id -> vm.grammar.page(id) }
    if (related.isNotEmpty()) item("rule:see") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionTitle("🔗 ${bi("grammar.seeAlso")}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in related) {
                    val met = r.id in view.met
                    Surface(
                        onClick = { if (met) onSee(r.id) }, enabled = met, shape = RoundedCornerShape(50),
                        color = if (met) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.heightIn(min = 40.dp),
                    ) {
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text("${if (met) r.emoji else "🔒"} ${r.target}", color = if (met) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
    item("rule:review") {
        Text(
            listOfNotNull(if (p.machineWritten) "🤖 ${bi("grammar.machineWritten")}" else null, p.review).joinToString(" · "),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** The green of something said right, lighter on the dark theme. */
@Composable
private fun green(): Color = if (isSystemInDarkTheme()) Color(0xFF7BD6A0) else Color(0xFF2E7D4F)

/** A heading on the page: "✏️ Primeri · Examples". */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 6.dp).semantics { heading() },
    )
}

@Composable
private fun Quiet(text: String) {
    Text(text, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}

/** A button on the page: the app's tonal pill, at least 48 dp high. */
@Composable
private fun PageButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.heightIn(min = 48.dp)) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(text, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The page's table: the head in bold, the rows ruled; words of the learned language can be looked up. */
@Composable
private fun RuleTable(head: List<GrammarCell>, rows: List<List<GrammarCell>>, vm: AppViewModel) {
    val ruled = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth().border(1.dp, ruled, MaterialTheme.shapes.small).clip(MaterialTheme.shapes.small)) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
            for (c in head) Text(
                c.shown(base), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
        for (r in rows) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp).background(ruled).padding(top = 1.dp))
            Row(Modifier.fillMaxWidth()) {
                for (c in r) {
                    val m = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 5.dp)
                    val text = c.shown(base)
                    if (c.isWords) WordText(text, lookUpIn(vm, text, null, Words.CHAT), modifier = m, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                    else Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = m)
                }
            }
        }
    }
}

/**
 * A grammar page over whatever is on screen (opened from the scroll, a "why", a challenge's intro, a word's card): a sheet
 * like the app's others (the cheat sheet's, the chat's): its emoji, its title in the learned language and in the base,
 * its level, ✕; then the page ([rulePage]); related pages open in it.
 */
@Composable
fun GrammarSheet(vm: AppViewModel, id: String) {
    val view = vm.grammar.view(id) ?: return
    val p = view.page
    LaniSheet(onDismissRequest = vm.grammar::close) {
        // a page opened from another's "Glej tudi": Back (and "‹ <its title>") to that one first, then the sheet closes
        val prev = vm.grammar.before.lastOrNull()?.let(vm.grammar::page)
        androidx.activity.compose.BackHandler(enabled = prev != null) { vm.grammar.back() }
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.95f).padding(horizontal = 16.dp)) {
            prev?.let { pp ->
                androidx.compose.material3.TextButton(onClick = { vm.grammar.back() }) { Text("‹ ${pp.emoji} ${pp.target}", maxLines = 1) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.emoji, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clearAndSetSemantics { })
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                    Text(p.target, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    p.titleIn(base).takeIf { it != p.target }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(
                    onClick = vm.grammar::close, shape = CircleShape, color = Color.Transparent,
                    modifier = Modifier.size(48.dp).clearAndSetSemantics { role = Role.Button; contentDescription = Labels.CLOSE; onClick { vm.grammar.close(); true } },
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("✕", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rulePage(view, vm) { vm.grammar.show(it) }
            }
        }
    }
}

/**
 * Pages of the grammar book beside a "why" or a run's intro ([ids], the pages there are): "📖 V knjigo · To the book"
 * for each, and for those just met ([fresh]) "📖 Nova stran v knjigi · New page in the book" above it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookLinks(vm: AppViewModel, ids: List<String>, fresh: List<String> = emptyList(), modifier: Modifier = Modifier) {
    val pages: List<GrammarPage> = ids.distinct().mapNotNull { vm.grammar.page(it) }
    if (pages.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (p in pages.filter { it.id in fresh }) {
            Text(
                "📖 ${bi("grammar.newPage")}: ${p.target}", fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (p in pages) AssistChip(
                onClick = { vm.grammar.show(p.id) },
                label = { Text("📖 ${bi("grammar.toBook")}: ${p.target}", style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

/** The run's pages, on its intro: "📖 V knjigi · In the book" and a link to each. */
@Composable
fun IntroBookLinks(vm: AppViewModel, ids: List<String>) {
    val shown = ids.distinct().filter { vm.grammar.page(it) != null }
    if (shown.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("📖 ${bi("grammar.inBook")}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
        BookLinks(vm, shown)
    }
}
