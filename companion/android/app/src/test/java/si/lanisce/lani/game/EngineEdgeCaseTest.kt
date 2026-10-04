package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.json
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** Clock, determinism, overflow and long-run edge cases of the engine. */
class EngineEdgeCaseTest {
    private val pool = Fixtures.pool()
    private val t0 = noon(day0)

    private fun village(age: Age = Age.TABOR, res: Int = 150, lastTick: LocalDate = day0) = GameEngine.newGame(1, t0).copy(
        age = age,
        resources = Res.entries.associateWith { res },
        buildings = listOf(Building("t1", BuildingType.TENT, 0), Building("t2", BuildingType.TENT, 1)),
        lastTick = lastTick.toString(),
        foundedOn = lastTick.toString(),
    )

    private fun tick(s: GameState, day: LocalDate, pool: ContentPool = this.pool) = GameEngine.tick(s, day, noon(day), pool)

    // --- content ---------------------------------------------------------------------------------

    @Test fun `a large deck is sorted without breaking the comparator contract`() {
        // The sort key used to be drawn inside the comparator: from 32 cards on, TimSort could throw.
        val cards = (0 until 300).map { ReviewCard("c$it", "beseda $it", "word $it", repetitions = it % 5, lastQuality = it % 6) }
        for (seed in 0 until 200) {
            val picked = Content.pickCards(cards, 40, Random(seed))
            assertEquals(40, picked.distinctBy { it.id }.size)
            assertTrue(picked.take(10).all { si.lanisce.lani.data.ReviewPlanner.familiarity(it) == 0 })
        }
        val big = ContentPool(cards, canSpeak = true)
        for (seed in 0L until 50L) {
            val s = GameEngine.tick(GameEngine.newGame(seed, t0), day0, t0, big).state
            assertEquals(3, s.quests.size)
            for (r in Res.entries) assertEquals(7, GameEngine.gatherChallenge(s, r, big).exercises.size)
        }
        assertTrue(Content.pickCards(cards, -1, Random(1)).isEmpty())
    }

    // --- determinism -----------------------------------------------------------------------------

    @Test fun `randomness does not depend on the process`() {
        // An enum's hashCode is its identity, different on every app start: enums seed by name.
        assertEquals(rng(9, "gather", "FOOD", 3L).nextLong(), rng(9, "gather", Res.FOOD, 3L).nextLong())
        assertEquals(rng(9, Age.VAS).nextInt(), rng(9, "VAS").nextInt())
        assertEquals(0, stableHash(null))
    }

    // --- clock -------------------------------------------------------------------------------------

    @Test fun `ticks count calendar days across months, leap days and daylight saving`() {
        val jan31 = LocalDate.of(2026, 1, 31)
        val s = village(lastTick = jan31).copy(villagers = 1)
        val mar1 = tick(s, LocalDate.of(2026, 3, 1)).state // 29 days, capped at 14
        assertEquals(tick(s, jan31.plusDays(14)).state.resources, mar1.resources)
        assertEquals("2026-03-01", mar1.lastTick)
        // a leap day is a day like any other (two villagers, no room for a third: 3 🌾 a day)
        val feb28 = village(lastTick = LocalDate.of(2028, 2, 28)).copy(villagers = 2, buildings = emptyList())
        val one = tick(feb28, LocalDate.of(2028, 2, 29)).state
        val two = tick(one, LocalDate.of(2028, 3, 1)).state
        assertEquals(150 - 3, one.res(Res.FOOD))
        assertEquals(150 - 6, two.res(Res.FOOD))
        // Summer time starts on 29 March 2026 in Europe: the night is an hour shorter, still one day each
        val zone = ZoneId.of("Europe/Ljubljana")
        val sat = LocalDate.of(2026, 3, 28)
        var d = village(lastTick = sat).copy(villagers = 2, buildings = emptyList())
        for (day in listOf(sat.plusDays(1), sat.plusDays(2))) {
            d = GameEngine.tick(d, day, day.atTime(7, 0).atZone(zone).toInstant().toEpochMilli(), pool).state
        }
        assertEquals(150 - 6, d.res(Res.FOOD))
        assertEquals("2026-03-30", d.lastTick)
    }

