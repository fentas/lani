package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.BusyVillage.at
import si.lanisce.lani.game.BusyVillage.cast
import si.lanisce.lani.game.BusyVillage.play
import si.lanisce.lani.game.BusyVillage.queue
import si.lanisce.lani.game.BusyVillage.zone
import si.lanisce.lani.game.scene.TownMarkers
import java.time.Instant
import kotlin.random.Random

/**
 * A week in the busy village (companion/GAME.md "Quests", "A window of requests"): the learner comes three times a day
 * (morning, afternoon, evening) and plays a few of the requests the map shows, some passed, some below the mark; the tutor
 * answers each run with a new task, half of them for the villager just helped (as it tends to); a new day prunes and asks
 * anew. Every quarter of an hour the map is looked at: never more bubbles than the window, one per villager, none of
 * someone resting, none tried below the mark before its morning, nothing lost.
 */
class RequestWindowSimulationTest {
    private data class Week(val maxShown: Int, val maxWindow: Int, val played: Int, val failed: Int, val published: Int, val looks: Int)

    private fun simulate(base: Int, seed: Int): Week {
        val r = Random(seed)
        var s = BusyVillage.state
        var day = BusyVillage.today
        var maxShown = 0
        var maxWindow = 0
        var played = 0
        var failed = 0
        var published = 0
        var looks = 0
        val sessions = listOf(8 to 0, 13 to 30, 19 to 30)
        repeat(7) { d ->
            val start = at(day, 6)
            if (d > 0) {
                // the new day's tick: the villagers' old requests expire, they ask anew (the tutor's stay)
                val (pruned, _) = Quests.prune(s, start)
                s = Quests.refill(pruned, day, start, Fixtures.pool()).first
            }
            for (minutes in 0 until 17 * 60 step 15) {
                val now = start + minutes * 60_000L
                val q = queue(s, now, base)
                looks++
                check(s, q, now, base)
                maxShown = maxOf(maxShown, TownMarkers.questMarkers(q.up, s, cast).size)
                maxWindow = maxOf(maxWindow, RequestWindow.size(base, s.pace, now))
                s = RequestWindow.settle(s, q, now, zone)
                val session = sessions.firstOrNull { (h, m) -> now == at(day, h, m) } ?: continue
                // a session: one to three of what the map shows, ten minutes each
                var t = now
                repeat(1 + r.nextInt(3)) {
                    val up = queue(s, t, base).up
                    if (up.isEmpty()) return@repeat
                    val pick = up[r.nextInt(up.size)]
                    val pass = r.nextFloat() < 0.6f
                    t += 10 * 60_000L
                    val (n, res) = play(s, pick.id, if (pass) 9 else 3, 10, t, base)
                    assertEquals(pass, res.won)
                    s = n
                    played++
                    if (!pass) failed++
                    // its bubble goes at once, either way; the giver doesn't ask again right away
                    val after = queue(s, t, base)
                    assertFalse("${pick.id} back at once", after.up.any { it.id == pick.id })
                    assertFalse("${pick.giver} asks again at once", after.up.any { it.giver == pick.giver })
                    // the tutor answers the run with a new task: for the same villager half the time
                    val giver = if (r.nextBoolean()) pick.giver else cast[r.nextInt(cast.size)].name
                    val emoji = cast.first { it.name == giver }.emoji
                    val module = "sim-${d}-${session.first}-${published}"
                    s = s.copy(quests = s.quests + BusyVillage.tutor(module, giver, "Naloga $module", since = day.toString()).copy(emoji = emoji))
                    published++
                    val next = queue(s, t, base)
                    check(s, next, t, base)
                    assertFalse("$giver's new task up right after their run", giver == pick.giver && next.up.any { it.giver == giver })
                    s = RequestWindow.settle(s, next, t, zone)
                }
            }
            day = day.plusDays(1)
        }
        return Week(maxShown, maxWindow, played, failed, published, looks)
    }

    /** What the map shows at [now] holds to the window's rules. */
    private fun check(s: GameState, q: QuestQueue.Queue, now: Long, base: Int) {
        val size = RequestWindow.size(base, s.pace, now)
        val shown = TownMarkers.questMarkers(q.up, s, cast)
        assertTrue("${shown.size} bubbles, the window $size", shown.size <= size && q.up.size <= size)
        assertTrue("never more than the start and its growth", size <= base + RequestWindow.GROW_MAX)
        assertEquals("one per villager", q.up.size, q.up.map { it.giver }.distinct().size)
        for (u in q.up) {
            val rest = s.pace.rests[u.giver]
            assertFalse("${u.giver} up while resting", rest != null && rest.until > now)
            assertFalse("${u.id} up before its morning", (s.pace.back[u.id] ?: 0L) > now)
        }
        // nothing lost: every open request is up or in "Later", once
        val open = s.quests.filter { !it.done }.map { it.id }.sorted()
        val listed = (q.up.map { it.id } + q.later.map { it.quest.id })
        assertEquals(open, listed.sorted())
        assertEquals(listed.size, listed.distinct().size)
        // the chip counts the rest
        assertEquals(q.later.count { it.why !is QuestQueue.Why.Spent }, q.waiting)
    }

    @Test fun `a week of a busy village never shows more than the window on a phone`() {
        for (seed in 1..4) {
            val w = simulate(RequestWindow.PHONE, seed)
            assertTrue("$seed: $w", w.maxShown <= RequestWindow.PHONE + RequestWindow.GROW_MAX)
            assertTrue("$seed: played $w", w.played >= 21)
            assertTrue("$seed: some below the mark $w", w.failed > 0)
            println("phone, seed $seed: $w")
        }
    }

    @Test fun `a week of a busy village never shows more than the window on a tablet`() {
        for (seed in 1..2) {
            val w = simulate(RequestWindow.TABLET, seed)
            assertTrue("$seed: $w", w.maxShown <= RequestWindow.TABLET + RequestWindow.GROW_MAX)
            println("tablet, seed $seed: $w")
        }
    }

    @Test fun `left alone, the map shows the start window all week, never the pile`() {
        var s = BusyVillage.state
        var day = BusyVillage.today
        repeat(7) {
            val start = at(day, 6)
            val (pruned, _) = Quests.prune(s, start)
            s = Quests.refill(pruned, day, start, Fixtures.pool()).first
            for (h in 6..22) {
                val now = at(day, h)
                val q = queue(s, now)
                assertTrue("${Instant.ofEpochMilli(now)}: ${q.up.size}", q.up.size <= RequestWindow.PHONE)
                s = RequestWindow.settle(s, q, now, zone)
            }
            day = day.plusDays(1)
        }
        // thirteen and more asked, the map at three
        assertTrue(s.quests.count { !it.done } >= 13)
        assertEquals(RequestWindow.PHONE, queue(s, at(day.minusDays(1), 12)).up.size)
    }
}
