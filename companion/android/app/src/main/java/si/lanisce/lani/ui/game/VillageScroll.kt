package si.lanisce.lani.ui.game

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.GrammarBook
import si.lanisce.lani.game.LogEntry
import si.lanisce.lani.game.Quest
import si.lanisce.lani.game.QuestQueue
import si.lanisce.lani.game.QuestSource
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.LaniSheet
import si.lanisce.lani.ui.Labels
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The village's scroll (the bottom bar's "📜 Zvitek · Scroll"; companion/GAME.md, "The scroll"): what the village has to
 * browse, its collections and its history, as four cards in a grid ([Collection]: the places and their scenes, the
 * people and how close they are, the grammar pages met, the chronicle), each opening its list; the chronicle's newest
 * line under them. Today and the next goal are the HUD's (its cards and sheet). It is a sheet like the app's others (the
 * chat's, the cheat sheet), not a parchment of its own. Its count on the bottom bar counts only what's new and worth
 * opening ([ScrollData.badge]).
 */
enum class Collection(val emoji: String, val key: String) {
    PLACES("🗺️", "townScroll.places"),
    PEOPLE("👥", "townScroll.villagers"),
    GRAMMAR("📖", "townScroll.grammar"),
    CHRONICLE("📜", "townScroll.chronicle"),
}

/** Someone who lives here: how close Jan is ([hearts], 0 to 4), what they're up to, [new]: moved in or born this week. */
data class PersonRow(val id: String, val name: String, val emoji: String, val hearts: Int, val doing: String?, val new: Boolean)

/** A grammar page met: its title in each language, its level, how well it sits ([stars]), [new]: met this week. */
data class RuleRow(val id: String, val emoji: String, val target: String, val base: String?, val level: String?, val stars: Int?, val new: Boolean)

/** A villager's request in the scroll: up now (its bubble's on the map, [place]), or waiting in "Kasneje · Later" ([why]). */
data class RequestRow(val quest: Quest, val place: TownPlace, val why: QuestQueue.Why? = null)

/** What the scroll shows, and what's new in it since it was last opened. */
class ScrollData(
    val places: List<PlaceEntry>,
    val locked: List<LockedScene>,
    val people: List<PersonRow>,
    /** The requests up now, then the ones waiting ("📜 Kasneje · Later", in the order they come up). */
    val requests: List<RequestRow>,
    /** How many of the learner's own words the dialogs on now test, by place ([TownPlace.key]; SCENES.md "Your words in the dialogs"). */
    val words: Map<String, Int> = emptyMap(),
    val rules: List<RuleRow>,
    /** Rules met in dialogs only (🌱), and the pages still to meet (🔒). */
    val seenRules: Int,
    val moreRules: Int,
    /** Newest first. */
    val chronicle: List<LogEntry>,
    /** When the scroll was last opened (ms): the chronicle's lines after it are new. */
    val since: Long,
) {
    val newPeople: Int get() = people.count { it.new }
    val newRules: Int get() = rules.count { it.new }
    val newLog: Int get() = chronicle.count { it.at > since }

    /**
     * The bottom bar's count: who joined the village and the grammar pages met this week, not seen in the scroll yet. The
     * chronicle's new lines are marked in it, not counted (something happens every day).
     */
    val badge: Int get() = newPeople + newRules

    /** How many things a collection holds. */
    fun count(c: Collection): Int = when (c) {
        Collection.PLACES -> places.size
        Collection.PEOPLE -> people.size
        Collection.GRAMMAR -> rules.size
        Collection.CHRONICLE -> chronicle.size
    }

    /** How many of them are new. */
    fun new(c: Collection): Int = when (c) {
        Collection.PLACES -> 0
        Collection.PEOPLE -> newPeople
        Collection.GRAMMAR -> newRules
        Collection.CHRONICLE -> newLog
    }

    /** The keys of what the scroll showed as new: seen once it closes. */
    val shownNew: Set<String> get() = (people.filter { it.new }.map { "person:${it.id}" } + rules.filter { it.new }.map { "rule:${it.id}" }).toSet()

    companion object {
        /** How long someone who joined, or a page met, is new in the scroll (until it's seen there). */
        const val WEEK = 7L

        /**
         * [people]: who lives here (the cast's and the village's own); [chapter]: the grammar pages met ([meter]: how well
         * each sits); [seen] and [since]: what the scroll showed as new before, and when it was last opened ([ScrollPrefs]).
         */
        fun of(
            s: GameState, overview: TownOverview, people: List<Villager>, chapter: GrammarBook.Chapter, meter: (String) -> Int?,
            seen: Set<String>, since: Long, today: LocalDate = LocalDate.now(), language: String = "sl",
        ): ScrollData {
            val doing = overview.residents.associateBy { it.name }
            val joined = s.residents.associate { it.id to runCatching { LocalDate.parse(it.since) }.getOrNull() }
            val met = GrammarBook.met(s, chapter.met.firstOrNull()?.language ?: language)
            val base = L10n.pair.base.code
            fun recent(d: LocalDate?) = d != null && !d.isBefore(today.minusDays(WEEK))
            return ScrollData(
                places = overview.places,
                locked = overview.locked,
                people = people.map { v ->
                    val d = doing[v.name]?.doing?.firstOrNull()
                    PersonRow(
                        v.id, v.name, v.emoji, Bonds.level(s, v.id),
                        when (d) {
                            is Doing.Asks -> "📋 ${d.quest.title}"
                            is Doing.Busy -> "💬 ${d.on.happening.title}"
                            is Doing.Lives -> "🏠 ${d.scene.title}"
                            null -> null
                        },
                        new = recent(joined[v.id]) && "person:${v.id}" !in seen,
                    )
                }.sortedWith(compareBy({ !it.new }, { -it.hearts })),
                words = overview.today.filterIsInstance<TodayItem.Happening>().filter { it.now && it.words > 0 }
                    .groupBy { it.place.key() }.mapValues { (_, hs) -> hs.sumOf { it.words } },
                requests = overview.today.filterIsInstance<TodayItem.Task>().map { RequestRow(it.quest, it.place) } +
                    overview.later.map { w -> RequestRow(w.quest, TownMarkers.questPlace(w.quest.giver, w.quest.source, s), w.why) },
                rules = chapter.met.map { p ->
                    val on = met[p.id]?.on?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
                    RuleRow(
                        p.id, p.emoji, p.target, p.titleIn(base).takeIf { it != p.target }, p.level, meter(p.id),
                        new = recent(on) && "rule:${p.id}" !in seen,
                    )
                }.sortedBy { !it.new },
                seenRules = chapter.seen.size,
                moreRules = chapter.more,
                chronicle = overview.chronicle,
                since = since,
            )
        }
    }
}

/** What the scroll remembers on this phone: what it showed as new, and when it was last opened. */
class ScrollPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("village_scroll", Context.MODE_PRIVATE)

    var seen by mutableStateOf(prefs.getStringSet("seen", null)?.toSet().orEmpty())
        private set

    /** The first time: a day ago, so yesterday's chronicle shows as new once. */
    var since by mutableStateOf(prefs.getLong("since", System.currentTimeMillis() - 24 * 3600_000L))
        private set

    /** The scroll closed, having shown [shown] as new: seen, and the chronicle's new lines read, at [at]. */
    fun closed(shown: Set<String>, at: Long = System.currentTimeMillis()) {
        seen = seen + shown
        since = at
        prefs.edit().putStringSet("seen", seen).putLong("since", at).apply()
    }
}

