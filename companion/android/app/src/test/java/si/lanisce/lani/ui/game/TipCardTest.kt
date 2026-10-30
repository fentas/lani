package si.lanisce.lani.ui.game

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.Tip
import si.lanisce.lani.game.TipAction
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.scene.TownMarker
import si.lanisce.lani.game.scene.TownPlace
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/**
 * A tip's card (companion/GAME.md "Guidance"): "💡 Namig · Tip", the line (an adult's "target · base", a child's in the base
 * with the course language's word bold), ONE button that does it, "👍 Razumem · Got it", "⏰ Pozneje · Later", a scene's
 * tip with no button but "Razumem"; the map's bubble of it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class TipCardTest {
    @get:Rule val compose = createComposeRule()

    private val did = mutableListOf<String>()
    private val wisdom = Tip("full-wisdom", "📜", "tips.full.trade", "tips.full.trade.child", TipAction.Trade(Res.WISDOM), Res.WISDOM.names, mapOf("getEmoji" to "🪨"), TownPlace.Fire)

    @After fun back() {
        Cultures.use(Cultures.DEFAULT)
        L10n.pair = LangPair.DEFAULT
    }

    private fun show(tip: Tip, child: Boolean) = compose.setContent {
        TipCard(tip, child, voice = null, onAction = { did += "action" }, onGotIt = { did += "got it" }, onLater = { did += "later" })
    }

    @Test fun `an adult's card - the line in both languages, its one button, got it and later`() {
        show(wisdom, child = false)
        compose.onNodeWithText("💡 Namig · Tip").assertExists()
        compose.onNodeWithText("Skladišče je polno: Modrost", substring = true).assertExists()
        compose.onNodeWithText(" · Your store is full: Wisdom", substring = true).assertExists()
        compose.onNodeWithText("⇄ Zamenjaj · Trade").performClick()
        compose.onNodeWithText("👍 Razumem · Got it").performClick()
        compose.onNodeWithText("⏰ Pozneje · Later").performClick()
        assertEquals(listOf("action", "got it", "later"), did)
    }

    @Test fun `a child's card - the base with the course language's word, as Teo reads it`() {
        Cultures.use(Cultures.load("friuli"))
        L10n.pair = LangPair(Lang.IT, Lang.DE)
        show(wisdom, child = true)
        // the marks go, the word stays (bold)
        compose.onNodeWithText("Dein Lager ist voll: Saggezza! Tausch etwas gegen 🪨.").assertExists()
        compose.onAllNodesWithText("Il magazzino", substring = true).assertCountEquals(0)
        compose.onNodeWithText("⇄ Scambia · Tauschen").assertExists()
        compose.onNodeWithText("👍 Capito · Verstanden").assertExists()
    }

    @Test fun `a scene's tip has no button but got it`() {
        val inside = Tip("inside", "👆", "tips.inside", "tips.inside.child", TipAction.None)
        show(inside, child = false)
        compose.onNodeWithText("Tapni stvar in slišiš njeno besedo", substring = true).assertExists()
        compose.onAllNodesWithText("👍 Razumem · Got it").assertCountEquals(1)
    }

    @Test fun `on the map it is a tip's bubble at its thing, its label the line`() {
        val m = wisdom.marker(child = false)!!
        assertEquals("tip:full-wisdom", m.id)
        assertEquals(TownMarker.Kind.TIP, m.kind)
        assertEquals(TownPlace.Fire, m.place)
        assertEquals(true, m.label.startsWith("Skladišče je polno: Modrost"))
        // a Home tip has no place on the map
        assertEquals(null, Tip("reviews", "🔁", "tips.reviews", "tips.reviews.child", TipAction.Review).marker(child = false))
    }
}
