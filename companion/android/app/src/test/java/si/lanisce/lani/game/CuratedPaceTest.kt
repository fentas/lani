package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.ModuleInfo
import si.lanisce.lani.data.json
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the app does with the list of curated modules the bridge offers (GET /modules, bridge/src/store.ts `offered`;
 * docs/plans/05-built/practice-it.md "The pace"). Eight at once are eight requests, and from the second day the tutor's rank
 * fills the map's window of four, a villager's own out of it. One at a time keeps the villagers' requests up, a module played
 * stays known as played, and the next asks by itself the day it is listed. An app of any age does this: `withTutorQuests`,
 * `QuestQueue.of` and `RequestWindow.order` read the same at 0.2.698 (a37c1765).
 */
class CuratedPaceTest {
    private val today = LocalDate.of(2026, 11, 3)
    private val zone = ZoneId.systemDefault()
    private val noon = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun module(id: String, giver: String) = json.decodeFromString<ModuleInfo>(
        """{"id":"$id","version":1,"title":"$id","level":"A1","quest":{"giver":"$giver","emoji":"x","story":"s","skill":"stone","reward":{"stone":60,"wisdom":10}}}""",
    )

    private val eight = listOf(
        module("essere-avere", "Nonno Bepi"), module("articoli", "Nonna Rosa"), module("presente", "Nonna Rosa"), module("aggettivi", "Pastore Davide"),
        module("preposizioni-articolate", "Pastore Davide"), module("domande-non", "Giulia"), module("mi-piace", "Nonna Rosa"), module("passato-avere", "Nonno Bepi"),
    )

    private fun local(giver: String, n: Int) = Quest(
        id = "q-$n", giver = giver, emoji = "x", title = "t$n", story = "s", skill = Res.FOOD, reward = mapOf(Res.FOOD to 30), source = QuestSource.LOCAL,
        expiresAt = noon + 86_400_000L, since = today.toString(),
    )

    private val locals = listOf(local("Apicoltore Aldo", 0), local("Ostessa Marisa", 1), local("Nonna Rosa", 2))

    private fun up(s: GameState, size: Int = RequestWindow.TABLET): QuestQueue.Queue =
        QuestQueue.of(s.quests, s.quests.filter { !it.done }, today, window = QuestQueue.Window(noon, RequestPace(), size, zone = zone))

    @Test fun `eight at once fill the window, nothing of the villagers' own stays up`() {
        val s = GameEngine.withTutorQuests(GameState(age = Age.VAS, quests = locals), eight, noon)
        assertEquals(11, s.quests.size)
        val q = up(s)
        assertEquals(4, q.up.size)
        assertTrue("every request up is a module's: ${q.up.map { it.id }}", q.up.all { it.source == QuestSource.TUTOR })
        assertEquals(7, q.waiting)
    }

    @Test fun `one at a time leaves the rest of the window to the villagers, one chronicle line`() {
        val s = GameEngine.withTutorQuests(GameState(age = Age.VAS, quests = locals), eight.take(1), noon)
        val q = up(s)
        assertEquals(1, q.up.count { it.source == QuestSource.TUTOR })
        assertEquals(setOf("Nonno Bepi", "Apicoltore Aldo", "Ostessa Marisa", "Nonna Rosa"), q.up.map { it.giver }.toSet())
        assertEquals(0, q.waiting)
        assertEquals(1, s.log.size)
    }

    @Test fun `the next the day it is listed, the one played kept as played`() {
        val first = GameEngine.withTutorQuests(GameState(age = Age.VAS, quests = locals), eight.take(1), noon)
        val played = first.copy(quests = first.quests.map { if (it.moduleId == "essere-avere") it.copy(done = true) else it })
        val next = GameEngine.withTutorQuests(played, eight.take(2), noon + 86_400_000L)
        assertEquals(true, next.quests.first { it.moduleId == "essere-avere" }.done)
        assertEquals(listOf("articoli"), next.quests.filter { it.source == QuestSource.TUTOR && !it.done }.map { it.moduleId })
    }

    @Test fun `a module the list drops would be forgotten as played, so the bridge keeps what was asked listed`() {
        val first = GameEngine.withTutorQuests(GameState(age = Age.VAS), eight.take(1), noon)
        val played = first.copy(quests = first.quests.map { it.copy(done = true) })
        assertEquals(emptyList<Quest>(), GameEngine.withTutorQuests(played, emptyList(), noon).quests)
    }
}
