package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "👁 Pokaži besedilo · Show the text" (companion/GAME.md, "Read first, then answer"): looking back at a text while its
 * questions are asked costs a little of the pay, less at the lower levels, never most of it.
 */
class PeeksTest {
    private fun costs(level: String, upTo: Int = 6) = (0..upTo).map { Peeks.cost(level, it) }

    @Test fun `at A1 the first two looks are free, the third costs a little, each after it as much`() {
        assertEquals(2, Peeks.free("A1"))
        assertEquals(listOf(0, 0, 0, 5, 10, 15, 20), costs("A1"))
    }

    @Test fun `higher up the first look is free and each next one costs a little more than the one before`() {
        for (l in listOf("A2", "B1", "B2", "C1", "C2")) assertEquals(l, 1, Peeks.free(l))
        assertEquals(listOf(0, 0, 5, 15, 30, 30, 30), costs("A2")) // 5, then 10, then 15 …
        assertEquals(listOf(0, 0, 10, 25, 30, 30, 30), costs("B1")) // 10, then 15 …
        assertEquals(listOf(0, 0, 10, 30, 30, 30, 30), costs("B2")) // 10, then 20 …
        assertEquals(costs("B2"), costs("C2"))
    }

    @Test fun `less at the lower levels, never more than a little of the pay`() {
        for (looks in 0..20) {
            val byLevel = listOf("A1", "A2", "B1", "B2").map { Peeks.cost(it, looks) }
            assertEquals("$looks looks: $byLevel", byLevel.sorted(), byLevel)
            assertTrue(byLevel.all { it in 0..Peeks.MAX_COST })
        }
        assertEquals(Peeks.MAX_COST, Peeks.cost("A1", 50))
    }

    @Test fun `a level it doesn't know yet counts as A1, the gentlest, and a level reads in any case`() {
        assertEquals(costs("A1"), costs(""))
        assertEquals(costs("A1"), costs("X9"))
        assertEquals(costs("B1"), costs(" b1 "))
        assertEquals(0, Peeks.cost("B2", -3))
    }

    @Test fun `the cut takes the percent off each resource, rounded, never all of it`() {
        val pay = mapOf(Res.WISDOM to 12, Res.FOOD to 1, Res.WOOD to 0)
        assertEquals(pay, Peeks.cut(pay, 0))
        assertEquals(mapOf(Res.WISDOM to 11, Res.FOOD to 1, Res.WOOD to 0), Peeks.cut(pay, 5)) // 11.4
        assertEquals(mapOf(Res.WISDOM to 8, Res.FOOD to 1, Res.WOOD to 0), Peeks.cut(pay, 30)) // 8.4; a 1 stays 1
        assertEquals(Peeks.cut(pay, Peeks.cost("A2", 3)), Peeks.pay(pay, "A2", 3))
    }
}
