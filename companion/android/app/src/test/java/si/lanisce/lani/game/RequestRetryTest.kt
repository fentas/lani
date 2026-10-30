package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.BusyVillage.at
import si.lanisce.lani.game.BusyVillage.cast
import si.lanisce.lani.game.BusyVillage.play
import si.lanisce.lani.game.BusyVillage.queue
import si.lanisce.lani.game.BusyVillage.today
import si.lanisce.lani.game.BusyVillage.zone
import si.lanisce.lani.game.scene.TownMarkers
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.ui.game.whyText

/**
 * A request tried below the pass mark (companion/GAME.md "Quests", "A run clears its \"!\""; Teo's tent move, 2026-10-30: 1 of 4,
 * "the challenge is not there anymore"): it says where it went (back tomorrow, the time), a quiet "🔁" stays at its giver,
 * its drill ("🎯 Vadi in poskusi znova") is offered after any failed try, and passing it brings the request back today.
 */
class RequestRetryTest {
    private val morning = at(today, 9)
    private val mojca = "tutor-dvojina-osnove-v1"

    private fun failed(): Pair<GameState, Long> {
        val s0 = RequestWindow.settle(BusyVillage.state, queue(BusyVillage.state, morning), morning, zone)
        val t = at(today, 10, 40)
        val (s, r) = play(s0, mojca, 3, 10, t)
        assertFalse(r.won)
        return s to t
    }

    @Test fun `a failed try is back tomorrow, says when, keeps a quiet sign at its giver and offers the drill`() {
        val (s, t) = failed()
        val q = s.quests.single { it.id == mojca }
        val tomorrow = at(today.plusDays(1), 5)
        assertEquals(tomorrow, s.pace.back[mojca])
        val now = queue(s, t + 60_000)
        assertEquals(QuestQueue.Why.Again(tomorrow), now.why(mojca))
        // the row says when ("Spet jutri od 05:00 · Back tomorrow from 05:00"), never just gone
        assertEquals("🌙 Spet jutri od 05:00 · Back tomorrow from 05:00", whyText(QuestQueue.Why.Again(tomorrow)).let { it.replace(Regex("""\d\d:\d\d"""), "05:00") })
        // the drill is offered after this one try (not only after three)
        assertTrue(QuestQueue.retry(q, s.pace, t + 60_000))
        assertFalse(QuestQueue.hard(q, s.quests))
        // on the map: her "🔁", quiet, where her requests are (her school), until her other one is up again
        val back = TownMarkers.backMarkers(now, s, cast)
        assertEquals(listOf("${TownMarkers.BACK}$mojca"), back.map { it.id })
        assertEquals("🔁", back.single().emoji)
        // the next morning it is up again, no drill offered
        assertFalse(QuestQueue.retry(q, s.pace, at(today.plusDays(1), 6)))
    }

    @Test fun `passing the drill brings it back today, its giver asking again`() {
        val (s, t) = failed()
        val drilled = t + 15 * 60_000
        val after = RequestWindow.retried(s, mojca, drilled)
        assertNull(after.pace.back[mojca])
        assertNull(after.pace.rests["Učiteljica Mojca"])
        val q = queue(RequestWindow.settle(after, queue(after, drilled), drilled, zone), drilled + 1_000)
        assertTrue(q.up.any { it.id == mojca })
        assertNull(q.why(mojca))
        assertTrue(TownMarkers.backMarkers(q, after, cast).isEmpty())
        // a drill for a request that isn't waiting (passed, or back already) changes nothing
        assertSame(after, RequestWindow.retried(after, mojca, drilled + 1_000))
        assertSame(BusyVillage.state, RequestWindow.retried(BusyVillage.state, "nope", drilled))
    }

    @Test fun `without the drill it comes back tomorrow as ever`() {
        val (s, t) = failed()
        val q = queue(s, at(today, 20))
        assertEquals(QuestQueue.Why.Again(at(today.plusDays(1), 5)), q.why(mojca))
        val next = queue(s, at(today.plusDays(1), 6))
        assertNull(next.why(mojca)?.takeIf { it is QuestQueue.Why.Again })
    }

    @Test fun `the tent move is never lost, open, its tries kept, back tomorrow, back today after the drill`() {
        val day = Fixtures.day0
        val t0 = Fixtures.noon(day)
        val s0 = GameEngine.newGame(7, t0).copy(
            age = Age.ZASELEK, resources = Res.entries.associateWith { 150 }, villagers = 4, morale = 60,
            buildings = listOf(Building("b0", BuildingType.FIELD, 0), Building("b1", BuildingType.TENT, 1), Building("b2", BuildingType.HUT, 2)),
            residents = listOf(Resident("luka", day.toString())), lastTick = day.minusDays(1).toString(), foundedOn = day.minusDays(30).toString(),
        )
        val asked = TentMove.offer(s0, t0)
        val tent = asked.quests.single { it.id == TentMove.ID }
        val (n, r) = GameEngine.completeQuest(asked, TentMove.ID, 1, 4, t0, missed = listOf("kje-mestnik-orodnik"))
        assertFalse(r.won)
        val s = RequestWindow.finished(n, tent, false, t0, zone)
        val q = s.quests.single { it.id == TentMove.ID }
        assertFalse(q.done)
        assertEquals(1, q.tries)
        assertEquals(listOf("kje-mestnik-orodnik"), q.missed)
        assertTrue(QuestQueue.retry(q, s.pace, t0 + 1_000))
        // passing its drill: back today, still the same request with its try
        val back = RequestWindow.retried(s, TentMove.ID, t0 + 600_000)
        assertNull(back.pace.back[TentMove.ID])
        assertEquals(1, back.quests.single { it.id == TentMove.ID }.tries)
        assertTrue(back.pace.up.any { it.id == TentMove.ID })
    }

    @Test fun `the map's tip says where it went, once, its one tap the drill`() {
        val (s, t) = failed()
        val markers = TownMarkers.backMarkers(queue(s, t + 60_000), s, cast)
        val tip = Guidance.next(s, GuideFacts(today, t + 6 * 60_000, ready = true, markers = markers, chatted = true, reviewed = true), TipPlace.MAP)
        assertEquals("back", tip?.moment)
        assertEquals(TipAction.Retry(mojca), tip?.action)
        val dealt = Guidance.dealt(Guidance.shown(s, tip!!, today, t), "back", today, t)
        assertNull(Guidance.next(dealt, GuideFacts(today.plusDays(3), t + 3 * 86_400_000L, ready = true, markers = markers, chatted = true, reviewed = true), TipPlace.MAP)?.takeIf { it.moment == "back" })
    }
}
