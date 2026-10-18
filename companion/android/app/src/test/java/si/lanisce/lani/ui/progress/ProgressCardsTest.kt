package si.lanisce.lani.ui.progress

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.data.GrammarPage
import si.lanisce.lani.data.UnitRef
import si.lanisce.lani.game.HuntStatus
import si.lanisce.lani.game.Readiness
import si.lanisce.lani.game.Station

/**
 * The progress screen's new cards on a phone's screen: "🧭 Do A2 · To A2" (each sign read as one by TalkBack, the pages
 * still to secure and the map one tap away) and "📝 Slovnica · Grammar" (the bars' TalkBack labels, the next unit and the
 * weakest page one tap away). A fixture learner.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ProgressCardsTest {
    @get:Rule val compose = createComposeRule()

    private fun page(id: String, title: String) = GrammarPage(id, "sl", "A1", "📖", mapOf("sl" to title, "en" to "$title (en)"), mapOf("en" to "…"))
    private val pages = listOf(page("dajalnik", "Dajalnik"), page("tozilnik", "Tožilnik"), page("mnozina", "Množina")).associateBy { it.id }

    private val unit = UnitRef("osebni-zaimki", mapOf("sl" to "Osebni zaimki", "en" to "Personal pronouns"))

    private val readiness = Readiness(
        from = "A1", to = "A2", pages = listOf("biti", "dajalnik", "tozilnik"), secure = listOf("biti"), needSecure = 3,
        reviews = 24, kept = 22, practised = 12, atLevel = 20,
    )

    @Test fun `the way to A2 reads each sign as one, and leads to the pages and the map`() {
        val opened = mutableListOf<String>()
        val path = ProgressLogic.path(readiness, 312, HuntStatus.NONE, null, Station.entries.toList(), "Stari Janez", false)!!
        compose.setContent { PathCard(path, page = pages::get, onPage = { opened += "page:$it" }, onMap = { opened += "map" }) }
        compose.onNodeWithText("🧭 Do A2 · To A2").assertExists()
        // how the level rises, with the storyteller's name
        compose.onNode(hasText("Raven zraste z zemljevidom zaklada", substring = true)).assertExists()
        compose.onNode(hasText("Stari Janez", substring = true)).assertExists()
        compose.onNodeWithContentDescription("Words: 312 / 300. met").assertExists()
        compose.onNodeWithContentDescription("Rules of A1 secure: 1 / 3. Needed: 3 of the level's 3 pages (70 %). not yet").assertExists()
        compose.onNodeWithContentDescription("Reviews, last 14 days: 92 %", substring = true).assertExists()
        compose.onNodeWithContentDescription("Days of practice at A1: 12 / 10", substring = true).assertExists()
        // the pages still to secure, a tap: the page
        compose.onNode(hasText("Tožilnik", substring = true)).performClick()
        compose.onNode(hasText("Zemljevid zaklada · The treasure map ›", substring = true)).performClick()
        assertEquals(listOf("page:tozilnik", "map"), opened)
    }

    @Test fun `the map waiting is a tap away`() {
        var opened = false
        val ready = readiness.copy(secure = readiness.pages, reviews = 40, kept = 36)
        val path = ProgressLogic.path(ready, 312, HuntStatus.OFFERED, null, Station.entries.toList(), "Stari Janez", false)!!
        compose.setContent { PathCard(path, page = pages::get, onPage = {}, onMap = { opened = true }) }
        compose.onNodeWithText("Zemljevid zaklada te čaka · The treasure map is waiting").performClick()
        compose.onNodeWithText("Stari Janez ima zate star zemljevid · Stari Janez has an old map for you").assertExists()
        compose.onAllNodesWithText("Raven zraste", substring = true).assertCountEquals(0)
        assertEquals(true, opened)
    }

    @Test fun `the grammar shows the school's bar, the next unit, the book's bar and its weakest pages`() {
        val opened = mutableListOf<String>()
        val g = GrammarStanding(
            school = SchoolStanding(
                "A1", done = 1, of = 18, started = listOf(UnitRef("spol", mapOf("sl" to "Spol", "en" to "Gender")), unit), next = UnitRef("vprasalnice", mapOf("sl" to "Vprašalnice", "en" to "Question words")),
                open = true,
            ),
            book = BookStanding("A1", pages = 28, met = 12, secure = 5, weakest = listOf(PageStanding(pages.getValue("dajalnik"), 1), PageStanding(pages.getValue("mnozina"), null))),
            counting = CountingStanding(cards = 9, secure = 4, due = 2),
        )
        compose.setContent {
            Column { GrammarCard(g, onSchool = { opened += "school" }, onUnit = { opened += "unit:$it" }, onPage = { opened += "page:$it" }) }
        }
        compose.onNodeWithText("📝 Slovnica · Grammar").assertExists()
        compose.onNodeWithContentDescription("A1 units done: 1 of 18").assertExists()
        compose.onNodeWithText("1 / 18").assertExists()
        compose.onNodeWithText("▶ V teku · In progress: Spol · Gender, Osebni zaimki · Personal pronouns").assertExists()
        compose.onNodeWithText("Naslednja · Next", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("A1 pages in the book: 28; met: 12; secure: 5").assertExists()
        compose.onNodeWithContentDescription("Dajalnik (en): 1 of 5 stars").assertExists()
        compose.onNodeWithContentDescription("Množina (en): not practised yet").performClick()
        compose.onNode(hasText("cards: 9, secure: 4, due today: 2", substring = true)).assertExists()
        compose.onNodeWithText("Vprašalnice · Question words", useUnmergedTree = true).performClick()
        compose.onNodeWithText("1 / 18").performClick()
        assertEquals(listOf("page:mnozina", "unit:vprasalnice", "school"), opened)
    }

    @Test fun `the unit suggested next, begun already, says so in its row`() {
        val g = GrammarStanding(SchoolStanding("A1", done = 3, of = 18, started = listOf(unit), next = unit, open = true), book = null, counting = null)
        compose.setContent { Column { GrammarCard(g, onSchool = {}, onUnit = {}, onPage = {}) } }
        compose.onNodeWithText("V teku · In progress", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText("Naslednja · Next", substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("▶ V teku · In progress:", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }
}
