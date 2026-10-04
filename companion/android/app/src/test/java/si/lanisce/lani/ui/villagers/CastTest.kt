package si.lanisce.lani.ui.villagers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.json
import si.lanisce.lani.game.ContentPool
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.Quests
import si.lanisce.lani.game.villagers.Bond
import si.lanisce.lani.game.villagers.Memory
import si.lanisce.lani.game.villagers.Villager
import si.lanisce.lani.game.villagers.VillagerLine
import si.lanisce.lani.game.villagers.at
import java.io.File
import java.time.LocalDate

/** The curated cast (companion/cultures/primorska/villagers) as the register, the pages and the friendship use it. */
class CastTest {
    private val day = LocalDate.of(2026, 9, 24)
    private val cast: List<Villager> = File("../../cultures/primorska/villagers").listFiles { f -> f.name.endsWith(".json") }.orEmpty()
        .sortedBy { it.name }.map { json.decodeFromString(Villager.serializer(), it.readText()) }

    @Test fun `every local quest's giver is in the cast, so a done quest befriends them`() {
        assertTrue(cast.size >= 10)
        val cards = (1..20).map { ReviewCard("c$it", "beseda $it", "word $it") }
        val givers = HashSet<String>()
        for (seed in 1L..60L) {
            var s = GameState(seed = seed)
            for (d in 0L..6L) {
                s = s.copy(quests = emptyList())
                givers += Quests.refill(s, day.plusDays(d), 0L, ContentPool(cards)).second.map { it.giver }
            }
        }
        assertTrue(givers.size >= 10)
        val names = cast.map { it.name }.toSet()
        assertEquals(emptySet<String>(), givers - names)
    }

    @Test fun `every villager thanks for a gift in their words, 3 lines for a favourite, 2 for an ordinary good, 1 for a rare one`() {
        assertEquals("the cast of 13 and the hunter, an extra", 14, cast.size)
        for (v in cast) {
            val g = v.lines.gift
            assertEquals(v.id, listOf(3, 2, 1), listOf(g.liked.size, g.ordinary.size, g.rare.size))
            for (l in g.liked + g.ordinary + g.rare) {
                assertTrue("${v.id}: $l", l.by["sl"]!!.isNotBlank() && l.by["en"]!!.isNotBlank() && '{' !in l.target)
            }
            // what they answer is theirs, for each kind of good
            for (r in GiftReaction.entries) assertTrue("${v.id} $r", (g.liked + g.ordinary + g.rare).any { it.target == GiftTalk.reply(v, r, 0, day).sl })
        }
    }

    @Test fun `every villager greets in their own words at every time of day, and remembers once they know Jan`() {
        val m = Memory(day.toString(), "ovco, ki sva jo našla v gozdu", "the sheep we found in the forest", "quest")
        for (v in cast) {
            for (level in 0..4) for (t in VillagerLine.PARTS) {
                val said = VillagerLogic.greet(v, level, day, t).sl
                assertTrue("${v.id} greets at $level in the $t: $said", v.lines.greet.at(t).any { it.target == said })
            }
            val remembered = VillagerLogic.remember(v, 4, listOf(m), day)
            assertNotNull(v.id, remembered)
            assertTrue(v.id, remembered!!.sl.contains(m.sl) && remembered.en.contains(m.en))
            val lines = VillagerLogic.greeting(v, Bond(points = 150, memories = listOf(m)), day)
            assertEquals(v.id, 2, lines.size)
        }
    }
}
