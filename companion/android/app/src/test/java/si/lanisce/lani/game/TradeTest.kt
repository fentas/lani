package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.json
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.Res.FOOD
import si.lanisce.lani.game.Res.STONE
import si.lanisce.lani.game.Res.WISDOM
import si.lanisce.lani.game.Res.WOOD
import si.lanisce.lani.l10n.Lang

/** "⇄ Zamenjaj · Trade" (GAME.md, "Trading"): the rate a run sets, the limits, the run's mix, and the day it keeps. */
class TradeTest {
    private val t0 = noon(day0)

    /** A hamlet (stores of 500) with [food] 🌾 and little of the rest. */
    private fun village(food: Int = 400, wood: Int = 20, age: Age = Age.ZASELEK, types: List<BuildingType> = emptyList()) = GameState(
        age = age, resources = mapOf(FOOD to food, WOOD to wood, STONE to 10, WISDOM to 10),
        buildings = types.mapIndexed { i, t -> Building("b$i", t, i) },
    )

    private fun scales(s: GameState, tier: Int = 1, forged: Int = 0) = s.copy(chest = Inventory(tools = mapOf("vida" to Tool(tier, forged))))

    // --- the rate ----------------------------------------------------------------------------------

    @Test fun `the rate is the base under half right, then 0,05 a right answer, 6 of 6 is 0,80`() {
        val s = village()
        assertEquals(listOf(50, 50, 50, 65, 70, 75, 80), (0..6).map { Trade.rate(s, it).pct })
        // the base trades all the same: no wall, a worse deal
        assertFalse(Trade.rate(s, 2).good)
        assertTrue(Trade.rate(s, 3).good)
        // a shorter run (few cards) shares the same bonus among its answers
        assertEquals(80, Trade.rate(s, 4, total = 4).pct)
        assertEquals(50, Trade.rate(s, 1, total = 4).pct)
    }

    @Test fun `the market adds 0,10 from Trg while it stands`() {
        val trg = village(age = Age.TRG, types = listOf(BuildingType.MARKET))
        assertEquals(listOf(60, 80, 90), listOf(0, 4, 6).map { Trade.rate(trg, it).pct })
        // not before Trg (it doesn't sell yet), nor damaged
        assertEquals(80, Trade.rate(village(age = Age.VAS, types = listOf(BuildingType.MARKET)), 6).pct)
        assertEquals(80, Trade.rate(trg.copy(buildings = trg.buildings.map { it.copy(damaged = true) }), 6).pct)
    }

    @Test fun `Vida's scales make trades a quarter better, never above one for one`() {
        val s = scales(village())
        assertEquals(62, Trade.rate(s, 0).pct) // 0.50 × 1.25 = 0.625, rounded down
        assertEquals(87, Trade.rate(s, 4).pct) // 0.70 × 1.25 = 0.875
        assertEquals(100, Trade.rate(s, 6).pct) // 0.80 × 1.25 = 1.00 exactly
        assertFalse(Trade.rate(s, 6).capped)
        // at the market as well: 0.90 × 1.25 would be 1.125
        val both = scales(village(age = Age.TRG, types = listOf(BuildingType.MARKET)))
        assertEquals(100, Trade.rate(both, 6).pct)
        assertTrue(Trade.rate(both, 6).capped)
        // the brass scales forged twice (+75 %): the base is 0.875, a good run hits the cap
        val brass = scales(village(), tier = 2, forged = 2)
        assertEquals(87, Trade.rate(brass, 0).pct)
        assertEquals(100, Trade.rate(brass, 3).pct)
        // the merchant's deal (2×, the market's 2.5×) stays the better one
        assertTrue(Catalog.TRADE_MAX < 200)
    }

    @Test fun `what comes in is rounded down, at least 1`() {
        assertEquals(70, Trade.brings(100, 70))
        assertEquals(4, Trade.brings(7, 65)) // 4.55
        assertEquals(1, Trade.brings(1, 50)) // 0.5: at least one
        assertEquals(1, Trade.brings(3, 50))
        assertEquals(0, Trade.brings(0, 80))
        assertEquals("0,70", Trade.decimal(70).of(Lang.SL))
        assertEquals("0.70", Trade.decimal(70).of(Lang.EN))
        assertEquals("1,25", Trade.factor(1.25f).of(Lang.DE))
    }

    // --- the limits --------------------------------------------------------------------------------

