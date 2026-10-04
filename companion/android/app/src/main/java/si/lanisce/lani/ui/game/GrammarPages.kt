package si.lanisce.lani.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import si.lanisce.lani.inRun
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.Markdown
import si.lanisce.lani.ui.SpeakButton
import si.lanisce.lani.ui.words.WordText
import si.lanisce.lani.ui.words.lookUpIn

/**
 * The grammar chapter as the village's scroll and book show it (companion/GAME.md, "The grammar book"): [chapter] (the
 * pages met, how many are still to come), [view] of one page, and [open]: what a tap on a page does outside the book (the
 * scroll shows it over itself); the book turns to it instead.
 */
class GrammarShelf(
    val vm: AppViewModel,
    val chapter: GrammarBook.Chapter,
    val view: (String) -> RuleView?,
    val open: ((String) -> Unit)? = null,
)

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
 * The chapter's list: the pages met (tap: the page), greyed the rules the dialogs said before they were introduced ("🌱 …
 * Srečano v pogovorih: 5× · Met in the dialogs: 5×", closed until they are), and how many more there are; an empty book
 * says how it fills.
 */
internal fun grammarChapter(k: Counted, shelf: GrammarShelf?, pal: Paper, onRule: (String) -> Unit) {
    val chapter = shelf?.chapter ?: GrammarBook.Chapter(emptyList(), 0)
    k.item("grammar:about") {
        Text(
            if (chapter.met.isEmpty()) bi("grammar.empty") else bi("grammar.chapterAbout"),
            fontFamily = Serif, fontStyle = FontStyle.Italic, color = pal.muted, style = MaterialTheme.typography.bodyMedium,
        )
    }
    // the test for the next level: "🗺️ Sem pripravljen · I'm ready", before the storyteller offers the map too
    shelf?.vm?.let { vm ->
        val to = si.lanisce.lani.game.LevelReadiness.next(vm.treasure.level)
        if (to != null) k.item("grammar:treasure") {
            val on = vm.treasure.status() == si.lanisce.lani.game.HuntStatus.ON
            PaperRow(
                emoji = "🗺️", title = if (on) bi("treasure.title") else bi("treasure.imReady"), sub = bi("treasure.bookRow", "to" to to),
                pal = pal, onClick = { vm.openTreasure() }, onShow = null,
            )
        }
    }
    for (p in chapter.met) k.item("grammar:${p.id}") {
        val v = shelf?.view?.invoke(p.id)
        PaperRow(
            emoji = p.emoji, badge = if (p.byTutor || p.extended) "🧑‍🏫" else null,
            title = p.target,
            sub = listOfNotNull(p.titleIn(base).takeIf { it != p.target }, p.level).joinToString(" · "),
            extra = v?.meter?.let { "${stars(it)}  ${bi("grammar.howWell")}" },
            pal = pal, onClick = { onRule(p.id) }, onShow = null,
        )
    }
    for ((p, m) in chapter.seen) k.item("grammar:seen:${p.id}") {
        PaperRow(
            emoji = "🌱", title = p.target,
            sub = listOfNotNull(p.titleIn(base).takeIf { it != p.target }, p.level).joinToString(" · "),
            extra = bi("grammar.metInDialogs", "n" to m.times),
            pal = pal, dim = true, onClick = null, onShow = null,
        )
    }
    if (chapter.more > 0) k.item("grammar:more") {
        PaperRow(
            emoji = "🔒", title = bi("grammar.more", "n" to chapter.more), sub = bi("grammar.unlockWhenMet"),
            pal = pal, dim = true, onClick = null, onShow = null,
        )
    }
}

/** A rule's page title: its emoji, the title in the learned language and in the base, and "‹" back to the chapter. */
@Composable
internal fun RuleTitle(view: RuleView, pal: Paper, onBack: (() -> Unit)?) {
    val p = view.page
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Surface(
                    onClick = onBack, shape = CircleShape, color = Color.Transparent,
                    modifier = Modifier.size(40.dp).clearAndSetSemantics { role = Role.Button; contentDescription = bi("townScroll.grammar"); onClick { onBack(); true } },
                ) { Box(contentAlignment = Alignment.Center) { Text("‹", color = pal.muted, style = MaterialTheme.typography.headlineSmall) } }
            }
            Text(p.emoji, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(p.target, fontFamily = Serif, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = pal.ink)
                p.titleIn(base).takeIf { it != p.target }?.let {
                    Text(it, fontFamily = Serif, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall, color = pal.muted)
                }
            }
        }
        Flourish(pal)
    }
}

