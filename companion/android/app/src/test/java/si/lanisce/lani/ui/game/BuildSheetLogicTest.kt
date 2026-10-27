package si.lanisce.lani.ui.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameEngine
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.NO_PLOT
import si.lanisce.lani.game.PlotWay
import si.lanisce.lani.game.Rebuild
import si.lanisce.lani.game.Res
import si.lanisce.lani.game.UpgradeOption
import si.lanisce.lani.game.culture.Culture
import si.lanisce.lani.game.culture.Cultures

/**
 * "🔨 Gradi · Build" shows only what can be built now or has a way on, what comes later in one quiet line (companion/GAME.md,
 * "The build sheet"); and a building's card's one button takes the upgrade's next step in its order ("The building's card").
 */
class BuildSheetLogicTest {
    private lateinit var was: Culture

    @Before fun setUp() {
        was = Cultures.home
        Cultures.use(Cultures.DEFAULT)
    }

    @After fun tearDown() { Cultures.use(was) }

    private fun b(id: String, t: BuildingType, plot: Int, level: Int = 1) = Building(id, t, plot, level)
    private fun plenty(n: Int = 5000) = Res.entries.associateWith { n }

    @Test fun `only what can be built now, the next age's in one quiet line`() {
        val camp = GameState(age = Age.TABOR, resources = plenty(), buildings = listOf(b("t", BuildingType.TENT, 0), b("p", BuildingType.PALISADE, NO_PLOT)))
        val list = BuildSheetLogic.of(camp, GameEngine.buildOptions(camp))
        val types = list.cards.map { it.type }.toSet()
        // the camp's: no palisade (one stands), nothing of the hamlet's
        assertEquals(setOf(BuildingType.TENT, BuildingType.FIELD, BuildingType.WELL, BuildingType.HUT, BuildingType.KOZOLEC), types)
        assertEquals("🔒 Pozneje (Zaselek): čebelnjak, stražni stolp, kovačnica … · Later (Hamlet): bee house, watchtower, smithy … 🛖", list.later)
        assertNull(list.kept)
        assertFalse(list.full)
        // what can be built comes first
        assertTrue(list.cards.first().available)
    }

    @Test fun `the plots kept for the next age are a line, not cards that can't be built`() {
        // a hamlet with one free plot left, Vas still needing the church, the linden and two dwellings
        val hamlet = GameState(age = Age.ZASELEK, resources = plenty(), buildings = (0 until 9).map { b("f$it", if (it < 4) BuildingType.FIELD else BuildingType.KOZOLEC, it) })
        val list = BuildSheetLogic.of(hamlet, GameEngine.buildOptions(hamlet))
        assertTrue(list.cards.map { it.type }.toSet().containsAll(setOf(BuildingType.CHURCH, BuildingType.LIPA, BuildingType.HUT, BuildingType.HOUSE)))
        assertFalse(list.cards.any { it.type == BuildingType.BEEHIVE || it.type == BuildingType.WELL })
        assertTrue(list.kept!!, list.kept!!.startsWith("🅿️ Prostor ostane za: "))
    }

    @Test fun `every plot built, the cards that have a way through the plots`() {
        val full = GameState(
            age = Age.TABOR, resources = plenty(), villagers = 4,
            buildings = listOf(b("t", BuildingType.TENT, 0), b("t2", BuildingType.TENT, 1), b("f", BuildingType.FIELD, 2), b("k", BuildingType.KOZOLEC, 3), b("w", BuildingType.WELL, 4)),
        )
        val list = BuildSheetLogic.of(full, GameEngine.buildOptions(full))
        assertTrue(list.full)
        // the hut: the second tent grows into it (the first stays a tent until the shepherd moves it)
        assertEquals(PlotWay.Grow("t2", BuildingType.TENT, BuildingType.HUT), list.cards.single { it.type == BuildingType.HUT }.plotWay)
        // the palisade takes no plot: as ever
        assertTrue(list.cards.any { it.type == BuildingType.PALISADE && it.available })
        // every card shown has a way on, or takes no plot
        assertTrue(list.cards.all { !it.type.onPlot || it.plotWay != null })
        // nothing comes down for what the next age doesn't need (a house for a tent): no tent card here
        assertTrue(list.cards.none { it.type == BuildingType.TENT })
    }