    @Test fun `at most half the stock, and no more than the other store can take at the best rate`() {
        val s = village(food = 401)
        val l = Trade.limit(s, FOOD, WOOD, day0)
        assertTrue(l.available)
        assertEquals(200, l.max) // half of 401, rounded down
        assertEquals(50 to 80, l.base to l.best)
        // wood at 490 of 500: ten places left, at 0.80 thirteen 🌾 bring ten 🪵 (14 would bring 11)
        val near = village(food = 400, wood = 490)
        assertEquals(13, Trade.limit(near, FOOD, WOOD, day0).max)
        assertEquals(10, Trade.brings(13, 80))
        assertEquals(11, Trade.brings(14, 80))
        // one place left: one for one at any rate
        assertEquals(1, Trade.forRoom(1, 100))
        assertEquals(3, Trade.forRoom(1, 50))
    }

    @Test fun `why a trade can't be, the same resource, a full store, too little, traded away today`() {
        assertNotNull(Trade.limit(village(), FOOD, FOOD, day0).reason)
        val full = Trade.limit(village(wood = 500), FOOD, WOOD, day0)
        assertFalse(full.available)
        assertTrue(full.reason!!.contains("Les"))
        val poor = village(food = 1)
        assertFalse(Trade.limit(poor, FOOD, WOOD, day0).available)
        assertNotNull(Trade.giveBlocker(poor, FOOD, day0))
        assertTrue(Trade.limit(village(food = 2), FOOD, WOOD, day0).available)
        // the scarcest store with room is what to get at first
        assertEquals(STONE, Trade.suggest(village(), FOOD))
        assertNull(Trade.suggest(village().copy(resources = Res.entries.associateWith { 500 }), FOOD))
    }

    @Test fun `once a day for each resource given`() {
        val s = village()
        val (traded, d) = Trade.settle(s, FOOD, WOOD, 100, correct = 4, total = 6, answered = 6, today = day0, now = t0)
        assertTrue(d.traded)
        assertTrue(Trade.tradedToday(traded, FOOD, day0))
        assertFalse(Trade.limit(traded, FOOD, STONE, day0).available)
        assertNotNull(Trade.giveBlocker(traded, FOOD, day0))
        // another resource can still be given today, and food again tomorrow
        assertTrue(Trade.limit(traded.copy(resources = traded.resources + (WISDOM to 100)), WISDOM, STONE, day0).available)
        assertTrue(Trade.limit(traded, FOOD, STONE, day0.plusDays(1)).available)
        // a second settle the same day trades nothing
        val (again, d2) = Trade.settle(traded, FOOD, STONE, 50, 6, 6, 6, day0, t0)
        assertSame(traded, again)
        assertFalse(d2.traded)
        // the next day's trade forgets the day before
        val (next, _) = Trade.settle(traded, WOOD, STONE, 10, 6, 6, 6, day0.plusDays(1), t0 + DAY_MS)
        assertEquals(TradeDay(day0.plusDays(1).toString(), setOf(WOOD)), next.trades)
    }

    // --- settling --------------------------------------------------------------------------------

    @Test fun `a trade moves the stocks at the run's rate and tells the chronicle`() {
        val s = village(food = 400, wood = 20)
        val (n, d) = Trade.settle(s, FOOD, WOOD, 100, correct = 4, total = 6, answered = 6, today = day0, now = t0)
        assertEquals(100, d.given)
        assertEquals(70, d.got)
        assertEquals(70, d.rate.pct)
        assertEquals(300, n.res(FOOD))
        assertEquals(90, n.res(WOOD))
        assertTrue(n.log.last().text, n.log.last().text.startsWith("Zamenjava: 100 🌾 za 70 🪵 (tečaj 0,70)"))
        val r = Trade.result(d)
        assertTrue(r.won)
        assertEquals(mapOf(WOOD to 70), r.rewards)
        assertEquals(mapOf(FOOD to 100), r.losses)
    }

    @Test fun `a run under half right trades at the base, one left early trades nothing`() {
        val s = village()
        val (_, low) = Trade.settle(s, FOOD, WOOD, 100, correct = 2, total = 6, answered = 6, today = day0, now = t0)
        assertEquals(50, low.got)
        val (left, d) = Trade.settle(s, FOOD, WOOD, 100, correct = 3, total = 6, answered = 3, today = day0, now = t0)
        assertSame(s, left)
        assertTrue(d.left)
        assertFalse(d.traded)
        assertFalse(Trade.result(d).won)
        assertTrue(Trade.result(d).message.contains("nič zamenjano"))
        // and leaving keeps the day's trade for later
        assertFalse(Trade.tradedToday(left, FOOD, day0))
    }

