package si.lanisce.lani.ui.game

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.data.GrammarPage
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.GrammarBook
import si.lanisce.lani.game.LogEntry
import si.lanisce.lani.game.RuleRecord
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.scene.TimeOfDay
import java.time.LocalDate

/**
 * The village's scroll (companion/GAME.md, "The scroll"): what it holds and what's new in it ([ScrollData]), its count on
 * the bottom bar, cleared once it was open ([ScrollPrefs]); and on a phone's screen, its grid of four collections, each
 * opening its list, "‹" back to the grid.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class VillageScrollTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 10, 17)
    private val s = GameState(
        age = Age.ZASELEK,
        residents = listOf(Resident("micka", "2026-09-24"), Resident("tone", "2026-09-28"), Resident("mojca", "2026-10-14")),
        grammar = mapOf("sl/biti" to RuleRecord("2026-09-25"), "sl/orodnik" to RuleRecord("2026-10-11", right = 3), "sl/kazalni" to RuleRecord("2026-10-16")),
        log = listOf(LogEntry(1_000L, "🔥", "Ogenj gori · The fire burns"), LogEntry(9_000L, "🧳", "Mojca se je preselila · Mojca moved in")),
    )
    private val people = listOf(
        Villager("micka", "Babica Micka", "👵", "grandma"),
        Villager("tone", "Kovač Tone", "⚒️", "smith", voice = "male"),
        Villager("mojca", "Učiteljica Mojca", "👩‍🏫", "teacher"),
    )
    private fun page(id: String, title: String) = GrammarPage(id, "sl", "A1", "📖", mapOf("sl" to title, "en" to "$title (en)"), mapOf("en" to "…"))
    private val chapter = GrammarBook.Chapter(listOf(page("biti", "Biti"), page("orodnik", "Orodnik"), page("kazalni", "Kazalni zaimki")), more = 4)
    private val overview = TownOverview(TimeOfDay.EVENING, emptyList(), emptyList(), emptyList(), emptyList(), s.log.reversed())

    private fun data(seen: Set<String> = emptySet(), since: Long = 5_000L) =
        ScrollData.of(s, overview, people, chapter, meter = { if (it == "orodnik") 3 else null }, seen = seen, since = since, today = today)

    @Before fun fresh() {
        context.getSharedPreferences("village_scroll", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `new is who joined and the pages met this week, not seen in the scroll yet`() {
        val d = data()
        assertEquals(listOf("mojca"), d.people.filter { it.new }.map { it.id })
        assertEquals("the new first", "mojca", d.people.first().id)
        assertEquals(setOf("orodnik", "kazalni"), d.rules.filter { it.new }.map { it.id }.toSet())
        assertEquals("the bottom bar's count: one person, two pages", 3, d.badge)
        assertEquals("the chronicle's line since it was opened: marked, not counted", 1, d.newLog)
        assertEquals(3, d.rules.first { it.id == "orodnik" }.stars)
        assertEquals("Orodnik (en)", d.rules.first { it.id == "orodnik" }.base)
        val after = data(seen = d.shownNew, since = 10_000L)
        assertEquals("seen in the scroll: no count", 0, after.badge)
        assertEquals(0, after.newLog)
    }

    @Test fun `the scroll remembers what it showed as new once it closes`() {
        val prefs = ScrollPrefs(context)
        prefs.closed(setOf("person:mojca"), at = 42L)
        val again = ScrollPrefs(context)
        assertEquals(setOf("person:mojca"), again.seen)
        assertEquals(42L, again.since)
    }

    @Test fun `the grid holds the four collections, each opening its list, back to the grid`() {
        val prefs = ScrollPrefs(context)
        var closed = false
        val opened = mutableListOf<String>()
        compose.setContent {
            VillageScroll(
                data(), ScrollRowActions(place = {}, scene = {}, person = { opened += "person:$it" }, register = { opened += "register" }, rule = { opened += "rule:$it" }),
                prefs, onClose = { closed = true },
            )
        }
        compose.onNodeWithContentDescription("Kraji · Places: 0").assertExists()
        compose.onNodeWithContentDescription("Prebivalci · Villagers: 3, Novo · New: 1").assertExists()
        compose.onNodeWithContentDescription("Slovnica · Grammar: 3, Novo · New: 2").assertExists()
        compose.onNodeWithContentDescription("Kronika · Chronicle: 2, Novo · New: 1").assertExists()
        // the chronicle's newest line under the grid
        compose.onAllNodes(hasText("Mojca se je preselila", substring = true)).assertCountEquals(2) // the card's peek, and the line

        compose.onNodeWithContentDescription("Prebivalci · Villagers: 3", substring = true).performClick()
        compose.onNode(hasText("Vsi prebivalci", substring = true)).performClick()
        compose.onNode(hasText("Učiteljica Mojca", substring = true)).performClick()
        assertEquals(listOf("register", "person:mojca"), opened)
        compose.onNodeWithContentDescription("‹ Zvitek · Scroll").performClick()
        compose.onNodeWithContentDescription("Slovnica · Grammar: 3", substring = true).performClick()
        compose.onNode(hasText("Orodnik", substring = true)).performClick()
        assertEquals("rule:orodnik", opened.last())
        compose.onAllNodesWithContentDescription("Kraji · Places", substring = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("Zapri · Close").performClick()
        assertTrue(closed)
    }
}
