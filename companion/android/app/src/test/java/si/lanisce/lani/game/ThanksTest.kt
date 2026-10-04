package si.lanisce.lani.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.culture.Culture
import si.lanisce.lani.game.culture.CultureError
import si.lanisce.lani.game.culture.Cultures
import java.time.LocalDate

/**
 * A request's thank-you good (companion/GAME.md, "The chest"): from the giver's rotation (chest.json `thanks_rotation`,
 * their `thanks` good first and two times in three), steady for a request and varied over requests, a festival's own in its
 * season, only goods the chest has; without a rotation their one `thanks` good, someone unknown the pack's default.
 * Played on Primorska with a rotation of the test's own, so the content can change without changing these.
 */
class ThanksTest {
    /** No festival of Primorska's is near: the grape harvest was on 26 September, St Martin's is on 11 November. */
    private val day = LocalDate.of(2026, 10, 6)
    private val seed = 7L

    private val rotation = """
        {
          "micka": { "goods": ["potica", "kruh", "jabolka"], "seasonal": { "pust": "krofi", "velika_noc": "pisanice", "bozic": "poprtnik" } },
          "janez": { "goods": ["caj", "jabolka"], "seasonal": { "bozic": "poprtnik", "novo_leto": "penina" } }
        }
    """

    /** Primorska, its chest.json's `thanks_rotation` replaced with [json]. */
    private fun primorska(json: String): Culture = Cultures.parse("primorska") { name ->
        val raw = javaClass.getResourceAsStream("/cultures/primorska/$name")?.use { it.readBytes().decodeToString() }
        if (name != "chest.json" || raw == null) raw
        else JsonObject(Json.parseToJsonElement(raw).jsonObject + ("thanks_rotation" to Json.parseToJsonElement(json))).toString()
    }

    private fun thanks(id: String?, request: String = "q-2026-10-06-0", on: LocalDate = day) = Chest.thanksFor(id, request, seed, on).id

    @After fun back() {
        Cultures.use(Cultures.DEFAULT)
    }

    @Test fun `the same request thanks with the same good, however often it's asked`() {
        Cultures.use(primorska(rotation))
        val first = thanks("micka")
        repeat(5) { assertEquals(first, thanks("micka")) }
        // a try that failed yesterday and passes today: the same good (out of season)
        assertEquals(first, thanks("micka", on = day.plusDays(1)))
    }

    @Test fun `over the requests, every good of the rotation comes, and only those, the usual one two times in three`() {
        Cultures.use(primorska(rotation))
        val picks = (0 until 40).map { thanks("micka", "q-2026-10-${6 + it / 3}-${it % 3}") }
        assertEquals(setOf("potica", "kruh", "jabolka"), picks.toSet())
        val many = (0 until 600).map { thanks("micka", "q-$it") }.groupingBy { it }.eachCount()
        assertTrue("$many", many.getValue("potica") in 340..460 && many.getValue("kruh") in 60..140 && many.getValue("jabolka") in 60..140)
        // not always the same one twice in a row: varied over time
        assertTrue(picks.zipWithNext().any { (a, b) -> a != b })
        // another village, another turn of the same requests
        assertNotEquals(picks, (0 until 40).map { Chest.thanksFor("micka", "q-2026-10-${6 + it / 3}-${it % 3}", seed + 1, day).id })
    }

    @Test fun `in a festival's season, its own good, from a week before to its last grace day`() {
        Cultures.use(primorska(rotation))
        // Christmas, 25 December: from the 18th to the 27th
        val december = (16..29).associateWith { thanks("micka", on = LocalDate.of(2026, 12, it)) }
        assertEquals((18..27).toList(), december.filterValues { it == "poprtnik" }.keys.toList())
        // Easter 2026 is on 5 April, pust 2027 on 9 February (47 days before Easter, 28 March)
        assertEquals("pisanice", thanks("micka", on = LocalDate.of(2026, 4, 5)))
        assertEquals("krofi", thanks("micka", on = LocalDate.of(2027, 2, 9)))
        assertEquals("krofi", thanks("micka", on = LocalDate.of(2027, 2, 2)))
        // two seasons at once: the festival nearer its day (Christmas on the 26th, New Year's from the 29th)
        assertEquals("poprtnik", thanks("janez", on = LocalDate.of(2026, 12, 26)))
        assertEquals("penina", thanks("janez", on = LocalDate.of(2026, 12, 29)))
        assertEquals("penina", thanks("janez", on = LocalDate.of(2027, 1, 3)))
        assertTrue(thanks("janez", on = LocalDate.of(2027, 1, 4)) in setOf("caj", "jabolka"))
    }

