package si.lanisce.lani.ui.game

import android.content.Context
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.data.HudPrefs
import si.lanisce.lani.game.AdvanceCheck
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.AgeStep
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.Teaser
import si.lanisce.lani.ui.theme.AlpineGreen

/**
 * The village's HUD on a phone's screen (companion/GAME.md, "The village's HUD"): the slim row, its panel, the pill; the
 * choice remembered on the phone ([HudPrefs]); the plot chooser minimizing it ([HudState]); the badges; TalkBack's labels
 * and actions; and a tap or a drag outside the open panel ([outsideCloses]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class VillageHudTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val s = GameState(
        age = Age.ZASELEK, fire = 80, morale = 77, villagers = 16, help = 65,
        resources = mapOf(Res.FOOD to 368, Res.WOOD to 17, Res.STONE to 315, Res.WISDOM to 482),
    )
    private fun step(have: Int) = AgeStep(AgeStep.Kind.RESOURCE, "🌾", "Hrana · Food", have, 5)
    private val check = AdvanceCheck(Age.VAS, ok = false, missing = listOf("+132 🌾"), steps = List(6) { step(5) } + List(4) { step(1) })
    private val did = mutableListOf<String>()

    private fun view(state: GameState = s, chestNew: Boolean = false, stepToday: Boolean = false) = HudView(
        state, GameEngine.attributes(state), check,
        goal = NextGoal.Grow(Age.VAS, listOf("+132 🌾", "+303 🪵"), Res.WOOD),
        goalAction = GoalAction("🪵 Nabiraj · Gather wood", AlpineGreen) { did += "gather" },
        chestNew = chestNew, stepToday = stepToday, festival = null,
        teasers = listOf(Teaser(Teaser.Kind.STORY, "💃", "Nocoj ob ognju: Janez pove zgodbo · Tonight by the fire: Janez tells a story")),
        newsSeen = emptySet(),
    )

    private lateinit var prefs: HudPrefs
    private lateinit var hud: HudState
    /** The plot chooser that is on (a number for each opening), as the village screen counts them; null: none. */
    private var chooser by mutableStateOf<Int?>(null)

    @Before fun fresh() {
        context.getSharedPreferences("village_hud", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = HudPrefs(context)
        hud = HudState(prefs)
    }

    /** The HUD as the village screen wires it, its actions noted in [did]. */
    private fun show(v: HudView = view()) = compose.setContent {
        VillageHud(
            v, hud.mode(chooser),
            HudActions(
                back = { did += "back" }, resource = { did += "res:$it" }, age = { did += "age" }, info = { did += "info" },
                fire = { did += "fire" }, chest = { did += "chest" }, projects = { did += "projects" }, progress = { did += "progress" },
                goal = { did += "goal" }, festival = { did += "festival:$it" }, news = { did += "news" },
                expand = { hud.open = it }, minimize = { hud.minimize(it, chooser) },
            ),
            moved = false, onRecenter = {}, reduced = true,
        )
    }

    private fun node(description: String) = compose.onNodeWithContentDescription(description, substring = true)
    private fun gone(description: String) = compose.onAllNodesWithContentDescription(description, substring = true).assertCountEquals(0)
    private val more = "Pokaži več · Show more"
    private val hide = "Skrij vrstico · Hide the bar"

    @Test fun `collapsed it is one slim row, the resources and the status, nothing of the panel`() {
        show()
        node("Nazaj · Back").assertExists()
        node("Hrana · Food: 368").assertExists()
        node("Modrost · Wisdom: 482").assertExists()
        val status = node("Doba · Age: ${Age.ZASELEK.sl}").fetchSemanticsNode()
        val said = status.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single()
        assertTrue(said, said.contains("še 4 koraki · 4 steps to go; Novice · News"))
        assertEquals(HudMode.COLLAPSED, hud.mode(chooser))
        gone("Vaščani · Villagers")
        gone("Skrinja · The chest")
        compose.onAllNodes(hasText(hide, substring = true)).assertCountEquals(0)
        // the row is all it covers: under 60 dp tall (no status bar here), where the old HUD took four rows
        val bottom = node(more).fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("the row's bottom: $bottom px", bottom <= with(compose.density) { 60.dp.toPx() })
        node("Hrana · Food: 368").performClick()
        assertEquals(listOf("res:FOOD"), did)
    }

    @Test fun `the chevron opens the panel with everything else, each leading on and closing it`() {
        show()
        node(more).performClick()
        assertEquals(HudMode.EXPANDED, hud.mode(chooser))
        node("Pokaži manj · Show less").assertExists()
        for (d in listOf("Vaščani · Villagers: 16", "Razpoloženje · Morale: 77", "Ogenj · Fire: 80", "Skrinja · The chest", "Skupni projekti · Village projects", "Napredek · Progress")) node(d).assertExists()
        compose.onNode(hasText("Vas: še 2 koraka", substring = true)).assertExists()
        compose.onNode(hasText("☐ +303 🪵")).assertExists()
        node("Skrinja · The chest").performClick()
        assertEquals(listOf("chest"), did)
        assertEquals(HudMode.COLLAPSED, hud.mode(chooser))
        gone("Vaščani · Villagers")

        // the status chip toggles it too; its goal's action and the news lead on
        node("Doba · Age: ${Age.ZASELEK.sl}").performClick()
        compose.onNode(hasText("Nabiraj · Gather wood", substring = true)).performClick()
        node(more).performClick()
        compose.onNode(hasText("Tonight by the fire", substring = true)).performClick()
        node(more).performClick()
        compose.onNode(hasText("Vas: še 2 koraka", substring = true)).performClick()
        assertEquals(listOf("chest", "gather", "news", "goal"), did)
        assertEquals(HudMode.COLLAPSED, hud.mode(chooser))
    }

    @Test fun `minimized it is a pill, and the choice is remembered on the phone`() {
        show()
        node(more).performClick()
        compose.onNode(hasText(hide, substring = true)).performClick()
        assertEquals(HudMode.MINIMIZED, hud.mode(chooser))
        gone("Hrana · Food")
        node("Pokaži vrstico · Show the bar. Doba · Age").assertExists()
        assertTrue("kept on the phone", HudPrefs(context).minimized)
        assertEquals("a new village screen starts minimized", HudMode.MINIMIZED, HudState(HudPrefs(context)).mode(null))

        node("Pokaži vrstico · Show the bar").performClick()
        assertEquals(HudMode.COLLAPSED, hud.mode(chooser))
        node("Hrana · Food: 368").assertExists()
        assertFalse(HudPrefs(context).minimized)
    }

    @Test fun `a long press on the status chip, or TalkBack's action, minimizes it`() {
        show()
        node("Doba · Age: ${Age.ZASELEK.sl}").performTouchInput { longClick() }
        assertEquals(HudMode.MINIMIZED, hud.mode(chooser))
        node("Pokaži vrstico · Show the bar").performClick()
        val chevron = node(more).fetchSemanticsNode()
        val action = chevron.config[SemanticsActions.CustomActions].single { it.label == hide }
        compose.runOnIdle { action.action() }
        assertEquals(HudMode.MINIMIZED, hud.mode(chooser))
        assertTrue(prefs.minimized)
    }

    @Test fun `the plot chooser minimizes it by itself and gives it back, the learner's choice untouched`() {
        show()
        chooser = 1
        compose.waitForIdle()
        gone("Hrana · Food")
        node("Pokaži vrstico · Show the bar").performClick()
        node("Hrana · Food: 368").assertExists()
        assertFalse("the pill tapped while choosing isn't a choice", prefs.minimized)
        chooser = 2 // the chooser opened again: minimized again
        compose.waitForIdle()
        gone("Hrana · Food")
        chooser = null
        compose.waitForIdle()
        node("Hrana · Food: 368").assertExists()
        assertEquals(HudMode.COLLAPSED, hud.mode(chooser))

        // minimized by the learner: still so after the chooser
        hud.minimize(true, null)
        chooser = 3
        compose.waitForIdle()
        chooser = null
        compose.waitForIdle()
        assertEquals(HudMode.MINIMIZED, hud.mode(chooser))
    }

    @Test fun `the badges ride on the status chip and the pill, and TalkBack reads them`() {
        show(view(state = s.copy(fire = 20), chestNew = true, stepToday = true))
        val status = node("Doba · Age: ${Age.ZASELEK.sl}").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single()
        for (b in listOf("Ogenj · Fire: 20, ugaša · dying", "Skrinja · The chest: nekaj novega · something new", "danes lahko narediš korak projekta · a project step can be taken today", "Novice · News")) {
            assertTrue("$b in «$status»", b in status)
        }
        hud.minimize(true, null)
        compose.waitForIdle()
        node("Pokaži vrstico · Show the bar. Doba · Age").assertExists()
        node("Skrinja · The chest: nekaj novega").assertExists()
    }

    @Test fun `a tap outside the open panel only closes it, a drag closes it and goes on to the map`() {
        var open = true
        var closed = 0
        var taps = 0
        var dragged = 0f
        compose.setContent {
            Box(Modifier.size(300.dp).testTag("map").outsideCloses(active = { open }, inside = { Rect(0f, 0f, 100f, 100f) }, onOutside = { closed++; open = false })) {
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures(onTap = { taps++ }) }
                        .pointerInput(Unit) { detectDragGestures { _, d -> dragged += d.x } },
                )
            }
        }
        val map = compose.onNodeWithTag("map")
        map.performTouchInput { click(Offset(200f, 200f)) }
        assertEquals("closed", 1, closed)
        assertEquals("and nothing under it tapped", 0, taps)

        open = true
        map.performTouchInput { swipe(Offset(200f, 200f), Offset(400f, 220f), 300) }
        assertEquals(2, closed)
        assertTrue("the drag went on: $dragged", dragged > 50f)

        map.performTouchInput { click(Offset(200f, 200f)) }
        assertEquals("closed, a tap is the map's", 1, taps)
        open = true
        map.performTouchInput { click(Offset(50f, 50f)) }
        assertEquals("inside the HUD: not outside", 2, closed)
        assertEquals(2, taps)
    }

}
