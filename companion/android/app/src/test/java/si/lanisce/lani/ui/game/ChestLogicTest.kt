package si.lanisce.lani.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Inventory
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Villager
import java.time.LocalDate

class ChestLogicTest {
    private val today = LocalDate.of(2026, 9, 25)
    private val zala = Villager("zala", "Zala", "👧", "child2", voice = "female")
    private val nejc = Villager("nejc", "Nejc", "👦", "child1", voice = "male")
    private val luka = Villager("luka", "Pastir Luka", "🐑", "shepherd", voice = "male")

    @Test fun `a good goes to who lives here and likes it, once a day each`() {
        val s = GameState(chest = Inventory(goods = mapOf("potica" to 2), given = mapOf("nejc" to today.toString())))
        val likers = ChestLogic.likers(s, "potica", listOf(zala, nejc, luka), today)
        assertEquals(listOf("Zala", "Nejc"), likers.map { it.name }) // Luka likes jota, bread, apples and socks
        assertNull(likers[0].blocked)
        assertTrue(likers[1].blocked!!.startsWith("Danes"))
    }

    @Test fun `what a moba is like here`() {
        assertTrue(ChestLogic.mobaText(GameState(villagers = 0)).startsWith("Še ni sosedov"))
        val three = GameState(residents = listOf(Resident("micka", "2026-09-01"), Resident("luka", "2026-09-01"), Resident("zala", "2026-09-01")))
        assertEquals("3 sosedje · 3 neighbours: do 30 % 🪵 🪨 · up to 30 % of the wood and stone; 1 🤝 = 5 🪵/🪨", ChestLogic.mobaText(three))
    }
}