@Composable
fun rememberScrollPrefs(): ScrollPrefs {
    val context = LocalContext.current
    return remember { ScrollPrefs(context) }
}

/** What the scroll's rows do. */
class ScrollRowActions(
    /** A place of the village: flies there and opens its card (the sheet closes). */
    val place: (TownPlace) -> Unit,
    /** Goes into a scene. */
    val scene: (SceneSpec) -> Unit,
    /** Someone's card over the map. */
    val person: (String) -> Unit,
    /** "Vsi prebivalci · Everyone in the village": the register. */
    val register: () -> Unit,
    /** A grammar page, over the scroll. */
    val rule: (String) -> Unit,
    /** "Prijatelji · Friends": the towns linked with this one; null: none. */
    val friends: (() -> Unit)? = null,
    /** "📖 Branje · Reading": the reading corner. */
    val reading: (() -> Unit)? = null,
    /** "🏫 Šola · School": the school's course; null without a course. */
    val school: (() -> Unit)? = null,
    /** A villager's request: its run. */
    val quest: (Quest) -> Unit = {},
)

private val dateFmt = DateTimeFormatter.ofPattern("d. M. HH:mm")

/** "♥♥♡♡" for a friendship level of 2. */
private fun hearts(n: Int): String = "♥".repeat(n.coerceIn(0, 4)) + "♡".repeat(4 - n.coerceIn(0, 4))

/** "Kraji · Places: 17, novo · new: 2": what TalkBack reads for a collection. */
private fun said(c: Collection, d: ScrollData): String {
    val n = d.new(c)
    return "${bi(c.key)}: ${d.count(c)}" + if (n > 0) ", ${bi("cheatSheet.new")}: $n" else ""
}

