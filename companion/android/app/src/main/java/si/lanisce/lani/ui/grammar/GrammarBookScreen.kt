package si.lanisce.lani.ui.grammar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.data.GrammarPage
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.GrammarBook
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.ui.GradientHeader
import si.lanisce.lani.ui.StatusBarScrim
import si.lanisce.lani.ui.backLabel
import si.lanisce.lani.ui.theme.TriglavRed
import java.time.LocalDate

/** A page of the grammar book in its index: its title in each language, its level, how well it sits ([stars]), [new] this week. */
data class BookRow(val id: String, val emoji: String, val target: String, val base: String?, val level: String?, val stars: Int?, val new: Boolean)

/** The grammar book's index (docs/plans/05-built/navigation.md, "The grammar book"), as its screen and Home's tile read it. */
data class BookIndex(
    /** The pages met, by level, the newest of each level first. */
    val rows: List<BookRow>,
    /** Rules met in the dialogs only (🌱), and the pages still to meet (🔒). */
    val seen: Int,
    val more: Int,
) {
    val new: Int get() = rows.count { it.new }

    companion object {
        /** A page met this many days ago or less is new in the index. */
        const val WEEK = 7L

        /**
         * The index of [chapter] ([GrammarBook.chapter]: the pages met, those seen in the dialogs, the rest) of [s]'s village in
         * [language], each page as well as it sits ([meter]), the titles in the learner's [base].
         */
        fun of(chapter: GrammarBook.Chapter, s: GameState?, language: String, meter: (String) -> Int?, base: String, today: LocalDate = LocalDate.now()): BookIndex {
            val met = s?.let { GrammarBook.met(it, language) }.orEmpty()
            fun day(p: GrammarPage) = met[p.id]?.on?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
            val rows = chapter.met.map { p ->
                val on = day(p)
                BookRow(p.id, p.emoji, p.target, p.titleIn(base).takeIf { it != p.target }, p.level, meter(p.id), new = on != null && !on.isBefore(today.minusDays(WEEK)))
            }
            val levels = si.lanisce.lani.data.Stats.LEVELS
            return BookIndex(
                // the CEFR levels in order, any other level after them by its name (each level one group), the week's first
                rows.sortedWith(compareBy<BookRow>({ levels.indexOf(it.level).let { i -> if (i < 0) levels.size else i } }, { it.level.orEmpty() }, { !it.new })),
                chapter.seen.size, chapter.more,
            )
        }
    }
}

/** [vm]'s grammar book now, as [BookIndex.of] reads it. */
fun bookIndex(vm: AppViewModel): BookIndex =
    BookIndex.of(vm.grammar.chapter(vm.game.state), vm.game.state, vm.grammar.language, { vm.grammar.view(it)?.meter }, L10n.pair.base.code)

/**
 * "📖 Slovnica · Grammar", the grammar book's index (from Home's tile; the village's Scroll has the same list): the pages met,
 * by level, each with how well it sits (★) and 🆕 for one met this week; a tap opens the page over the index (Back closes
 * it). Then how many rules were met in the dialogs only and how many pages are still to come, and the cheat sheet.
 */
@Composable
fun GrammarBookScreen(vm: AppViewModel) {
    val index = remember(vm.game.state, vm.grammar.pages, vm.langPair) { bookIndex(vm) }
    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 32.dp)) {
            item("header") {
                GradientHeader(
                    "📖 ${bi("townScroll.grammar")}",
                    if (index.rows.isEmpty()) bi("grammar.empty") else bi("nav.grammarPages", "n" to index.rows.size),
                    vm.backLabel, vm::back,
                )
            }
            var level: String? = null
            for (r in index.rows) {
                if (r.level != level) {
                    level = r.level
                    item("level-${r.level}") {
                        Text(
                            r.level ?: "…", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp).semantics { heading() },
                        )
                    }
                }
                item(r.id) { PageRow(r, Modifier.fillMaxWidth().wrapContentWidth().padding(horizontal = 16.dp, vertical = 4.dp)) { vm.grammar.show(r.id) } }
            }
            item("rest") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp).widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (index.seen > 0) Text("🌱 ${bi("scrollSheet.seenInDialogs", "n" to index.seen)}", style = MaterialTheme.typography.bodyMedium)
                    if (index.more > 0) Text("🔒 ${bi("grammar.more", "n" to index.more)}", style = MaterialTheme.typography.bodyMedium)
                    if (index.seen > 0 || index.more > 0) Text(bi("grammar.unlockWhenMet"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { vm.cheat.show() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("📋 ${bi("cheatSheet.title")} ›") }
                }
            }
        }
        StatusBarScrim(list, Modifier.align(Alignment.TopCenter))
    }
}

/** One page's row: its emoji, its title in the target language and the base, its level, ★ how well it sits, 🆕. */
@Composable
private fun PageRow(r: BookRow, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = modifier.fillMaxWidth().widthIn(max = 720.dp).heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(r.emoji, style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.target, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                r.base?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                r.stars?.let { Text("${si.lanisce.lani.ui.game.stars(it)}  ${bi("grammar.howWell")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (r.new) Text("🆕", modifier = Modifier.padding(start = 8.dp).semantics { }, color = TriglavRed)
        }
    }
}