    @Test fun `without a rotation, the one thanks good, and someone the pack doesn't know, the default`() {
        Cultures.use(primorska(rotation))
        assertEquals("podkev", thanks("tone"))
        assertEquals("podkev", thanks("tone", on = LocalDate.of(2026, 12, 25)))
        assertEquals("jabolka", thanks("n-ana-furlan"))
        assertEquals("jabolka", thanks(null))
        // no rotation in the pack at all: as before it, each villager's own good
        Cultures.use(primorska("{}"))
        assertEquals(listOf("potica", "kruh", "med"), listOf("micka", "france", "anton").map { thanks(it, on = LocalDate.of(2026, 12, 25)) })
    }

    @Test fun `a request done pays the good of the day it's done, and says so`() {
        Cultures.use(primorska(rotation))
        val christmas = LocalDate.of(2026, 12, 24)
        val q = Quest("q-2026-12-23-0", "Babica Micka", "👵", "t", "s", Res.FOOD, mapOf(Res.FOOD to 10))
        val s = GameEngine.newGame(seed, noon(christmas)).copy(quests = listOf(q), lastTick = christmas.toString())
        val (won, r) = GameEngine.completeQuest(s, q.id, 5, 5, noon(christmas), giverId = "micka", today = christmas)
        assertEquals("poprtnik", r.thanks?.id)
        assertEquals(1, won.chest.goods["poprtnik"])
        assertEquals("🍞" to "Micka ti v zahvalo da poprtnik · Micka thanks you with Christmas bread", won.log.last().let { it.emoji to it.text })
        // out of season, the same request: one of her rotation, the one thanksFor names
        val (_, later) = GameEngine.completeQuest(s, q.id, 5, 5, noon(day), giverId = "micka", today = day)
        assertEquals(Chest.thanksFor("micka", q.id, s.seed, day).id, later.thanks?.id)
    }

    @Test fun `a rotation the pack can't give is refused, and says where`() {
        fun problems(json: String): String = try {
            primorska(json)
            fail("accepted $json")
            ""
        } catch (e: CultureError) {
            e.problems.joinToString("\n")
        }
        assertTrue(problems("""{"micka": {"goods": ["kruh", "potica"]}}""").contains("thanks_rotation.micka.goods[0]: \"kruh\", not their thanks good \"potica\""))
        assertTrue(problems("""{"micka": {"goods": ["potica", "gubana"]}}""").contains("thanks_rotation.micka.goods[1]: no good \"gubana\" in chest.json"))
        assertTrue(problems("""{"micka": {"goods": ["potica", "krofi"]}}""").contains("\"krofi\" is rare"))
        assertTrue(problems("""{"micka": {"goods": ["potica"]}}""").contains("2 to 4 goods, not 1"))
        assertTrue(problems("""{"micka": {"goods": ["potica", "kruh", "kruh"]}}""").contains("twice: kruh"))
        assertTrue(problems("""{"zala": {"goods": ["potica", "kruh"]}}""").contains("\"zala\" has no thanks good"))
        assertTrue(problems("""{"micka": {"goods": ["potica", "kruh"], "seasonal": {"halloween": "krofi"}}}""").contains("no festival \"halloween\" in festivals.json"))
        assertTrue(problems("""{"micka": {"goods": ["potica", "kruh"], "seasonal": {"pust": "gubana"}}}""").contains("thanks_rotation.micka.seasonal.pust: no good \"gubana\""))
        assertTrue(problems("""{"micka": {"goods": ["potica", "kruh"], "sometimes": true}}""").isNotEmpty())
    }
}
