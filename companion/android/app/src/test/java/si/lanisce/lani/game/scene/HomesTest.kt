package si.lanisce.lani.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.Building
import si.lanisce.lani.game.BuildingType
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.ui.game.household

/**
 * Houses and their rooms (companion/SCENES.md, "Houses and their rooms"): which house opens which room, the same one every
 * time; the hut keeps the kitchen; a room is seen in its own house, at that house's level.
 */
class HomesTest {
    private fun room(id: String, art: String, vararg household: String, from: List<String> = listOf("house")) =
        SceneSpec(id = id, title = id, art = art, from = from, household = household.toList())

    private val kitchen = room("kuhinja", "kitchen", from = listOf("hut", "house"))
    private val living = room("v-hisi", "livingroom", "janez")
    private val workshop = room("v-delavnici", "workshop", "france", "tine")
    private val cellar = room("v-kleti", "cellar", "marko")
    private val attic = room("v-podstresju", "attic", "vida", "nejc")
    private val smithy = SceneSpec(id = "v-kovacnici", title = "V kovačnici", art = "smithy", from = listOf("smithy"))
    private val scenes = listOf(kitchen, living, workshop, cellar, attic, smithy)

    private fun house(id: String, builtAt: Long, level: Int = 1) = Building(id, BuildingType.HOUSE, builtAt.toInt(), level = level, builtAt = builtAt)
    private fun people(vararg ids: String) = ids.map { Resident(it, "2026-09-01") }

    private val village = GameState(
        seed = 5, age = Age.VAS,
        buildings = listOf(Building("hut", BuildingType.HUT, 0), Building("s", BuildingType.SMITHY, 1), house("h1", 10), house("h2", 20, level = 2), house("h3", 30, level = 3)),
        // in the order they moved in: Micka first, then Janez, France and Tine, then Marko; Vida and Nejc not yet
        residents = people("micka", "luka", "zala", "janez", "france", "tine", "marko"),
    )

    private fun dealt(state: GameState, scenes: List<SceneSpec> = this.scenes) = Homes.deal(scenes, state).mapValues { it.value.id }

    @Test fun `the houses open the rooms of who lives there, in the order they moved in`() {
        assertEquals(mapOf("h1" to "v-hisi", "h2" to "v-delavnici", "h3" to "v-kleti"), dealt(village))
        // the same every time, whatever order the buildings are kept in
        assertEquals(dealt(village), dealt(village.copy(buildings = village.buildings.reversed())))
        assertEquals(dealt(village), dealt(village, scenes.reversed()))
    }

    @Test fun `the hut keeps the kitchen, and a house gets it only after the households' rooms`() {
        assertEquals(listOf("kuhinja"), Homes.scenesOf(village.buildings.first { it.id == "hut" }, scenes, village).map { it.id })
        val four = village.copy(buildings = village.buildings + house("h4", 40))
        assertEquals("kuhinja", dealt(four)["h4"])
        // without a hut, the kitchen is anyone's: after the rooms whose people live here
        val noHut = four.copy(buildings = four.buildings.filter { it.type != BuildingType.HUT })
        assertEquals(mapOf("h1" to "v-hisi", "h2" to "v-delavnici", "h3" to "v-kleti", "h4" to "kuhinja"), dealt(noHut))
    }

    @Test fun `a room whose people don't live here is in no house, until they move in`() {
        assertFalse("v-podstresju" in dealt(village).values)
        assertFalse(Homes.placed(attic, village, Homes.deal(scenes, village)))
        assertTrue(Homes.placed(cellar, village, Homes.deal(scenes, village)))
        assertTrue("the kitchen is the hut's", Homes.placed(kitchen, village, Homes.deal(scenes, village)))
        // Vida and Nejc move in, and a new house is built: theirs; the old houses keep their rooms
        val moved = village.copy(buildings = village.buildings + house("h4", 40), residents = village.residents + people("vida", "nejc"))
        assertEquals(mapOf("h1" to "v-hisi", "h2" to "v-delavnici", "h3" to "v-kleti", "h4" to "v-podstresju"), dealt(moved))
    }

    @Test fun `a house left over opens one of the rooms by its id, the same one every time`() {
        val many = village.copy(buildings = village.buildings + (4..9).map { house("h$it", it * 10L) }, residents = village.residents + people("vida", "nejc"))
        val d = dealt(many)
        assertEquals(9, d.size)
        assertEquals(listOf("v-hisi", "v-delavnici", "v-kleti", "v-podstresju", "kuhinja"), (1..5).map { d.getValue("h$it") })
        for (i in 6..9) assertTrue(d.getValue("h$i") in setOf("v-hisi", "v-delavnici", "v-kleti", "v-podstresju", "kuhinja"))
        assertEquals(d, dealt(many.copy(buildings = many.buildings.shuffled(java.util.Random(4)))))
    }

    @Test fun `a house opens its own room, any other building its scenes`() {
        val h2 = village.buildings.first { it.id == "h2" }
        assertEquals(listOf("v-delavnici"), Homes.scenesOf(h2, scenes, village).map { it.id })
        assertEquals(listOf("v-kovacnici"), Homes.scenesOf(village.buildings.first { it.id == "s" }, scenes, village).map { it.id })
        // nobody of any room's household lives here yet: no room at all
        val empty = village.copy(residents = people("luka"))
        assertEquals(emptyList<String>(), Homes.scenesOf(h2, scenes.filter { it.id != "kuhinja" }, empty).map { it.id })
        // without the cast's names (an older bridge), every room is lived in, in the scenes' order
        assertEquals("v-hisi", dealt(village.copy(residents = emptyList()))["h1"])
    }

    @Test fun `a house's card names who lives there`() {
        val people = listOf(ScenePerson("france", "Mlinar France", "🧑‍🌾", "farmer", "bench", villager = "france"), ScenePerson("tine", "Tine", "👦", "child3", "floor", villager = "tine"))
        assertEquals(" · Mlinar France, Tine", household(listOf(workshop.copy(people = people))))
        assertEquals("the kitchen is anyone's", "", household(listOf(kitchen)))
        assertEquals("", household(listOf(smithy)))
        assertEquals("", household(emptyList()))
    }

    @Test fun `a room is seen in its own house, at that house's level`() {
        assertEquals("h2", Homes.buildingOf(workshop, village, dealt = Homes.deal(scenes, village))?.id)
        assertEquals(2, Homes.world(workshop, village, scenes = scenes).hereLevel)
        assertEquals(3, Homes.world(cellar, village, scenes = scenes).hereLevel)
        assertEquals("opened from its house", 3, Homes.world(cellar, village, "h3", scenes).hereLevel)
        // the kitchen from the hut, or from a house it is in
        assertEquals("hut", Homes.buildingOf(kitchen, village, dealt = Homes.deal(scenes, village))?.id)
        assertNull("a room in no house is seen in none", Homes.buildingOf(attic, village, dealt = Homes.deal(scenes, village)))
    }
}