    @Test fun `what the run's answers filled meanwhile is taken into account, never more than fits`() {
        // the sheet allowed 13 🌾 for the last ten places; the run's listening filled six of them
        val s = village(food = 400, wood = 496)
        val (n, d) = Trade.settle(s, FOOD, WOOD, 13, correct = 6, total = 6, answered = 6, today = day0, now = t0)
        assertEquals(4, d.got)
        assertEquals(6, d.given) // 6 × 0.80 = 4.8: four, and what would bring a fifth stays
        assertEquals(500, n.res(WOOD))
        assertEquals(394, n.res(FOOD))
        // full by now: nothing traded, nothing taken, the day still free
        val (same, none) = Trade.settle(village(wood = 500), FOOD, WOOD, 13, 6, 6, 6, day0, t0)
        assertFalse(none.traded)
        assertFalse(none.left)
        assertNull(same.trades)
        // and never more than half of what is in store now
        val (_, half) = Trade.settle(village(food = 60), FOOD, WOOD, 100, 6, 6, 6, day0, t0)
        assertEquals(30, half.given)
    }

    // --- the run ---------------------------------------------------------------------------------

    @Test fun `the run is three of each, in turn, giving first, no word twice`() {
        assertEquals(listOf(FOOD, WOOD, FOOD, WOOD, FOOD, WOOD), Trade.mix(FOOD, WOOD, canSpeak = true))
        val c = Trade.challenge(village(), FOOD, WOOD, 100, Fixtures.pool(canSpeak = true), day0)!!
        assertEquals(Catalog.TRADE_EXERCISES, c.exercises.size)
        assertEquals(c.exercises.size, c.skills.size)
        assertEquals(c.exercises.size, c.cardIds.size)
        // the listening half is heard (choice by ear or a short dictation), the vocabulary half isn't
        for ((i, ex) in c.exercises.withIndex()) {
            val heard = GameEngine.resourceOf(ex) == WOOD
            assertEquals("exercise $i: $ex", i % 2 == 1, heard)
        }
        val cards = c.cardIds.filterNotNull()
        assertEquals("no word asked twice: $cards", cards.size, cards.distinct().size)
        // its intro says what goes out and what may come in
        assertTrue(c.intro, c.intro.contains("100 🌾") && c.intro.contains("50 do 80 🪵"))
    }

    @Test fun `without a voice the listening half is words, and wisdom is asked as gathering asks it`() {
        assertEquals(List(6) { FOOD }, Trade.mix(FOOD, WOOD, canSpeak = false))
        assertEquals(listOf(STONE, FOOD, STONE, FOOD, STONE, FOOD), Trade.mix(STONE, WOOD, canSpeak = false))
        val quiet = Trade.challenge(village(), FOOD, WOOD, 100, Fixtures.pool(canSpeak = false), day0)!!
        assertEquals(6, quiet.exercises.size)
        assertTrue(quiet.exercises.none { it.heard != null })
        assertTrue(quiet.intro.contains("🔇"))
        // wisdom given: its half is the linden's (the modules' conversation, else the cards translated), paid as 📜
        val wise = Trade.challenge(village().copy(resources = mapOf(WISDOM to 200)), WISDOM, FOOD, 50, Fixtures.pool(), day0)!!
        assertEquals(listOf(WISDOM, WISDOM, WISDOM), wise.skills.filterIndexed { i, _ -> i % 2 == 0 })
        assertTrue(wise.exercises.filterIndexed { i, _ -> i % 2 == 0 }.all { it is Exercise.Translate })
        // what can't be traded has no run
        assertNull(Trade.challenge(village(wood = 500), FOOD, WOOD, 100, Fixtures.pool(), day0))
    }

    // --- the day it keeps ----------------------------------------------------------------------------

    @Test fun `today's trades survive the save, and a save from before has none`() {
        val (s, _) = Trade.settle(village(), FOOD, WOOD, 100, 6, 6, 6, day0, t0)
        val text = json.encodeToString(GameState.serializer(), s)
        assertEquals(s.trades, json.decodeFromString(GameState.serializer(), text).trades)
        assertTrue(text.contains("\"trades\""))
        val old = json.decodeFromString(GameState.serializer(), """{"seed":1,"age":"VAS","resources":{"FOOD":300}}""")
        assertNull(old.trades)
        assertTrue(Trade.limit(old, FOOD, WOOD, day0).available)
        // and a village that never traded doesn't write the field
        assertFalse(json.encodeToString(GameState.serializer(), village()).contains("\"trades\""))
    }
}
