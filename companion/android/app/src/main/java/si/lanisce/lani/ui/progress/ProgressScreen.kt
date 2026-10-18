package si.lanisce.lani.ui.progress

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import si.lanisce.lani.AppViewModel
import si.lanisce.lani.app.ChatContext
import si.lanisce.lani.data.DueDay
import si.lanisce.lani.data.GrammarPage
import si.lanisce.lani.data.LanguageSummary
import si.lanisce.lani.data.MistakeStat
import si.lanisce.lani.data.ProgressStats
import si.lanisce.lani.data.Projection
import si.lanisce.lani.data.SkillMastery
import si.lanisce.lani.data.Trend
import si.lanisce.lani.game.LevelReadiness
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.ui.GradientHeader
import si.lanisce.lani.ui.backLabel
import si.lanisce.lani.ui.StatusBarScrim
import si.lanisce.lani.ui.theme.AlpineGreen
import si.lanisce.lani.ui.theme.FlameOrange
import si.lanisce.lani.ui.theme.SloBlue
import si.lanisce.lani.ui.theme.SloBlueDeep
import si.lanisce.lani.ui.theme.TriglavRed
import si.lanisce.lani.ui.theme.XpGold
import si.lanisce.lani.l10n.Dates
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.l10n.inBase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

private val SL = Locale.forLanguageTag("sl")
private val longEn = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** "23. 9." — short Slovene day label. */
private fun LocalDate.short() = "$dayOfMonth. $monthValue."

/**
 * "Napredek · Progress": words, the way to the next level (the treasure map's signs), the grammar (the school, the book,
 * counting), streak calendar, accuracy, skills, mistakes and upcoming reviews.
 */
@Composable
fun ProgressScreen(vm: AppViewModel, fromVillage: Boolean) {
    BackHandler { vm.leaveProgress(fromVillage) }
    val p = vm.progress.stats
    val list = rememberLazyListState()
    val t = vm.treasure
    val s = vm.game.state
    val d = vm.content.dashboard
    val readiness = remember(s, d, vm.grammar.pages, vm.scenes.all) { t.readiness() }
    // the lines' texts are the pair's: made again when it changes
    val pair = vm.langPair
    val path = remember(readiness, p?.wordsKnown, s, vm.villagers.all, pair) {
        readiness?.let { r -> ProgressLogic.path(r, p?.wordsKnown ?: d?.wordsLearned ?: 0, t.status(), t.hunt(), t.required(), t.tellerName(), t.waiting) }
    }
    val grammar = remember(readiness, s, d, vm.school.course, vm.grammar.pages) {
        ProgressLogic.grammar(
            level = readiness?.from ?: LevelReadiness.norm(vm.grammar.level), language = vm.grammar.language, course = vm.school.course, s = s,
            pages = vm.grammar.pages, mastery = vm.grammar::mastery, meter = { vm.grammar.view(it)?.meter },
            counting = d?.counting?.takeIf { vm.counting.on(d) }, today = LocalDate.now(),
        )
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), state = list, contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Hero(p, fromVillage) { vm.leaveProgress(fromVillage) } }
            if (p == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                return@LazyColumn
            }
            path?.let { item("path") { PathCard(it, page = vm.grammar::page, onPage = vm.grammar::show, onMap = { vm.openTreasure(fromVillage) }) } }
            if (!grammar.empty) item("grammar") {
                GrammarCard(grammar, onSchool = { vm.openSchool(fromVillage) }, onUnit = { vm.openUnit(it, fromVillage) }, onPage = vm.grammar::show)
            }
            item { StreakCard(p) }
            item { AccuracyCard(p) }
            item { WeeklyCard(p) }
            item { SkillsCard(p.skills) }
            item { MistakesCard(p.mistakes, p.today) { m -> vm.chat.askAbout(mistakeContext(m)) } }
            item { DueCard(p.due, p.overdue) }
            // Everything above is the home language's; the languages practised on visits have their own line each.
            if (p.otherLanguages.isNotEmpty()) item { LanguagesCard(p.otherLanguages, due = { vm.content.decks[it]?.dueCards?.size ?: 0 }, onReview = vm::startReview) }
        }
        StatusBarScrim(list, Modifier.align(Alignment.TopCenter))
    }
}