    @Test fun `what the next age needs comes first`() {
        val hamlet = GameState(age = Age.ZASELEK, resources = plenty(), buildings = listOf(b("t", BuildingType.TENT, 0), b("f", BuildingType.FIELD, 1)))
        val check = GameEngine.advanceCheck(hamlet, 0)
        val list = BuildSheetLogic.of(hamlet, GameEngine.buildOptions(hamlet), check = check)
        // Vas needs the church, the linden and two huts or houses: those cards lead
        assertEquals(
            setOf(BuildingType.CHURCH, BuildingType.LIPA, BuildingType.HUT, BuildingType.HOUSE),
            list.cards.take(4).map { it.type }.toSet(),
        )
    }

    @Test fun `replacing a building, what can go up on its plot as if it stood free`() {
        val full = GameState(
            age = Age.TABOR, resources = plenty(), villagers = 4,
            buildings = listOf(b("t", BuildingType.TENT, 0), b("t2", BuildingType.TENT, 1), b("f", BuildingType.FIELD, 2), b("k", BuildingType.KOZOLEC, 3), b("w", BuildingType.WELL, 4)),
        )
        val choices = Rebuild.replaceOptions(full, "k")
        val list = BuildSheetLogic.of(full.copy(buildings = full.buildings.filter { it.id != "k" }), choices, replacing = true)
        assertNull(list.later)
        assertFalse(list.full)
        // Zaselek needs a field (one stands) and the palisade (on no plot): a field, a hut, a tent … can go there
        assertTrue(list.cards.none { it.type == BuildingType.KOZOLEC })
        assertTrue(list.cards.any { it.type == BuildingType.HUT && it.available })
    }

    @Test fun `the card's one button - the age, then the words, the course, the price, then the upgrade`() {
        val tent = b("t", BuildingType.TENT, 0, level = 2)
        val s = GameState(age = Age.TABOR, resources = plenty(), buildings = listOf(tent))
        fun up(cost: Map<Res, Int> = mapOf(Res.FOOD to 75), words: si.lanisce.lani.game.UpgradeWords? = null) =
            UpgradeOption(3, cost, true, null, "+4 👥", words = words)
        assertEquals(UpgradeStep.Later(Age.ZASELEK), BuildingCardLogic.step(s, tent, up(), true))
        val hamlet = s.copy(age = Age.ZASELEK)
        assertEquals(UpgradeStep.Loading, BuildingCardLogic.step(hamlet, tent, up(), false))
        assertEquals(UpgradeStep.Ready, BuildingCardLogic.step(hamlet, tent, up(), true))
        assertEquals(UpgradeStep.Gather(Res.STONE, 4000), BuildingCardLogic.step(hamlet, tent, up(mapOf(Res.STONE to 9000)), true))
        assertEquals(UpgradeStep.Top, BuildingCardLogic.step(hamlet, tent, null, true))
        assertEquals(UpgradeStep.Damaged, BuildingCardLogic.step(hamlet, tent.copy(damaged = true), up(), true))
        val gate = si.lanisce.lani.game.CourseGate("t", 3, "A2", 2)
        assertEquals(UpgradeStep.Course(gate), BuildingCardLogic.step(hamlet, tent, up().copy(course = gate), true))
        assertEquals("Stopnja · Level 2 · +3 👥", BuildingCardLogic.sub(tent))
        assertEquals("⬆️ Nadgradi · Upgrade", BuildingCardLogic.label(UpgradeStep.Ready))
        assertNull(BuildingCardLogic.label(UpgradeStep.Top))
    }
}