/**
 * The scroll: a sheet with the four collections as cards in a grid (a count, a peek, "✨ Novo: 7") and the chronicle's
 * newest line under them; a card opens its list in the sheet, "‹" (or back) leads to the grid again. What it showed as
 * new is seen once it closes ([prefs]).
 */
@Composable
fun VillageScroll(d: ScrollData, a: ScrollRowActions, prefs: ScrollPrefs, start: Collection? = null, onClose: () -> Unit) {
    var open by remember { mutableStateOf(start) }
    val shown by rememberUpdatedState(d.shownNew)
    DisposableEffect(Unit) { onDispose { prefs.closed(shown) } }
    LaniSheet(onDismissRequest = onClose) {
        BackHandler(enabled = open != null) { open = null }
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 16.dp)) {
            val c = open
            if (c == null) Grid(d, onOpen = { open = it }, onClose = onClose)
            else {
                ListHeader(c, onBack = { open = null }, onClose = onClose)
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    collection(c, d, a)
                }
            }
        }
    }
}

/** The grid: the scroll's title, the four collections, the chronicle's newest line. */
@Composable
private fun Grid(d: ScrollData, onOpen: (Collection) -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("📜 ${bi("villageScreen.scroll")}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(bi("scrollSheet.about"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CloseButton(onClose)
        }
        Spacer(Modifier.size(12.dp))
        for (row in Collection.entries.chunked(2)) {
            Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (k in row) CollectionTile(k, d, Modifier.weight(1f)) { onOpen(k) }
            }
        }
        // the newest of the chronicle under the grid: the village's story goes on
        d.chronicle.firstOrNull()?.let { e ->
            SubHeading("📜 ${bi("townScroll.chronicle")}")
            Spacer(Modifier.size(6.dp))
            ItemRow(e.emoji, e.text, dateFmt.format(Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault())), new = e.at > d.since, onClick = { onOpen(Collection.CHRONICLE) })
        }
    }
}