    @Test fun `a clock set far ahead does not freeze the village`() {
        val future = LocalDate.of(2031, 1, 1)
        val wrong = tick(village(), future).state // a new day there: quests expire in 2031
        assertTrue(wrong.quests.all { it.expiresAt > noon(future) })
        // back to the right date: without the fix nothing happened until 2031
        val back = day0.plusDays(1)
        val fixed = tick(wrong, back)
        assertEquals(back.toString(), fixed.state.lastTick)
        assertTrue(fixed.state.quests.all { it.expiresAt <= noon(back) + Quests.LIFETIME_DAYS * DAY_MS })
        assertEquals(wrong.resources, fixed.state.resources) // no upkeep for the jump
        val next = tick(fixed.state, back.plusDays(1)).state
        assertEquals(back.plusDays(1).toString(), next.lastTick)
        assertTrue(next.res(Res.FOOD) < fixed.state.res(Res.FOOD)) // days count again
        // one day back is a time zone change: wait for the date to catch up
        val east = tick(village(), day0.plusDays(1)).state
        assertEquals(east, tick(east, day0).state)
    }

    @Test fun `an unreadable date starts the village clock again instead of failing`() {
        val broken = village().copy(lastTick = "31.12.2026", foundedOn = "?")
        val s = tick(broken, day0).state
        assertEquals(day0.toString(), s.lastTick)
        assertEquals(day0.toString(), s.foundedOn)
        assertEquals(broken.resources, s.resources)
    }

    // --- events and quests while away ------------------------------------------------------------------

    @Test fun `an event left open while away is lost once, then life goes on`() {
        val s = village(Age.ZASELEK, 300).copy(
            event = GameEvent("ev-x", EventKind.WOLVES, 2, t0, t0 + Events.BASE_DEADLINE_HOURS * HOUR_MS),
            buildings = listOf(Building("p", BuildingType.PALISADE, 0), Building("f", BuildingType.FIELD, 1)),
        )
        val back = day0.plusDays(10)
        val r = tick(s, back)
        assertEquals(1, r.state.stats.eventsLost)
        assertTrue(r.news.any { it.text.contains("too late") && it.at == noon(back) })
        assertTrue(r.news.any { it.text.contains("10 days") })
        // the only event now is today's, if any: none piled up while the app was closed
        r.state.event?.let { assertEquals("ev-$back", it.id) }
        // at most one building was damaged, and it can be repaired with what is left
        val damaged = r.state.buildings.filter { it.damaged }
        assertTrue(damaged.size <= 1)
        damaged.forEach { b -> assertTrue(GameEngine.repair(r.state, b.id, noon(back)).buildings.none { it.damaged }) }
    }

    @Test fun `quests expire after three days, also across a gap`() {
        val s = tick(GameEngine.newGame(5, t0), day0).state
        val first = s.quests.map { it.id }.toSet()
        assertEquals(3, first.size)
        val later = tick(s, day0.plusDays(2)).state // 48 h: still open
        assertTrue(later.quests.map { it.id }.containsAll(first))
        val gone = tick(later, day0.plusDays(20))
        assertTrue(gone.state.quests.none { it.id in first })
        assertEquals(3, gone.news.count { it.text.contains("too late, the request expired") })
        assertEquals(3, gone.state.quests.count { !it.done })
        // an expired quest can no longer be completed
        val (after, result) = GameEngine.completeQuest(gone.state, first.first(), 6, 6, noon(day0.plusDays(20)))
        assertEquals(gone.state, after)
        assertTrue(result.rewards.isEmpty())
    }

    @Test fun `quest side rewards top up the scarcest resource`() {
        val s = GameEngine.newGame(3, t0).copy(age = Age.ZASELEK, resources = mapOf(Res.FOOD to 400, Res.WOOD to 400, Res.STONE to 5, Res.WISDOM to 300))
        val q = tick(s.copy(lastTick = ""), day0).state.quests
        assertEquals(3, q.size)
        assertTrue(q.all { it.skill == Res.STONE || Res.STONE in it.reward })
    }

    // --- merchant -------------------------------------------------------------------------------------

    @Test fun `the merchant never takes more than the store can take back`() {
        val caps = GameEngine.attributes(village(Age.ZASELEK)).caps
        val nearlyFull = village(Age.ZASELEK).copy(
            resources = mapOf(Res.FOOD to caps.getValue(Res.FOOD), Res.WOOD to caps.getValue(Res.WOOD) - 10, Res.STONE to caps.getValue(Res.STONE) - 9, Res.WISDOM to caps.getValue(Res.WISDOM) - 9),
            event = GameEvent("ev-m", EventKind.MERCHANT, 3, t0, t0 + 36 * HOUR_MS),
        )
        val (after, r) = GameEngine.resolveEvent(nearlyFull, 7, 7, t0)
        assertTrue(r.won)
        val paid = r.losses.values.sum()
        val got = r.rewards.values.sum()
        assertTrue("paid $paid for $got", paid in 1..5 && got >= paid * 2 - 1)
        assertEquals(nearlyFull.resources.values.sum() - paid + got, after.resources.values.sum())
    }

