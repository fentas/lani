package si.lanisce.lani.ui.game

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.data.HudPrefs
import si.lanisce.lani.game.AdvanceCheck
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Res
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.Localized
import si.lanisce.lani.ui.theme.AlpineGreen

/**
 * The village's HUD and the navigation round (docs/plans/05-built/navigation.md): "🤝 Prijatelji · Friends" one tap from
 * the map with what waits there on its badge; the bar's "‹" says where it leads; the "Danes in kmalu" party row leads to
 * the party (it did nothing before: its action was lost on the way through the sheet's wrapper).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class VillageHudFriendsTest {
    @get:Rule val compose = createComposeRule()

    private val s = GameState(age = Age.ZASELEK, resources = mapOf(Res.FOOD to 100, Res.WOOD to 100, Res.STONE to 100, Res.WISDOM to 100))
    private val check = AdvanceCheck(Age.VAS, ok = false, missing = listOf("+1 🪵"))
    private val did = mutableListOf<String>()
    private val party = SoonLine(
        "party:ZASELEK", 0, false, "🎉", Localized { if (it == Lang.SL) "Praznik zaselka" else "The hamlet's party" },
        gold = true, news = true, action = SoonAction.Party(Age.ZASELEK),
    )

    private fun show(news: Int, back: String = "") {
        val hud = HudState(HudPrefs(ApplicationProvider.getApplicationContext()))
        compose.setContent {
            VillageHud(
                HudView(
                    s, GameEngine.attributes(s), check, goal = NextGoal.Grow(Age.VAS, check.missing, Res.WOOD),
                    goalAction = GoalAction("🪵", AlpineGreen) {}, chestNew = false, festival = null, soon = listOf(party),
                    friendsNews = news, backLabel = back,
                ),
                cards = true, section = hud.section,
                actions = HudActions(
                    back = { did += "back" }, resource = {}, age = {}, info = {}, fire = {}, chest = {}, projects = {}, progress = {},
                    goal = {}, festival = {}, arrival = {}, surprise = {}, step = {}, cards = {}, sheet = { hud.section = it },
                    party = { did += "party:$it" }, friends = { did += "friends" },
                ),
                moved = false, onRecenter = {}, reduced = true,
            )
        }
    }

    private fun node(d: String) = compose.onNodeWithContentDescription(d, substring = true)

    @Test
    fun `the friends one tap from the map, what waits there on the badge`() {
        show(news = 3, back = "‹ Domov · Home")
        val said = node("Prijatelji · Friends").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertTrue(said, said.contains("3 novice · 3 new"))
        compose.onNode(hasText("3"), useUnmergedTree = true).assertExists()
        node("Prijatelji · Friends").performClick()
        node("‹ Domov · Home").performClick()
        assertEquals(listOf("friends", "back"), did)
    }

    @Test
    fun `no badge while nothing waits`() {
        show(news = 0)
        val said = node("Prijatelji · Friends").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertEquals("Prijatelji · Friends", said)
        compose.onAllNodesWithContentDescription("novic", substring = true).assertCountEquals(0)
        node("Nazaj · Back").assertExists()
    }

    @Test
    fun `the party's row in today and what's coming leads to the party`() {
        show(news = 0)
        node("Danes in kmalu · Today and coming up: ").performClick()
        compose.onAllNodes(hasText("Praznik zaselka", substring = true))[1].performClick()
        assertEquals(listOf("party:ZASELEK"), did)
    }
}
