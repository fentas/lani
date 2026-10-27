package si.lanisce.lani.ui.game

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.game.AdvanceCheck
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.AgeStep
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Res

/**
 * The ring's sheet (companion/GAME.md "The village's HUD"): what the next age needs at once, its count the ring's ("1/3",
 * no "Doba 3 / 6" or percentage beside it), ONE action for the next step at its top, every step with its own button, and
 * the switch for the row of cards at its foot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AgeSheetTest {
    @get:Rule val compose = createComposeRule()

    private val did = mutableListOf<String>()
    private val s = GameState(age = Age.ZASELEK, resources = mapOf(Res.WOOD to 198))
    private val check = AdvanceCheck(
        Age.VAS, false, listOf("+122 🪵", "300 besed"),
        listOf(
            AgeStep(AgeStep.Kind.BUILDING, "🏠", "Hiša · House", 1, 1),
            AgeStep(AgeStep.Kind.RESOURCE, "🪵", "Les · Wood", 198, 320, res = Res.WOOD),
            AgeStep(AgeStep.Kind.WORDS, "📚", "Besede · Words", 252, 300),
        ),
    )

    private fun text(t: String) = compose.onNode(hasText(t, substring = true))

    @Test fun `what the next age needs, its count as the ring's, one action, every step, the cards' switch`() {
        compose.setContent {
            AgeSheet(
                s, check, onAdvance = { did += "advance" }, onGather = { did += "gather:$it" }, onBuild = { did += "build" }, onLearn = { did += "learn" },
                onPolish = {}, onFriends = {}, onDismiss = {},
                next = "🪵 Nabiraj · Gather wood" to { did += "next" }, cards = true, onCards = { did += "cards:$it" },
            )
        }
        text("Naslednja doba").assertExists()
        text("1/3").assertExists()
        compose.onAllNodesWithText(" %", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Doba · Age", substring = true).assertCountEquals(0)
        text("🪵 Nabiraj · Gather wood").performClick()
        assertEquals(listOf("next"), did)
        // the step's own button too
        text("📚 Uči se").performClick()
        assertEquals("learn", did.last())
        text("Kartice pod vrstico · Cards under the bar").assertExists()
        compose.onNode(isToggleable()).performClick()
        assertEquals("cards:false", did.last())
    }

    @Test fun `once the village can advance, that is the one action`() {
        compose.setContent {
            AgeSheet(
                s, check.copy(ok = true, missing = emptyList()), onAdvance = { did += "advance" }, onGather = {}, onBuild = {}, onLearn = {},
                onPolish = {}, onFriends = {}, onDismiss = {}, next = "🪵 Nabiraj · Gather wood" to { did += "next" },
            )
        }
        text("✨ ").performClick()
        assertEquals(listOf("advance"), did)
        compose.onAllNodesWithText("Nabiraj · Gather wood", substring = true).assertCountEquals(0)
    }
}