    // --- pay and totals -------------------------------------------------------------------------------

    @Test fun `the first answers of a day pay in full, later ones half`() {
        val s = village(Age.ZASELEK).copy(morale = 50, villagers = 1, resources = emptyMap())
        val fresh = GameEngine.earn(s, List(Catalog.FRESH_ANSWERS) { Res.FOOD to Verdict.CORRECT })
        assertEquals(6 * Catalog.FRESH_ANSWERS, fresh.second[Res.FOOD])
        assertEquals(0, GameEngine.freshAnswersLeft(fresh.first))
        val tired = GameEngine.earn(fresh.first.copy(resources = emptyMap()), List(10) { Res.FOOD to Verdict.CORRECT })
        assertEquals(30, tired.second[Res.FOOD])
        // a run that crosses the line pays full up to it
        val mixed = GameEngine.earn(s.copy(answersToday = Catalog.FRESH_ANSWERS - 2), List(4) { Res.STONE to Verdict.CORRECT })
        assertEquals(6 + 6 + 3 + 3, mixed.second[Res.STONE])
        // a new day starts fresh, the same day does not
        assertEquals(tired.first.answersToday, tick(tired.first, day0).state.answersToday)
        assertEquals(Catalog.FRESH_ANSWERS, GameEngine.freshAnswersLeft(tick(tired.first, day0.plusDays(1)).state))
    }

    @Test fun `lifetime totals stop at the largest number instead of turning negative`() {
        val s = village().copy(stats = GameStats(totalEarned = mapOf(Res.FOOD to Int.MAX_VALUE - 3)), resources = emptyMap(), morale = 50)
        val after = GameEngine.earn(s, List(5) { Res.FOOD to Verdict.CORRECT }).first
        assertEquals(Int.MAX_VALUE, after.stats.totalEarned[Res.FOOD])
        assertEquals(30, after.res(Res.FOOD))
        assertNotNull(GameEngine.gatherChallenge(after, Res.FOOD, pool))
        // a negative stock (an old or broken save) is treated as empty, not as a crash
        val odd = s.copy(resources = mapOf(Res.WOOD to -5))
        assertEquals(0, debit(odd, mapOf(Res.WOOD to 10)).second.values.sum())
    }

    @Test fun `building ids stay unique`() {
        val s = village(Age.TABOR, 500).copy(buildings = listOf(Building("tent-2", BuildingType.TENT, 0)))
        val built = GameEngine.build(s, BuildingType.TENT, t0)
        assertEquals(2, built.buildings.map { it.id }.distinct().size)
    }

    // --- a long life ----------------------------------------------------------------------------------

    @Test fun `a year of play keeps the saved state small`() {
        var s = GameEngine.newGame(8, t0)
        for (d in 0 until 400L) {
            val day = day0.plusDays(d)
            s = tick(s, day).state
            s = GameEngine.earn(s, List(30) { Res.entries[(it + d.toInt()) % 4] to Verdict.CORRECT }).first
            s.quests.firstOrNull { !it.done }?.let { s = GameEngine.completeQuest(s, it.id, 5, 6, noon(day)).first }
            GameEngine.eventChallenge(s, pool)?.let { s = GameEngine.resolveEvent(s, it.exercises.size, it.exercises.size, noon(day)).first }
            s = GameEngine.buildOptions(s).firstOrNull { it.available }?.let { GameEngine.build(s, it.type, noon(day)) } ?: s
            s = GameEngine.advance(s, 2000, noon(day))
        }
        assertTrue(s.log.size <= LOG_SIZE)
        assertTrue(s.quests.size <= Quests.OPEN_LOCAL)
        assertTrue(s.stats.questsDone > 100)
        val text = json.encodeToString(GameState.serializer(), s)
        assertTrue("state is ${text.length} characters", text.length < 20_000)
        assertEquals(s, json.decodeFromString(GameState.serializer(), text))
        assertNull(GameEngine.upgradeOption(s, "nope"))
    }
}
