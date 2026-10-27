package si.lanisce.lani.ui.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.data.PackWord
import si.lanisce.lani.game.AdvanceCheck
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.NeedWord
import si.lanisce.lani.game.Rebuild
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.UpgradeWords
import si.lanisce.lani.game.WordState
import si.lanisce.lani.game.scene.SceneSpec

/**
 * A building's card (companion/GAME.md, "The building's card"): one card, the next step first. Going in first (none where
 * the culture has no scene for it), the upgrade's ONE button for its next step (the words, then the price, then
 * "Nadgradi"), the words and the moba folded, "…" while the words' packs come, moving at its foot and replacing or taking
 * down folded (taking down asks first), the repair there when damaged; none of the old sheet's build date, "V dobrem
 * stanju" or disabled buttons.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class BuildingCardTest {
    @get:Rule val compose = createComposeRule()

    private val did = mutableListOf<String>()
    private val tent = Building("t1", BuildingType.TENT, 0, level = 2, builtAt = 0)
    private val s = GameState(
        age = Age.ZASELEK, villagers = 4,
        resources = mapOf(Res.FOOD to 400, Res.WOOD to 300, Res.STONE to 200, Res.WISDOM to 100),
        buildings = listOf(Building("pond", BuildingType.TENT, NO_PLOT), tent, Building("t2", BuildingType.TENT, 1), Building("f", BuildingType.FIELD, 2)),
    )
    private val check = AdvanceCheck(Age.VAS, false, listOf("x"))
    private val tentScene = SceneSpec(id = "v-sotoru", title = "V šotoru · In the tent", art = "tent", from = listOf("tent"))

    /** [n] words of the tent, [known] of them known (the rest new, each with its pack word to teach). */
    private fun words(n: Int, known: Int, need: Int = 14) = UpgradeWords(
        "t1", 3,
        List(n) { i ->
            NeedWord("v-sotoru", "w$i", "beseda$i", "word$i", null, if (i < known) WordState.KNOWN else WordState.NEW, learn = PackWord("w$i", sl = "beseda$i", en = "word$i"))
        },
        need, "⛺ V šotoru · In the tent",
    )

    private val actions = BuildingCardActions(
        onClose = { did += "close" }, onScene = { did += "scene:${it.id}" }, onMarker = { did += "marker" },
        onUpgraded = { did += "upgraded" }, onPractise = { did += "practise" }, onAges = { did += "ages" },
        onGather = { did += "gather:$it" }, onSchool = { did += "school" }, onMove = { did += "move" },
        onReplace = { did += "replace" }, onTakeDown = { did += "takeDown" },
    )
    private val run = BuildingRun(upgrade = { did += "upgrade:$it" }, practise = { did += "run" }, repair = { did += "repair:$it" }, grow = { did += "grow" })

    private fun show(state: GameState = s, b: Building = tent, w: UpgradeWords? = words(14, 0), ready: Boolean = true, scenes: List<SceneSpec> = listOf(tentScene), a: BuildingCardActions = actions) {
        val up = GameEngine.upgradeOption(state, b.id, w)
        val view = BuildingView(
            state, b, up, BuildingCardLogic.step(state, b, up, ready), check,
            grow = Rebuild.growOption(state, b.id), repairMoba = null, takeDown = a.onTakeDown?.let { TakeDownView.of(state, b) },
        )
        // over the map its column scrolls within the card
        compose.setContent { Column(Modifier.verticalScroll(rememberScrollState())) { BuildingCardBody(view, a, run, scenes, emptyList(), close = true) } }
    }

    private fun text(t: String) = compose.onNode(hasText(t, substring = true))
    private fun none(t: String) = compose.onAllNodesWithText(t, substring = true).assertCountEquals(0)

    @Test fun `one card - going in first, what it gives once, the next step as one button, the rest folded`() {
        show()
        text("Šotor · Tent").assertExists()
        text("Stopnja · Level 2 · +3 👥").assertExists()
        text("🚪 Vstopi · Go in: ").performClick()
        assertEquals(listOf("scene:v-sotoru"), did)
        text("⬆️ Za stopnjo 3 · For level 3: +4 👥").assertExists()
        text("🔤 0 / 14").assertExists()
        // the one button: its words first, as many as a run takes
        text("🎯 Nauči se 8 besed · Learn 8 words").performScrollTo().performClick()
        assertEquals("run", did.last())
        // no "Upgrade" while the words are missing, nothing disabled and grey, no build date, no "in good shape"
        none("Nadgradi · Upgrade")
        none("V dobrem stanju")
        none("1. 1.")
        // folded, the first two words its peek
        text("Katere besede · Which words").assertExists()
        text("beseda0, beseda1 … +12").assertExists()
        none("beseda5")
        text("Katere besede · Which words").performScrollTo().performClick()
        text("beseda5").assertExists()
        // the tent grows into a hut, its level kept
        text("Postane: Koča · Becomes: Hut").performScrollTo()
        text("Naj zraste · Let it grow").performScrollTo().performClick()
        assertEquals("grow", did.last())
        text("↔ Premakni (brezplačno) · Move (free)").performScrollTo().performClick()
        assertEquals("move", did.last())
    }

    @Test fun `no scene for it, no going in - and nothing takes its place`() {
        show(scenes = emptyList())
        none("Vstopi")
        text("Šotor · Tent").assertExists()
    }

    @Test fun `the words count waits for their packs instead of jumping`() {
        show(ready = false)
        text("🔤 …").assertExists()
        none("0 / 14")
        none("Nauči se")
    }

    @Test fun `the words known, the button gathers what the price lacks most, then upgrades`() {
        val poor = s.copy(resources = mapOf(Res.FOOD to 10, Res.WOOD to 10))
        show(poor, w = words(14, 14))
        // tent to level 3: 75 🌾 45 🪵: 65 🌾 short, 35 🪵 short
        text("🌾 Naberi še 65 🌾 · Gather 65 more 🌾").performScrollTo().performClick()
        assertEquals("gather:FOOD", did.last())
    }

    @Test fun `everything there, the button upgrades`() {
        show(w = words(14, 14))
        text("🔤 14 / 14 ✓").assertExists()
        text("⬆️ Nadgradi · Upgrade").performScrollTo().performClick()
        assertEquals("upgrade:false", did.last())
    }

    @Test fun `the next level waits for the age - one tap to what the next age needs`() {
        show(s.copy(age = Age.TABOR), b = tent.copy(level = 2), w = null)
        text("🔒 Najprej: Zaselek · First: Hamlet").performScrollTo().performClick()
        assertEquals("ages", did.last())
    }

    @Test fun `damaged - nothing to upgrade until the repair at its foot`() {
        val broken = tent.copy(damaged = true)
        show(s.copy(buildings = s.buildings.map { if (it.id == "t1") broken else it }), b = broken)
        text("Poškodovano · Damaged").assertExists()
        none("Nauči se")
        text("🛠️ Popravi · Repair").performScrollTo().performClick()
        assertEquals("repair:false", did.last())
    }

    @Test fun `replacing and taking down are folded, and taking down asks first, saying what comes back`() {
        show(b = s.buildings.single { it.id == "t2" })
        none("🔨 Podri · Take down")
        text("Nadomesti ali podri · Replace or take down").performScrollTo().performClick()
        text("🔁 Nadomesti · Replace").performScrollTo().performClick()
        assertEquals("replace", did.last())
        text("🔨 Podri · Take down").performScrollTo().performClick()
        // the confirmation: what is lost, what comes back (half the tent: 12 🌾 7 🪵), where the people sleep
        text("Podreš: Šotor?").assertExists()
        text("12 🌾").assertExists()
        assertTrue("not taken down before the confirmation", "takeDown" !in did)
        // the dialog's own "Podri" under the card's
        val podri = compose.onAllNodesWithText("🔨 Podri · Take down", substring = true)
        assertEquals(2, podri.fetchSemanticsNodes().size)
        podri[1].performClick()
        assertEquals("takeDown", did.last())
    }
}
