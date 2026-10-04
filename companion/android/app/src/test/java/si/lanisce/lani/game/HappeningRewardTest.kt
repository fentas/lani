package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HappeningRewardTest {
    private val day = LocalDate.of(2026, 9, 24)
    private val now = Fixtures.noon(day)
    private val village = GameState(seed = 1, age = Age.TABOR, resources = mapOf(Res.FOOD to 10, Res.WOOD to 10))
    private val entry = "👵" to "Babica Micka: Babica kuha juho · Grandma is cooking soup"
    private val soup = mapOf(Res.FOOD to 16, Res.WISDOM to 4)

    @Test fun `a happening pays once per day and is written to the chronicle`() {
        val (s1, paid) = GameEngine.completeHappening(village, "ob-ognju/juha", soup, 0, day, now, entry)
        assertEquals(soup, paid)
        assertEquals(26, s1.res(Res.FOOD))
        assertEquals(4, s1.res(Res.WISDOM))
        assertEquals("2026-09-24", s1.happeningsDone["ob-ognju/juha"])
        assertEquals(LogEntry(now, "👵", entry.second), s1.log.last())
        assertEquals(16, s1.stats.totalEarned[Res.FOOD])

        val (s2, again) = GameEngine.completeHappening(s1, "ob-ognju/juha", soup, 0, day, now + 1000, entry)
        assertTrue(again.isEmpty())
        assertSame(s1, s2)

        val (s3, tomorrow) = GameEngine.completeHappening(s1, "ob-ognju/juha", soup, 0, day.plusDays(1), now, entry)
        assertEquals(soup, tomorrow)
        assertEquals(mapOf("ob-ognju/juha" to "2026-09-25"), s3.happeningsDone)
    }

    @Test fun `mistakes make the reward smaller, never below a quarter or 1`() {
        assertEquals(mapOf(Res.FOOD to 16), GameEngine.happeningPay(mapOf(Res.FOOD to 16), 0))
        assertEquals(mapOf(Res.FOOD to 12), GameEngine.happeningPay(mapOf(Res.FOOD to 16), 1))
        assertEquals(mapOf(Res.FOOD to 8), GameEngine.happeningPay(mapOf(Res.FOOD to 16), 2))
        assertEquals(mapOf(Res.FOOD to 4), GameEngine.happeningPay(mapOf(Res.FOOD to 16), 3))
        assertEquals(mapOf(Res.FOOD to 4), GameEngine.happeningPay(mapOf(Res.FOOD to 16), 9))
        assertEquals(mapOf(Res.WOOD to 1), GameEngine.happeningPay(mapOf(Res.WOOD to 2, Res.STONE to 0), 5))
        val (_, paid) = GameEngine.completeHappening(village, "k/h", soup, 2, day, now, entry)
        assertEquals(mapOf(Res.FOOD to 8, Res.WISDOM to 2), paid)
    }

    @Test fun `only today's done happenings are kept`() {
        val old = village.copy(happeningsDone = mapOf("a/x" to "2026-09-20", "b/y" to "2026-09-24"))
        val (s, _) = GameEngine.completeHappening(old, "c/z", soup, 0, day, now, entry)
        assertEquals(mapOf("b/y" to "2026-09-24", "c/z" to "2026-09-24"), s.happeningsDone)
    }

    @Test fun `a happening without a reward is still marked done`() {
        val (s, paid) = GameEngine.completeHappening(village, "k/h", emptyMap(), 0, day, now, entry)
        assertTrue(paid.isEmpty())
        assertEquals("2026-09-24", s.happeningsDone["k/h"])
        assertEquals(village.resources, s.resources)
    }
}

class HappeningPaidTest {
    @Test fun `full stores and a second time are told apart`() {
        val due = mapOf(Res.FOOD to 15)
        val full = si.lanisce.lani.app.HappeningPaid(emptyMap(), due, again = false)
        assertTrue(full.full)
        assertTrue(!si.lanisce.lani.app.HappeningPaid(due, due, again = false).full)
        assertTrue(!si.lanisce.lani.app.HappeningPaid(emptyMap(), due, again = true).full)
    }
}