private fun mistakeContext(m: MistakeStat) = ChatContext(
    label = "⚠️ ${m.label}",
    data = buildJsonObject {
        put("error_pattern", m.id)
        put("frequency", m.frequency)
        m.lastSeen?.let { put("last_seen", it.toString()) }
        m.wrong?.let { put("example_incorrect", it) }
        m.right?.let { put("example_correct", it) }
        put("question", "Explain this recurring mistake and give me a quick drill.")
    },
)

@Composable
private fun chartColors(): ChartColors {
    val cs = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    return ChartColors(
        data = cs.primary,
        surface = cs.surface,
        grid = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
        empty = cs.surfaceVariant,
        text = cs.onSurfaceVariant,
    )
}

@Composable
private fun Hero(p: ProgressStats?, fromVillage: Boolean, onBack: () -> Unit) {
    GradientHeader(backLabel(fromVillage), onBack) {
        Text("📊 ${bi("common.progress")}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        if (p == null) return@GradientHeader
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(
                Modifier.weight(1f).clearAndSetSemantics {
                    contentDescription = bi("progressScreen.wordsLearnedTrend", "wordsKnown" to p.wordsKnown, "first" to (p.wordsTrend.first()))
                },
            ) {
                Text("${p.wordsKnown}", style = MaterialTheme.typography.displayMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text(bi("progressScreen.wordsLearned"), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                if (p.wordsTracked > p.wordsKnown) Text(
                    "+${p.wordsTracked - p.wordsKnown} ${bi("progressScreen.stillBeingLearned")}",
                    color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall,
                )
            }
            Sparkline(p.wordsTrend, XpGold, SloBlue, Modifier.width(120.dp).height(56.dp))
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LevelBadge(p.level, filled = true)
            Text("→", color = Color.White)
            LevelBadge(p.targetLevel, filled = false)
            Spacer(Modifier.width(4.dp))
            Text("🔥 ${p.streaks.current}  ·  ⏱ ${p.totalMinutes} min", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(14.dp))
        // the words goal: the level's own, then on to the next one once passed (the level rises with the treasure map)
        val w = p.words
        LinearProgressIndicator(
            progress = { (p.wordsKnown.toFloat() / w.goal).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).semantics {
                contentDescription = inBase("progressScreen.wordsBar", "wordsKnown" to p.wordsKnown, "goal" to w.goal, "level" to w.forLevel)
            },
            color = XpGold,
            trackColor = Color.White.copy(alpha = 0.2f),
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (w.reached == null) bi("progressScreen.wordsNextlevel", "wordsKnown" to p.wordsKnown, "nextGoal" to w.goal, "nextLevel" to w.forLevel)
            else bi("progressScreen.wordsGoalFor", "wordsKnown" to p.wordsKnown, "goal" to w.goal, "level" to w.forLevel),
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodySmall,
        )
        w.reached?.takeIf { !w.complete(p.wordsKnown) }?.let { r ->
            Spacer(Modifier.height(4.dp))
            Surface(color = Color.White.copy(alpha = 0.16f), shape = RoundedCornerShape(50)) {
                Text(
                    "✅ ${bi("progressScreen.wordsGoalReached", "goal" to r.words, "level" to r.level)}",
                    color = Color.White, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(projectionText(p), color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * When the words bar's goal ([ProgressStats.words]) is reached: at the recent pace, and with a word pack a day. Before the
 * level's own goal it names the level ("A2 okoli …"); past it, the words ("1000 besed okoli …"): the level doesn't come
 * with them.
 */
private fun projectionText(p: ProgressStats): String {
    val w = p.words
    val lvl = w.forLevel
    val moved = w.reached != null
    // What a daily word-pack session (6 new words) would do: a goal to act on, not only a forecast.
    val daily = LocalDate.now().plusDays(((w.goal - p.wordsKnown).coerceAtLeast(0) + 5L) / 6)
    val plan = "💪 " + if (moved) bi("progressScreen.goalOnePackADay", "goal" to w.goal, "date" to Dates.long(daily), "enDate" to daily.format(longEn))
    else bi("progressScreen.onePackADay", "lvl" to lvl, "date" to Dates.long(daily), "enDate" to daily.format(longEn))
    return when (val pr = p.projection) {
        Projection.Reached -> "✅ ${bi("progressScreen.wordGoalLvlReached", "lvl" to lvl)}"
        Projection.NotEnoughData -> plan
        is Projection.TooFar -> "🐢 ${bi("progressScreen.soFarPerWeek", "perWeek" to fmt(pr.perWeek))}\n$plan"
        is Projection.Eta ->
            "🎯 " + (
                if (moved) bi("progressScreen.goalAround", "goal" to w.goal, "date" to Dates.long(pr.date), "enDate" to pr.date.format(longEn))
                else bi("progressScreen.lvlAround", "lvl" to lvl, "date" to Dates.long(pr.date), "enDate" to pr.date.format(longEn))
                ) + " (${fmt(pr.perWeek)} ${bi("progressScreen.wordsPerWeek")})"
    }
}

private fun fmt(x: Double) = if (x >= 10) "${x.roundToInt()}" else "%.1f".format(Locale.ROOT, x)

@Composable
private fun LevelBadge(level: String, filled: Boolean) {
    Surface(
        color = if (filled) Color.White else Color.White.copy(alpha = 0.16f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            level,
            color = if (filled) SloBlueDeep else Color.White,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Section(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

// --- the way to the next level (the treasure map's signs) ------------------------------------------------------------

/**
 * "🧭 Do A2 · To A2": how the level rises (the treasure map, the storyteller's), the map waiting or the hunt on (a tap:
 * the map), the words and the three signs each with ✅ or ⏳ and its numbers, the pages still to secure (a tap: the page).
 * [page]: a page of the book by id.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PathCard(path: LevelPath, page: (String) -> GrammarPage?, onPage: (String) -> Unit, onMap: () -> Unit) {
    val about = (path.map as? MapState.Coming)?.let { bi("progressScreen.pathAbout", "teller" to it.teller) }
    Section("🧭 ${bi("progressScreen.pathTitle", "to" to path.to)}", about) {
        path.found?.let { f ->
            Text("🪙 ${bi("treasure.found")} ${bi("treasure.nowAt", "to" to f.level)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (f.tutorWaits) Caption("⏳ ${bi("treasure.tutorWaits")}")
        }
        when (val m = path.map) {
            is MapState.Waiting -> MapRow(bi("progressScreen.mapWaiting"), bi("treasure.homeOffered", "teller" to m.teller), onMap)
            is MapState.On -> MapRow(bi("treasure.homeOn", "done" to m.done, "n" to m.n), null, onMap)
            is MapState.Coming -> Unit
        }
        for (line in path.lines) {
            PathRow(line)
            if (line.sign == PathSign.GRAMMAR) {
                val pages = path.toSecure.mapNotNull(page)
                if (pages.isNotEmpty()) {
                    Caption(bi("treasure.toSecure"), Modifier.padding(start = SIGN_WIDTH))
                    FlowRow(
                        Modifier.padding(start = SIGN_WIDTH),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // short (the title up to its colon), so several fit a row; TalkBack reads the whole title
                        for (pg in pages) Surface(
                            onClick = { onPage(pg.id) }, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = pg.titleShown(L10n.pair) },
                        ) {
                            Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
                                Text("${pg.emoji} ${ProgressLogic.chipTitle(pg)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (path.toSecureMore > 0) Box(Modifier.heightIn(min = 40.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                            Caption("+${path.toSecureMore}")
                        }
                    }
                }
            }
        }
        // before the offer: the map's own page (its signs, and "🗺️ Sem pripravljen · I'm ready" to try now)
        if (path.map is MapState.Coming) TextButton(onClick = onMap, modifier = Modifier.heightIn(min = 48.dp)) { Text("🗺️ ${bi("treasure.title")} ›") }
    }
}

/** The ✅ or ⏳ column of a sign. */
private val SIGN_WIDTH = 30.dp

/** One line of the way: ✅ or ⏳, the sign and its numbers, what they're measured against; read as one by TalkBack. */
@Composable
private fun PathRow(line: PathLine) {
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = line.description }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (line.met) "✅" else "⏳", modifier = Modifier.width(SIGN_WIDTH), style = MaterialTheme.typography.bodyMedium)
            Text("${line.sign.emoji} ${line.title}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(
                line.value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = if (line.met) AlpineGreen else MaterialTheme.colorScheme.onSurface, maxLines = 1,
            )
        }
        line.caption?.let { Caption(it, Modifier.padding(start = SIGN_WIDTH)) }
    }
}

/** The treasure map waiting or the hunt on: one tap to the map. */
@Composable
private fun MapRow(title: String, sub: String?, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = MaterialTheme.shapes.medium, color = XpGold.copy(alpha = 0.22f),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🗺️", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                sub?.let { Caption(it) }
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

// --- the grammar: the school, the book, counting -----------------------------------------------------------------------

/**
 * "📝 Slovnica · Grammar": the school's units of the learner's level (done of all, a bar; those begun; the one suggested
 * next, a tap: the unit; the row itself: the school), the book's pages of the level (met and secure, a bar; the weakest,
 * a tap: the page), and the counting cards while counting is on.
 */
@Composable
internal fun GrammarCard(g: GrammarStanding, onSchool: () -> Unit, onUnit: (String) -> Unit, onPage: (String) -> Unit) {
    Section("📝 ${bi("townScroll.grammar")}") {
        g.school?.let { SchoolPart(it, onSchool, onUnit) }
        g.book?.let { BookPart(it, onPage) }
        g.counting?.let { c ->
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔢 ${bi("counting.title")}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${c.cards}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                Caption(bi("progressScreen.countingOf", "cards" to c.cards, "secure" to c.secure, "due" to c.due))
            }
        }
    }
}

@Composable
private fun SchoolPart(sc: SchoolStanding, onSchool: () -> Unit, onUnit: (String) -> Unit) {
    val pair = L10n.pair
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(onClick = onSchool, color = Color.Transparent, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏫 ${bi("progressScreen.schoolUnits", "level" to sc.level)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${sc.done} / ${sc.of}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("  ›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(
                    progress = { if (sc.of == 0) 0f else sc.done.toFloat() / sc.of },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).semantics {
                        contentDescription = inBase("progressScreen.schoolUnitsDesc", "level" to sc.level, "done" to sc.done, "n" to sc.of)
                    },
                    color = AlpineGreen, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeCap = StrokeCap.Round,
                )
            }
        }
        if (!sc.open) Caption("🔒 ${si.lanisce.lani.ui.school.beginsIn()}")
        val next = sc.next
        // the unit suggested next, begun already: its row says so, not the line of those begun too
        val nextBegun = next != null && sc.started.any { it.id == next.id }
        val begun = sc.started.filter { it.id != next?.id }
        if (begun.isNotEmpty()) Caption("▶ ${bi("progressScreen.unitsInProgress")}: ${begun.joinToString(", ") { it.titleShown(pair) }}")
        when {
            sc.open && next != null -> Surface(
                onClick = { onUnit(next.id) }, shape = MaterialTheme.shapes.small, color = XpGold.copy(alpha = 0.16f),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (nextBegun) "▶" else "★", style = MaterialTheme.typography.titleMedium, color = XpGold)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (nextBegun) bi("progressScreen.unitsInProgress") else bi("school.next"),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(next.titleShown(pair), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge)
                }
            }
            sc.open && sc.done >= sc.of -> Caption("✅ ${bi("school.allDone")}")
        }
    }
}

@Composable
private fun BookPart(b: BookStanding, onPage: (String) -> Unit) {
    val base = L10n.pair.base.code
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(
            Modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = inBase("progressScreen.bookPagesDesc", "level" to b.level, "pages" to b.pages, "met" to b.met, "secure" to b.secure)
            },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📖 ${bi("progressScreen.bookPages", "level" to b.level)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${b.met} / ${b.pages}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            // secure, met and not secure yet, still to open: one bar
            Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                val learning = b.met - b.secure
                if (b.secure > 0) Box(Modifier.weight(b.secure.toFloat()).fillMaxHeight().background(AlpineGreen))
                if (learning > 0) Box(Modifier.weight(learning.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)))
                if (b.pages > b.met) Spacer(Modifier.weight((b.pages - b.met).toFloat()))
            }
            Caption(bi("progressScreen.bookPagesOf", "met" to b.met, "secure" to b.secure))
        }
        if (b.weakest.isNotEmpty()) {
            Caption(bi("progressScreen.weakest"))
            for (w in b.weakest) Surface(
                onClick = { onPage(w.page.id) }, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                    contentDescription = "${w.page.titleIn(base)}: " + (w.stars?.let { inBase("progressScreen.pageStars", "stars" to it) } ?: inBase("grammar.notPractisedYet"))
                },
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${w.page.emoji}  ${w.page.target}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        w.stars?.let { si.lanisce.lani.ui.game.stars(it) } ?: bi("grammar.notPractisedYet"),
                        style = MaterialTheme.typography.labelMedium, color = if (w.stars != null) XpGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StreakCard(p: ProgressStats) {
    val colors = chartColors()
    var selected by remember(p) { mutableStateOf(p.calendar.lastOrNull()?.lastOrNull { it != null }) }
    val days = p.calendar.flatten().filterNotNull()
    val active = days.count { it.sessions > 0 }
    val s = p.streaks
    Section("🔥 ${bi("homeHero.streak")}", bi("progressScreen.last12WeeksMinutes")) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("${s.current}", bi("progressScreen.current"), FlameOrange, Modifier.weight(1f))
            Stat("${s.best}", bi("progressScreen.best"), XpGold, Modifier.weight(1f))
            Stat("$active", bi("progressScreen.daysPractised"), AlpineGreen, Modifier.weight(1f))
        }
        if (!s.practisedToday && s.current > 0) {
            Caption("⏳ ${bi("progressScreen.notPractisedTodayYet")}")
        }
        Heatmap(
            p.calendar, colors, selected?.date,
            description = inBase("progressScreen.streakCalendar", "active" to active, "minutes" to days.sumOf { it.minutes }, "current" to s.current, "best" to s.best),
            onSelect = { selected = it },
        )
        selected?.let { d ->
            Text(
                "${d.date.short()}  ${if (d.sessions == 0) bi("progressScreen.noPractice") else "${d.minutes} min · ${d.exercises} ${bi("progressScreen.exercises")}"}",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        HeatLegend(colors)
    }
}

@Composable
private fun HeatLegend(colors: ChartColors) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().clearAndSetSemantics { }) {
        Caption("${bi("progressScreen.less")}  ")
        for (level in 0..4) {
            Box(
                Modifier.padding(horizontal = 1.dp).size(10.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (level == 0) colors.empty else colors.data.copy(alpha = HEAT_ALPHA[level])),
            )
        }
        Caption("  ${bi("progressScreen.more")}")
    }
}

@Composable
private fun Stat(value: String, label: String, accent: Color, modifier: Modifier) {
    Surface(color = accent.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small, modifier = modifier) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            // "trenutni · current" on two lines: never cut off, also with large system fonts.
            for (part in label.split(" · ")) {
                Text(part, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun AccuracyCard(p: ProgressStats) {
    val pts = p.accuracy
    var selected by remember(p) { mutableStateOf<Int?>(null) }
    Section("🎯 ${bi("progressScreen.accuracy")}", bi("progressScreen.shareCorrectAnswers")) {
        if (pts.isEmpty()) {
            Caption(bi("progressScreen.noDataYet"))
            return@Section
        }
        val desc = "${inBase("progressScreen.accuracyByDay")}: " +
            pts.joinToString("; ") { inBase("progressScreen.percentOf", "date" to it.date, "pct" to (it.accuracy * 100).roundToInt(), "n" to it.exercises) }
        AccuracyChart(pts, chartColors(), selected, desc, onSelect = { selected = it })
        val f = pts[selected ?: pts.lastIndex]
        Caption("${f.date.short()}: ${(f.accuracy * 100).roundToInt()} % · ${f.exercises} ${bi("progressScreen.exercises")}")
    }
}

@Composable
private fun WeeklyCard(p: ProgressStats) {
    var selected by remember(p) { mutableStateOf<Int?>(null) }
    Section("📈 ${bi("progressScreen.exercisesPerWeek")}") {
        val values = p.weeks.map { it.exercises }
        val desc = "${inBase("progressScreen.exercisesPerWeekDesc")}: " +
            p.weeks.joinToString("; ") { inBase("progressScreen.weekOfDesc", "start" to it.start, "n" to it.exercises) }
        ColumnChart(values, chartColors(), selected, desc, onSelect = { selected = it })
        AxisLabels(p.weeks.map { it.start.short() })
        val w = p.weeks[selected ?: p.weeks.lastIndex]
        Caption(
            "${bi("progressScreen.weekOf")} ${w.start.short()}: ${w.exercises} ${bi("progressScreen.exercises")}, ${w.minutes} min, " +
                "${w.sessions} × ${bi("progressScreen.sessions")}",
        )
    }
}

@Composable
private fun AxisLabels(labels: List<String>, highlight: Int? = null) {
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
        labels.forEachIndexed { i, l ->
            Text(
                l,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (i == highlight) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private val skillNames = mapOf(
    "writing" to ("✍️" to bi("progressScreen.writing")),
    "speaking" to ("🗣️" to bi("progressScreen.speaking")),
    "vocabulary" to ("📚" to bi("progressScreen.vocabulary")),
    "reading" to ("👀" to bi("progressScreen.reading")),
    "listening" to ("🎧" to bi("progressScreen.listening")),
)

@Composable
private fun SkillsCard(skills: List<SkillMastery>) {
    Section("💪 ${bi("progressScreen.skills")}", bi("progressScreen.mastery05Confidence")) {
        skills.forEach { SkillRow(it) }
    }
}

@Composable
private fun SkillRow(s: SkillMastery) {
    val (emoji, name) = skillNames[s.skill] ?: ("•" to s.skill.replaceFirstChar { it.uppercase() })
    val pct = (s.confidence * 100).roundToInt()
    Column(Modifier.clearAndSetSemantics { contentDescription = "$name: ${inBase("progressScreen.skillDesc", "level" to s.level, "pct" to pct)}" }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$emoji  $name", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${s.level}/5", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("  ·  $pct %", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 1..5) {
                Box(
                    Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (i <= s.level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun MistakesCard(mistakes: List<MistakeStat>, today: LocalDate, onAsk: (MistakeStat) -> Unit) {
    Section("⚠️ ${bi("progressScreen.recurringMistakes")}", bi("progressScreen.tapOneAskTutor")) {
        if (mistakes.isEmpty()) {
            Caption(bi("progressScreen.noMistakesTrackedGreat"))
            return@Section
        }
        mistakes.forEach { m -> MistakeRow(m, today) { onAsk(m) } }
    }
}

@Composable
private fun MistakeRow(m: MistakeStat, today: LocalDate, onClick: () -> Unit) {
    val (arrow, color, word) = when (m.trend) {
        Trend.WORSE -> Triple("↑", TriglavRed, bi("progressScreen.recurring"))
        Trend.STEADY -> Triple("→", MaterialTheme.colorScheme.onSurfaceVariant, bi("progressScreen.steady"))
        Trend.BETTER -> Triple("↓", AlpineGreen, bi("progressScreen.improving"))
    }
    val seen = m.lastSeen?.let { d ->
        val days = ChronoUnit.DAYS.between(d, today)
        when (days) {
            0L -> bi("progressScreen.today")
            1L -> bi("progressScreen.yesterday")
            else -> bi("progressScreen.daysDaysAgo", "days" to days)
        }
    } ?: "—"
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (m.wrong != null && m.right != null) {
                    Text("✗ ${m.wrong}  →  ✓ ${m.right}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${m.frequency}× · ${inTarget("progressScreen.lastTime")} $seen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(arrow, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(word, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/**
 * The languages practised elsewhere, each with its own level, words and review deck: "Italijanščina · Italian: A1",
 * "85 besed · 85 words, iz obiskov · from visits". [due]: the cards due in a language's deck, by its code.
 */
@Composable
private fun LanguagesCard(languages: List<LanguageSummary>, due: (String) -> Int, onReview: (String) -> Unit) {
    Section("🌍 ${bi("progressScreen.otherLanguages")}") {
        languages.forEach { l ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${l.label()}: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                    Text(l.level, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                val from = if (l.source == LanguageSummary.VISITS) ", ${bi("progressScreen.fromVisits")}" else ""
                Caption("${bi("progressScreen.languageWords", "n" to l.words)}$from")
                val n = due(l.code)
                if (n > 0) TextButton(onClick = { onReview(l.code) }) { Text("🔄 ${bi("reviewScreen.review")} ($n)") }
            }
        }
    }
}

/** Short weekday names under the chart, in the learner's target language ("po", "to", …). */
private val weekdays = listOf(
    "progressScreen.mon", "progressScreen.tue", "progressScreen.wed", "progressScreen.thu", "progressScreen.fri",
    "progressScreen.sat", "progressScreen.sun",
)

@Composable
private fun DueCard(due: List<DueDay>, overdue: Int) {
    var selected by remember(due) { mutableStateOf<Int?>(null) }
    val total = due.sumOf { it.count }
    Section("📅 ${bi("progressScreen.reviewsDue")}", "${bi("progressScreen.next7Days")}: $total ${bi("progressScreen.cards")}") {
        val desc = "${inBase("progressScreen.cardsDue")}: " +
            due.mapIndexed { i, d -> "${if (i == 0) inBase("progressScreen.today") else d.date.toString()}: ${d.count}" }.joinToString("; ")
        ColumnChart(due.map { it.count }, chartColors(), selected, desc, onSelect = { selected = it })
        AxisLabels(due.mapIndexed { i, d -> if (i == 0) inTarget("progressScreen.today") else inTarget(weekdays[d.date.dayOfWeek.value - 1]) }, highlight = 0)
        val i = selected ?: 0
        val d = due.getOrNull(i) ?: return@Section
        Caption(
            if (i == 0) "${bi("common.today")}: ${d.count} ${bi("progressScreen.cards")}" + (if (overdue > 0) " (${bi("progressScreen.overdue", "overdue" to overdue)})" else "")
            else "${inTarget(weekdays[d.date.dayOfWeek.value - 1])} ${d.date.short()}: ${d.count} ${bi("progressScreen.cards")}",
        )
    }
}