/**
 * One rule's page (in the book, and in [GrammarSheet]): who wrote it and its level, the rule, the tutor's addition, the
 * table, the examples (🔊, word lookup), the learner's own examples and mistakes on it, how well it sits, "🎯 Vadi" and
 * "💬 Vprašaj", the related pages met ([onSee]), and whether a machine wrote it.
 */
@OptIn(ExperimentalLayoutApi::class)
internal fun rulePage(k: Counted, view: RuleView, pal: Paper, shelf: GrammarShelf, onSee: (String) -> Unit) {
    val p = view.page
    val vm = shelf.vm
    k.item("rule:meta") {
        Text(
            listOfNotNull(
                p.level,
                if (p.byTutor) "🧑‍🏫 ${bi("townScroll.fromTutor")}" else null,
                if (p.extended) "🧑‍🏫 ${bi("grammar.tutorAdded")}" else null,
            ).joinToString("  ·  "),
            color = pal.muted, style = MaterialTheme.typography.labelMedium,
        )
    }
    k.item("rule:rule") { Markdown(p.ruleIn(base), color = pal.ink) }
    p.moreIn(base)?.let { more ->
        k.item("rule:more") {
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(pal.row).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("🧑‍🏫 ${bi("grammar.tutorAdds")}", color = pal.accent, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                Markdown(more, color = pal.ink)
            }
        }
    }
    p.table?.let { t -> k.item("rule:table") { RuleTable(t.head, t.rows, pal, vm) } }

    k.item("rule:examplesTitle") { SubTitle("✏️ ${bi("grammar.examples")}", pal) }
    for ((i, e) in p.examples.withIndex()) k.item("rule:ex:$i") {
        val line = e.target(p.language)
        val meaning = e.meaning(base)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                WordText(line, lookUpIn(vm, line, meaning, Words.CHAT), color = pal.ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text((if (e.byTutor) "🧑‍🏫 " else "") + meaning, fontStyle = FontStyle.Italic, color = pal.muted, style = MaterialTheme.typography.bodyMedium)
                e.noteIn(base)?.let { Text("💡 $it", color = pal.muted, style = MaterialTheme.typography.bodySmall) }
            }
            SpeakButton(vm.speaker, line, Modifier.padding(start = 6.dp))
        }
    }

    // what the learner said right on it, and their grammar cards on it
    val said = view.record?.said.orEmpty().reversed()
    k.item("rule:mineTitle") { SubTitle("🙋 ${bi("grammar.yourExamples")}", pal) }
    if (said.isEmpty() && view.cards.isEmpty()) k.item("rule:mineNone") { Quiet(bi("grammar.noExamplesYet"), pal) }
    for ((i, s) in said.withIndex()) k.item("rule:said:$i") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✓ ", color = GREEN, fontWeight = FontWeight.Bold)
            WordText(s, lookUpIn(vm, s, null, Words.CHAT), modifier = Modifier.weight(1f), color = pal.ink, style = MaterialTheme.typography.bodyLarge)
            SpeakButton(vm.speaker, s, Modifier.padding(start = 6.dp))
        }
    }
    for (c in view.cards.take(6)) k.item("rule:card:${c.id}") {
        Column {
            Text("🗂️ ${c.front}", color = pal.ink, style = MaterialTheme.typography.bodyLarge)
            Text(c.back, fontStyle = FontStyle.Italic, color = pal.muted, style = MaterialTheme.typography.bodySmall)
        }
    }

    // the mistakes on it: the tutor's record (mistakes-db) and the slips answered here
    val slips = view.record?.slips.orEmpty().reversed().map { it.answer to it.correct }
    val mistakes = (view.mistakes.flatMap { it.examples.takeLast(2) } + slips).distinct().take(6)
    k.item("rule:mistakesTitle") { SubTitle("🩹 ${bi("grammar.yourMistakes")}", pal) }
    if (mistakes.isEmpty()) k.item("rule:mistakesNone") { Quiet(bi("grammar.noMistakes"), pal) }
    for ((i, m) in mistakes.withIndex()) k.item("rule:mistake:$i") {
        Column {
            Text("✗ ${m.first}", color = pal.accent, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✓ ${m.second}", color = pal.ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                SpeakButton(vm.speaker, m.second, Modifier.padding(start = 6.dp))
            }
        }
    }

    k.item("rule:meter") {
        val r = view.record
        val answers = (r?.right ?: 0) + (r?.wrong ?: 0)
        val meter = view.meter
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 6.dp)) {
            Text(
                "📈 ${bi("grammar.howWell")}: " + if (meter != null) stars(meter) else bi("grammar.notPractisedYet"),
                fontFamily = Serif, fontWeight = FontWeight.Bold, color = pal.ink, style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { contentDescription = "${bi("grammar.howWell")}: ${meter?.let { "$it / 5" } ?: bi("grammar.notPractisedYet")}" },
            )
            if (answers > 0) Text(bi("grammar.answers", "right" to (r?.right ?: 0), "total" to answers), color = pal.muted, style = MaterialTheme.typography.bodySmall)
            // how often the dialogs said it before it was introduced
            view.meetings?.takeIf { it.times > 0 }?.let {
                Text("🌱 ${bi("grammar.metInDialogs", "n" to it.times)}", color = pal.muted, style = MaterialTheme.typography.bodySmall)
            }
            // how its turns are asked now: choosing, typing the missing word, saying the whole sentence
            masteryText(view.mastery)?.let { Text(it, color = pal.muted, style = MaterialTheme.typography.bodySmall) }
        }
    }
    k.item("rule:actions") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            // not in a run: leaving it would lose the answers
            if (!vm.screen.inRun) PaperButton("🎯 ${bi("grammar.practise")}", pal, Modifier.weight(1f)) { vm.practiseGrammar(p.id) }
            PaperButton("💬 ${bi("grammar.ask")}", pal, Modifier.weight(1f)) { vm.grammar.ask(p.id) }
        }
    }
    val related = p.see.mapNotNull { id -> vm.grammar.page(id) }
    if (related.isNotEmpty()) k.item("rule:see") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SubTitle("🔗 ${bi("grammar.seeAlso")}", pal)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in related) {
                    val met = r.id in view.met
                    Surface(
                        onClick = { if (met) onSee(r.id) }, enabled = met, shape = RoundedCornerShape(50),
                        color = if (met) pal.accent.copy(alpha = 0.14f) else pal.row, modifier = Modifier.heightIn(min = 40.dp),
                    ) {
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text("${if (met) r.emoji else "🔒"} ${r.target}", color = if (met) pal.ink else pal.muted, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
    k.item("rule:review") {
        Text(
            listOfNotNull(if (p.machineWritten) "🤖 ${bi("grammar.machineWritten")}" else null, p.review).joinToString(" · "),
            color = pal.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private val GREEN = Color(0xFF2E7D4F)

@Composable
private fun Quiet(text: String, pal: Paper) {
    Text(text, fontFamily = Serif, fontStyle = FontStyle.Italic, color = pal.muted, style = MaterialTheme.typography.bodyMedium)
}

/** A button on the paper: ink on a light wash, at least 48 dp high. */
@Composable
private fun PaperButton(text: String, pal: Paper, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = pal.accent.copy(alpha = 0.16f), modifier = modifier.heightIn(min = 48.dp)) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(text, color = pal.ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The page's table: the head in bold, the rows ruled; words of the learned language can be looked up. */
@Composable
private fun RuleTable(head: List<GrammarCell>, rows: List<List<GrammarCell>>, pal: Paper, vm: AppViewModel) {
    Column(Modifier.fillMaxWidth().border(1.dp, pal.rule, MaterialTheme.shapes.small).clip(MaterialTheme.shapes.small)) {
        Row(Modifier.fillMaxWidth().background(pal.row)) {
            for (c in head) Text(
                c.shown(base), fontWeight = FontWeight.Bold, color = pal.ink, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
        for (r in rows) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp).background(pal.rule).padding(top = 1.dp))
            Row(Modifier.fillMaxWidth()) {
                for (c in r) {
                    val m = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 5.dp)
                    val text = c.shown(base)
                    if (c.isWords) WordText(text, lookUpIn(vm, text, null, Words.CHAT), modifier = m, color = pal.ink, style = MaterialTheme.typography.bodyMedium)
                    else Text(text, color = pal.muted, style = MaterialTheme.typography.bodySmall, modifier = m)
                }
            }
        }
    }
}

/**
 * A grammar page over whatever is on screen (opened from a "why", a challenge's intro, the scroll): the page on paper in
 * a sheet; related pages open in it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarSheet(vm: AppViewModel, id: String) {
    val view = vm.grammar.view(id) ?: return
    val pal = paper()
    val shelf = GrammarShelf(vm, vm.grammar.chapter(vm.game.state), vm.grammar::view, open = vm.grammar::show)
    ModalBottomSheet(onDismissRequest = vm.grammar::close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = pal.paper) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).parchment(pal)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { RuleTitle(view, pal, onBack = null) }
                CloseSeal(pal, vm.grammar::close)
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 18.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rulePage(Counted(this), view, pal, shelf) { vm.grammar.show(it) }
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