/** A list's header: "‹" to the grid, the collection's emoji and name in each language, ✕. */
@Composable
private fun ListHeader(c: Collection, onBack: () -> Unit, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onBack, shape = RoundedCornerShape(12.dp), color = Color.Transparent,
            modifier = Modifier.weight(1f).semantics { contentDescription = "‹ ${bi("villageScreen.scroll")}" },
        ) {
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text("${c.emoji} ${bi(c.key)}", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            }
        }
        CloseButton(onClose)
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    Surface(
        onClick = onClose, shape = CircleShape, color = Color.Transparent,
        modifier = Modifier.size(48.dp).clearAndSetSemantics { role = Role.Button; contentDescription = Labels.CLOSE; onClick { onClose(); true } },
    ) {
        Box(contentAlignment = Alignment.Center) { Text("✕", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** A collection's card: its emoji and how many, its name in each language, a peek at what's in it, what's new. */
@Composable
private fun CollectionTile(c: Collection, d: ScrollData, modifier: Modifier, onClick: () -> Unit) {
    val n = d.new(c)
    val peek = when (c) {
        Collection.PLACES -> d.places.take(8).joinToString(" ") { it.place.emoji() }
        Collection.PEOPLE -> d.people.take(8).joinToString(" ") { it.emoji }
        Collection.GRAMMAR -> d.rules.firstOrNull()?.let { "${it.emoji} ${it.target}" } ?: bi("grammar.empty")
        Collection.CHRONICLE -> d.chronicle.firstOrNull()?.text ?: bi("townScroll.storyJustBeginning")
    }
    Surface(
        onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.heightIn(min = 150.dp).semantics { contentDescription = said(c, d) },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.weight(1f))
                Text("${d.count(c)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text(inTarget(c.key), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(inBase(c.key), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(peek, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (n > 0) Text("✨ ${inTarget("cheatSheet.new")}: $n", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

/** One row: an emoji in a disc, a title and what's under it, "✨ Novo" when new; a tap leads on when it does. */
@Composable
private fun ItemRow(emoji: String, title: String, sub: String?, extra: String? = null, new: Boolean = false, dim: Boolean = false, onClick: (() -> Unit)?) {
    val color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dim) 0.5f else 1f)
    val body: @Composable () -> Unit = {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                Text(emoji, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                sub?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                extra?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            }
            if (new) Text(
                "✨ ${inTarget("cheatSheet.new")}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(start = 6.dp),
            )
            if (onClick != null) Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
        }
    }
    if (onClick != null) Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = color, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { body() }
    else Surface(shape = RoundedCornerShape(16.dp), color = color, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics(mergeDescendants = true) { }) { body() }
}

/** A request: its emoji, title, who asks and where (or why it waits), how close Jan is; a tap: its run. */
@Composable
private fun RequestItem(r: RequestRow, a: ScrollRowActions) {
    val q = r.quest
    val who = listOfNotNull(q.giver, r.why?.let { whyText(it) } ?: r.place.where()).joinToString(" · ")
    ItemRow(
        q.emoji, q.title, listOfNotNull(who, progressText(q)).joinToString("\n"),
        extra = if (q.source == QuestSource.TUTOR) "🧑‍🏫 ${bi("townScroll.fromTutor")}" else null,
        dim = r.why != null, onClick = { a.quest(q) },
    )
}

@Composable
private fun SubHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

/** A collection's list. */
@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.collection(c: Collection, d: ScrollData, a: ScrollRowActions) {
    when (c) {
        Collection.PLACES -> {
            // each place of the village (a tap: there, its card), the scenes that open there (a tap: in)
            for (e in d.places) item("place:${e.place.key()}") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ItemRow(
                        e.place.emoji(), e.place.label() + if (e.count > 1) "  ×${e.count}" else "",
                        e.place.where() + if (e.here.isEmpty()) "" else " · " + e.here.joinToString(" ") { it.emoji },
                        // the learner's own words a dialog on there now tests
                        extra = d.words[e.place.key()]?.let { "📇 ${bi("dialogWords.inHappening", "n" to it)}" },
                        onClick = { a.place(e.place) },
                    )
                    if (e.scenes.isNotEmpty()) FlowRow(Modifier.padding(start = 52.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (sc in e.scenes) Surface(onClick = { a.scene(sc) }, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.heightIn(min = 40.dp)) {
                            Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text("🚪 ${sc.emoji} ${sc.title}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (d.locked.isNotEmpty()) {
                item("lockedTitle") { SubHeading("🔒 ${bi("townScroll.stillLocked")}") }
                for (l in d.locked) item("locked:${l.scene.id}") { ItemRow(l.scene.emoji, l.scene.title, l.needs.joinToString(" · "), dim = true, onClick = null) }
            }
        }
        Collection.PEOPLE -> {
            item("register") { ItemRow("📖", bi("townScroll.everyoneVillage"), bi("townScroll.friendshipsMemoriesTalks"), onClick = a.register) }
            a.friends?.let { open -> item("friends") { ItemRow("🤝", bi("towns.friends"), bi("towns.friendsAbout"), onClick = open) } }
            for (p in d.people) item("person:${p.id}") {
                ItemRow(p.emoji, p.name, p.doing, extra = hearts(p.hearts), new = p.new, onClick = { a.person(p.id) })
            }
            // their requests: the ones up now (their bubbles are on the map), then the ones waiting, each opening its run
            val up = d.requests.filter { it.why == null }
            val later = d.requests.filter { it.why != null }
            if (up.isNotEmpty()) item("requestsTitle") { SubHeading("📋 ${bi("townScroll.villagersRequests")}") }
            for (r in up) item("request:${r.quest.id}") { RequestItem(r, a) }
            if (later.isNotEmpty()) item("laterTitle") { SubHeading(laterTitle()) }
            for (r in later) item("later:${r.quest.id}") { RequestItem(r, a) }
        }
        Collection.GRAMMAR -> {
            a.reading?.let { open -> item("reading") { ItemRow("📖", bi("readings.title"), bi("readings.inBook"), onClick = open) } }
            a.school?.let { open -> item("school") { ItemRow("🏫", bi("school.title"), bi("school.about"), onClick = open) } }
            if (d.rules.isEmpty()) item("noRules") { Text(bi("grammar.empty"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            for (r in d.rules) item("rule:${r.id}") {
                ItemRow(
                    r.emoji, r.target, listOfNotNull(r.base, r.level).joinToString(" · "),
                    extra = r.stars?.let { "${stars(it)}  ${bi("grammar.howWell")}" }, new = r.new, onClick = { a.rule(r.id) },
                )
            }
            if (d.seenRules > 0) item("seen") { ItemRow("🌱", bi("scrollSheet.seenInDialogs", "n" to d.seenRules), bi("grammar.unlockWhenMet"), dim = true, onClick = null) }
            if (d.moreRules > 0) item("more") { ItemRow("🔒", bi("grammar.more", "n" to d.moreRules), bi("grammar.unlockWhenMet"), dim = true, onClick = null) }
        }
        Collection.CHRONICLE -> {
            if (d.chronicle.isEmpty()) item("noLog") { Text(bi("townScroll.storyJustBeginning"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            for ((i, e) in d.chronicle.withIndex()) item("log:$i:${e.at}") {
                ItemRow(e.emoji, e.text, dateFmt.format(Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault())), new = e.at > d.since, onClick = null)
            }
        }
    }
}
