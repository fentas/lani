package si.lanisce.lani.ui.game

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.GrammarBook
import si.lanisce.lani.game.LogEntry
import si.lanisce.lani.game.scene.SceneSpec
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.bi
import si.lanisce.lani.l10n.inBase
import si.lanisce.lani.l10n.inTarget
import si.lanisce.lani.ui.LaniSheet
import si.lanisce.lani.ui.theme.XpGold
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Design variants of the village's scroll (the bottom bar's "📜 Zvitek · Scroll"), for Jan to compare on a phone (QA's
 * hook "scroll:variant:<A|B|C>" in a debug build; the parchment scroll stays the default). With the HUD's cards holding
 * today and the next goal, the scroll keeps what's there to browse: the village's collections and its history, the
 * places and what's there, the people and how close they are, the grammar pages met, the chronicle. Its count on the
 * bottom bar counts only what's new and worth opening: who joined the village and the grammar pages met this week, not
 * seen in it yet ([ScrollData.badge]).
 *
 * - [A]: one sheet with its four collections as tabs (chips), each a list.
 * - [B]: one sheet with the four collections as cards in a grid (a count, a peek, what's new), each opening its list.
 * - [C]: a fourth card in the HUD's row, "📜 Zvitek · Scroll", whose section in the HUD's sheet holds the four
 *   collections, each opening in place.
 */
enum class ScrollVariant {
    A, B, C;

    companion object {
        fun of(name: String?): ScrollVariant? = entries.firstOrNull { it.name == name }
    }
}

/** The scroll's collections. */
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

/** What the scroll's variants show, and what's new in it since it was last opened. */
class ScrollData(
    val places: List<PlaceEntry>,
    val locked: List<LockedScene>,
    val people: List<PersonRow>,
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

    /** The bottom bar's count: who joined the village and the grammar pages met, not seen in the scroll yet. */
    val badge: Int get() = newPeople + newRules

    /** What a collection holds, and how much of it is new. */
    fun count(c: Collection): Int = when (c) {
        Collection.PLACES -> places.size
        Collection.PEOPLE -> people.size
        Collection.GRAMMAR -> rules.size
        Collection.CHRONICLE -> chronicle.size
    }

    fun new(c: Collection): Int = when (c) {
        Collection.PLACES -> 0
        Collection.PEOPLE -> newPeople
        Collection.GRAMMAR -> newRules
        Collection.CHRONICLE -> newLog
    }

    /** The ids the scroll showed as new: seen once it closes. */
    val shownNew: Set<String> get() = (people.filter { it.new }.map { "person:${it.id}" } + rules.filter { it.new }.map { "rule:${it.id}" }).toSet()

    companion object {
        private const val WEEK = 7L

        fun of(
            s: GameState, overview: TownOverview, people: List<Villager>, chapter: GrammarBook.Chapter, meter: (String) -> Int?,
            seen: Set<String>, since: Long, today: LocalDate = LocalDate.now(),
        ): ScrollData {
            val doing = overview.residents.associateBy { it.name }
            val joined = s.residents.associate { it.id to runCatching { LocalDate.parse(it.since) }.getOrNull() }
            val met = GrammarBook.met(s, chapter.met.firstOrNull()?.language ?: "sl")
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

    /** As on a phone that never opened it (QA, switching variants): this week's people and pages new again. */
    fun reset(at: Long = System.currentTimeMillis()) {
        seen = emptySet()
        since = at - 24 * 3600_000L
        prefs.edit().clear().apply()
    }

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
    val friends: (() -> Unit)? = null,
    val reading: (() -> Unit)? = null,
    val school: (() -> Unit)? = null,
)

private val dateFmt = DateTimeFormatter.ofPattern("d. M. HH:mm")

/** "♥♥♡♡" for a friendship level of 2. */
private fun hearts(n: Int): String = "♥".repeat(n.coerceIn(0, 4)) + "♡".repeat(4 - n.coerceIn(0, 4))

// --- the rows (A and B: the app's sheets' look) ----------------------------------------------

/** "📜 Zvitek · Scroll" (from Vas "📖 Knjiga · Book") and what it holds. */
@Composable
private fun ScrollTitle(book: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "${if (book) "📖" else "📜"} ${bi(if (book) "villageScreen.book" else "villageScreen.scroll")}",
            style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() },
        )
        Text(bi("scrollSheet.about"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    else Surface(shape = RoundedCornerShape(16.dp), color = color, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { body() }
}

@Composable
private fun SubHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

/** A collection's list, in the app's sheets' look. */
@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.collection(c: Collection, d: ScrollData, a: ScrollRowActions) {
    when (c) {
        Collection.PLACES -> {
            for (e in d.places) item("place:${e.place.key()}") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ItemRow(
                        e.place.emoji(), e.place.label() + if (e.count > 1) "  ×${e.count}" else "",
                        e.place.where() + if (e.here.isEmpty()) "" else " · " + e.here.joinToString(" ") { it.emoji },
                        onClick = { a.place(e.place) },
                    )
                    if (e.scenes.isNotEmpty()) FlowRow(Modifier.padding(start = 52.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (sc in e.scenes) Surface(onClick = { a.scene(sc) }, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.heightIn(min = 40.dp)) {
                            Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text("🚪 ${sc.emoji} ${sc.title}", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** Marks what the sheet showed as new as seen once it goes. */
@Composable
private fun SeenOnClose(d: ScrollData, prefs: ScrollPrefs) {
    val shown by rememberUpdatedState(d.shownNew)
    DisposableEffect(Unit) { onDispose { prefs.closed(shown) } }
}

// --- A: tabs ----------------------------------------------------------------------------------

/** A: one sheet, its four collections as chips at its top, the chosen one's list under them. */
@Composable
fun ScrollSheetTabs(d: ScrollData, a: ScrollRowActions, book: Boolean, prefs: ScrollPrefs, start: Collection = Collection.PLACES, onClose: () -> Unit) {
    var at by remember { mutableStateOf(start) }
    SeenOnClose(d, prefs)
    LaniSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 16.dp)) {
            ScrollTitle(book)
            Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in Collection.entries) {
                    val n = d.new(c)
                    FilterChip(
                        selected = at == c, onClick = { at = c },
                        label = { Text("${c.emoji} ${inTarget(c.key)}  ${d.count(c)}" + if (n > 0) "  ✨$n" else "") },
                        modifier = Modifier.semantics { contentDescription = "${bi(c.key)}: ${d.count(c)}" + if (n > 0) ", ${bi("cheatSheet.new")}: $n" else "" },
                    )
                }
            }
            Text(inBase(at.key), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                collection(at, d, a)
            }
        }
    }
}

// --- B: a grid of collections -----------------------------------------------------------------

/** B: one sheet, its four collections as cards in a grid (a count, a peek, what's new); a card opens its list. */
@Composable
fun ScrollSheetGrid(d: ScrollData, a: ScrollRowActions, book: Boolean, prefs: ScrollPrefs, onClose: () -> Unit) {
    var open by remember { mutableStateOf<Collection?>(null) }
    SeenOnClose(d, prefs)
    LaniSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 16.dp)) {
            val c = open
            if (c == null) {
                ScrollTitle(book)
                Spacer(Modifier.size(12.dp))
                for (row in Collection.entries.chunked(2)) {
                    Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (k in row) CollectionTile(k, d, Modifier.weight(1f)) { open = k }
                    }
                }
                // the newest of the chronicle under the grid: the village's story goes on
                d.chronicle.firstOrNull()?.let { e ->
                    SubHeading("📜 ${bi("townScroll.chronicle")}")
                    Spacer(Modifier.size(6.dp))
                    ItemRow(e.emoji, e.text, dateFmt.format(Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault())), new = e.at > d.since, onClick = { open = Collection.CHRONICLE })
                }
            } else {
                Surface(onClick = { open = null }, shape = RoundedCornerShape(12.dp), color = Color.Transparent, modifier = Modifier.semantics { contentDescription = "‹ " + bi(if (book) "villageScreen.book" else "villageScreen.scroll") }) {
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("‹", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text("${c.emoji} ${bi(c.key)}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    collection(c, d, a)
                }
            }
        }
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
        modifier = modifier.heightIn(min = 150.dp).semantics { contentDescription = "${bi(c.key)}: ${d.count(c)}" + if (n > 0) ", ${bi("cheatSheet.new")}: $n" else "" },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.weight(1f))
                Text("${d.count(c)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text(inTarget(c.key), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(inBase(c.key), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(peek, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (n > 0) Text("✨ ${inTarget("cheatSheet.new")}: $n", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

// --- C: in the HUD's sheet ----------------------------------------------------------------------

/** C's card in the HUD's row: the collections in brief, the chronicle's newest line. */
fun scrollCard(d: ScrollData): Pair<String, String> =
    Collection.entries.take(3).joinToString("  ") { "${it.emoji} ${d.count(it)}" } to (d.chronicle.firstOrNull()?.let { "${it.emoji} ${it.text}" } ?: "")

/** C: the scroll as a section of the HUD's sheet, in its look: the four collections, each opening in place. */
@Composable
fun ScrollHudSection(d: ScrollData, a: ScrollRowActions, prefs: ScrollPrefs) {
    var open by remember { mutableStateOf<Collection?>(null) }
    SeenOnClose(d, prefs)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 28.dp).semantics { heading(); contentDescription = bi("villageScreen.scroll") }, verticalAlignment = Alignment.CenterVertically) {
            Text(inTarget("villageScreen.scroll").uppercase(), color = XpGold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(" · ${inBase("villageScreen.scroll")}", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelMedium)
        }
        for (c in Collection.entries) {
            val n = d.new(c)
            val isOpen = open == c
            Surface(
                onClick = { open = if (isOpen) null else c }, color = PanelChip, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
                    .semantics { contentDescription = "${bi(c.key)}: ${d.count(c)}" + if (n > 0) ", ${bi("cheatSheet.new")}: $n" else "" },
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text(inTarget(c.key), color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(inBase(c.key), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                    }
                    if (n > 0) Text("✨ $n", color = XpGold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                    Text("${d.count(c)}", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (isOpen) "  ▾" else "  ▸", color = Color.White.copy(alpha = 0.7f))
                }
            }
            if (isOpen) DarkList(c, d, a)
        }
    }
}

/** A collection's list in the HUD's dark look (at most a dozen, the register and the chronicle lead on). */
@Composable
private fun DarkList(c: Collection, d: ScrollData, a: ScrollRowActions) {
    @Composable
    fun line(emoji: String, title: String, sub: String?, new: Boolean = false, onClick: (() -> Unit)?) {
        val body: @Composable () -> Unit = {
            Row(Modifier.padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(30.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    sub?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                if (new) Text("✨", color = XpGold)
                if (onClick != null) Text("›", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (onClick != null) Surface(onClick = onClick, color = Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(start = 16.dp)) { body() }
        else Surface(color = Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(start = 16.dp)) { body() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (c) {
            Collection.PLACES -> for (e in d.places) line(e.place.emoji(), e.place.label(), e.scenes.joinToString(" · ") { "${it.emoji} ${it.title}" }.ifBlank { e.place.where() }) { a.place(e.place) }
            Collection.PEOPLE -> {
                line("📖", bi("townScroll.everyoneVillage"), bi("townScroll.friendshipsMemoriesTalks"), onClick = a.register)
                for (p in d.people) line(p.emoji, "${p.name}  ${hearts(p.hearts)}", p.doing, p.new) { a.person(p.id) }
            }
            Collection.GRAMMAR -> {
                if (d.rules.isEmpty()) line("📖", bi("grammar.empty"), null, onClick = null)
                for (r in d.rules) line(r.emoji, r.target, listOfNotNull(r.base, r.level, r.stars?.let { stars(it) }).joinToString(" · "), r.new) { a.rule(r.id) }
            }
            Collection.CHRONICLE -> for (e in d.chronicle.take(12)) line(e.emoji, e.text, dateFmt.format(Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault())), e.at > d.since, onClick = null)
        }
    }
}
