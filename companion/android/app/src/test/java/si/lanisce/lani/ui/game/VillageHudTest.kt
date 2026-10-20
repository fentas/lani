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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.Localized
import si.lanisce.lani.ui.theme.AlpineGreen

/**
 * The village's HUD on a phone's screen (companion/GAME.md, "The village's HUD"): the bar and its row of cards, the ring
 * showing and hiding them (the choice remembered on the phone, [HudPrefs]), each card's section in the sheet and what
 * leads on from it, the plot chooser hiding the cards ([HudState]), the dots, TalkBack's labels, and a tap or a drag
 * outside the open sheet ([outsideCloses]).
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
    private val wood = AgeStep(AgeStep.Kind.RESOURCE, "🪵", "Les · Wood", 198, 320, res = Res.WOOD)
    private val words = AgeStep(AgeStep.Kind.WORDS, "📚", "Besede · Words", 252, 300)
    private fun done() = AgeStep(AgeStep.Kind.BUILDING, "🏠", "Hiša · House", 1, 1)
    private val check = AdvanceCheck(Age.VAS, ok = false, missing = listOf("+122 🪵", "300 besed"), steps = List(7) { done() } + listOf(wood, words))
    private val did = mutableListOf<String>()
    private val story = SoonLine("teaser:STORY", 0, true, "⛰️", Localized { if (it == Lang.SL) "Janez pove zgodbo o Zlatorogu" else "Janez tells the story of Goldhorn" }, news = true)
    private val pedlar = SoonLine("teaser:PEDLAR", 4, false, "🎒", Localized { if (it == Lang.SL) "Krošnjar pride" else "The pedlar comes" })
    private val feast = SoonLine("festival:martinovo", 0, false, "🍷", Localized { if (it == Lang.SL) "Martinovo" else "St Martin's Day" }, gold = true, news = true, action = SoonAction.Festival("martinovo"))

    private fun view(chestNew: Boolean = false, soon: List<SoonLine> = listOf(story, pedlar), soonSeen: Set<String> = emptySet(), stepsSeen: Set<String> = emptySet()) = HudView(
        s, GameEngine.attributes(s), check,
        goal = NextGoal.Grow(Age.VAS, check.missing, Res.WOOD),
        goalAction = GoalAction("🪵 Nabiraj · Gather wood", AlpineGreen) { did += "gather" },
        chestNew = chestNew, festival = null, soon = soon, soonSeen = soonSeen, stepsSeen = stepsSeen,
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
            v, cards = hud.cards(chooser), section = hud.section,
            actions = HudActions(
                back = { did += "back" }, resource = { did += "res:$it" }, age = { did += "age" }, info = { did += "info" },
                fire = { did += "fire" }, chest = { did += "chest" }, projects = { did += "projects:$it" }, progress = { did += "progress" },
                goal = { did += "goal" }, festival = { did += "festival:$it" }, arrival = { did += "arrival:$it" }, surprise = { did += "surprise" },
                step = { did += "step:${it.kind}" }, cards = { hud.toggleCards(chooser) }, sheet = { hud.section = it },
            ),
            moved = false, onRecenter = {}, reduced = true,
        )
    }

    private fun node(description: String) = compose.onNodeWithContentDescription(description, substring = true)
    private fun gone(description: String) = compose.onAllNodesWithContentDescription(description, substring = true).assertCountEquals(0)
    private fun text(t: String) = compose.onNode(hasText(t, substring = true))
    private val ring = "Doba · Age: ${Age.ZASELEK.sl}"
    private val goalCard = "Naslednji cilj · Next goal: "
    private val soonCard = "Danes in kmalu · Today and coming up: "
    /** The row of cards swiped to card [i] (0 the goal's, 2 the settlement's: off the screen at first). */
    private fun cardAt(i: Int) = compose.onNodeWithTag(CARDS_TAG).performScrollToIndex(i)
    private val settlementCard = "Naselje · Settlement: "

    @Test fun `the bar and its three cards, each number with its name`() {
        show()
        node("Nazaj · Back").assertExists()
        node("Hrana · Food: 368").assertExists()
        node("Modrost · Wisdom: 482").assertExists()
        val said = node(ring).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertTrue(said, said.contains("še 2 koraka · 2 steps to go"))
        node(goalCard).assertExists()
        node(soonCard).assertExists()
        cardAt(2)
        node(settlementCard).assertExists()
        cardAt(0)
        // the goal card: the next step with its own action
        text("Les · Wood  198/320").assertExists()
        text("🪵 Nabiraj").performClick()
        assertEquals(listOf("step:RESOURCE"), did)
        // the bar and the cards are all it covers: under 140 dp (no status bar here)
        val bottom = node(goalCard).fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("the cards' bottom: $bottom px", bottom <= with(compose.density) { 140.dp.toPx() })
        node("Hrana · Food: 368").performClick()
        assertEquals(listOf("step:RESOURCE", "res:FOOD"), did)
    }

    @Test fun `the ring hides and shows the cards, and the phone remembers`() {
        show()
        node(ring).performClick()
        gone(goalCard)
        assertFalse("kept on the phone", HudPrefs(context).cardsShown)
        assertFalse("a new village screen starts without them", HudState(HudPrefs(context)).cards(null))
        node("Hrana · Food: 368").assertExists()
        node(ring).performTouchInput { longClick() }
        node(goalCard).assertExists()
        assertTrue(HudPrefs(context).cardsShown)
    }

    @Test fun `the goal card opens every open step with its button, and what leads on closes the sheet`() {
        show()
        node(goalCard).performClick()
        assertEquals(HudSection.GOAL, hud.section)
        compose.onNodeWithContentDescription("Naslednji cilj · Next goal").assertExists()
        text("Besede").assertExists()
        text("252/300").assertExists()
        text("📚 Uči se").performClick()
        assertEquals(listOf("step:WORDS"), did)
        assertEquals(null, hud.section)
        node(goalCard).performClick()
        node("Zapri · Close").performClick()
        assertEquals(null, hud.section)
    }

    @Test fun `the settlement's card opens its six tiles, each leading on`() {
        show(view(chestNew = true))
        cardAt(2)
        node(settlementCard).performClick()
        for (d in listOf("Vaščani · Villagers: 16", "Razpoloženje · Morale: 77", "Ognjišča · Hearths: ", "Pomoč · Help: 65", "Skrinja · The chest: ", "Skupni projekti · Village projects: ", "Napredek · Progress")) node(d).assertExists()
        text("Prebivalci").assertExists()
        text("Razpoloženje").assertExists()
        node("Skrinja · The chest: 0, nekaj novega · something new").performClick()
        assertEquals(listOf("chest"), did)
        assertEquals(null, hud.section)
        node(settlementCard).performClick()
        // the ring's label starts as the age's row does: the row is the second
        compose.onAllNodesWithContentDescription("Doba · Age: ${Age.ZASELEK.sl} · ${Age.ZASELEK.en}. ", substring = true)[1].performClick()
        node(settlementCard).performClick()
        node("Napredek · Progress").performClick()
        assertEquals(listOf("chest", "age", "progress"), did)
    }

    @Test fun `today and what's coming, a line each, today's lead on, the others show all of themselves`() {
        show(view(soon = listOf(feast, story, pedlar)))
        text("+2").assertExists()
        node(soonCard).performClick()
        text("Nocoj").assertExists()
        text(VillageHudLogic.whenOf(4, false, java.time.LocalDate.now()).first).assertExists()
        text("Krošnjar pride").assertExists()
        compose.onAllNodes(hasText("v torek", substring = true)).assertCountEquals(0)
        // the card shows it first too: the sheet's line is the second
        compose.onAllNodes(hasText("Martinovo", substring = true))[1].performClick()
        assertEquals(listOf("festival:martinovo"), did)
        assertEquals(null, hud.section)
    }

    @Test fun `a dot for what's new, on the ring while the cards are hidden, and TalkBack hears it`() {
        show(view(chestNew = true))
        val said = node(ring).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertTrue(said, said.endsWith("novo · new: Naslednji cilj · Next goal, Danes in kmalu · Today and coming up, Skrinja · The chest"))
    }

    @Test fun `the plot chooser hides the cards by itself and gives them back, the learner's choice untouched`() {
        show()
        chooser = 1
        compose.waitForIdle()
        gone(goalCard)
        node("Hrana · Food: 368").assertExists()
        node(ring).performClick()
        node(goalCard).assertExists()
        assertTrue("the ring tapped while choosing isn't a choice", prefs.cardsShown)
        chooser = 2 // the chooser opened again: hidden again
        compose.waitForIdle()
        gone(goalCard)
        chooser = null
        compose.waitForIdle()
        node(goalCard).assertExists()
    }

    @Test fun `a tap outside the open sheet only closes it, a drag closes it and goes on to the map`() {
        var open = true
        var closed = 0
        var taps = 0
        var dragged = 0f
        compose.setContent {
            Box(Modifier.size(300.dp).testTag("map").outsideCloses(active = { open }, inside = { p -> p.x < 100f && p.y < 100f }, onOutside = { closed++; open = false })) {
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
