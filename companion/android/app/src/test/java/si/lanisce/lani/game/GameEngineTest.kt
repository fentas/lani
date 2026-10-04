package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.ModuleInfo
import si.lanisce.lani.data.QuestMeta
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.json
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon

class GameEngineTest {
    private val pool = Fixtures.pool()
    private val t0 = noon(day0)

    private fun state(
        age: Age = Age.OGENJ,
        res: Map<Res, Int> = emptyMap(),
        vararg types: BuildingType,
    ) = GameEngine.newGame(1, t0).copy(
        age = age,
        resources = res,
        buildings = types.mapIndexed { i, t -> Building("b$i", t, i) },
        lastTick = day0.toString(),
        foundedOn = day0.toString(),
    )

    private fun all(n: Int) = Res.entries.associateWith { n }

    // --- resources ------------------------------------------------------------------------

    @Test fun `exercises map to their skill's resource`() {
        val e = GameEngine::resourceOf
        assertEquals(Res.FOOD, e(Exercise.Flashcard("a", "b")))
        assertEquals(Res.FOOD, e(Exercise.Choice("p", listOf("a"), 0)))
        assertEquals(Res.WOOD, e(Exercise.Choice("", listOf("a"), 0, audio = "živjo")))
        assertEquals(Res.WOOD, e(Exercise.Dictation("živjo", listOf("živjo"))))
        assertEquals(Res.STONE, e(Exercise.Cloze("_", listOf("a"))))
        assertEquals(Res.STONE, e(Exercise.Reorder("p", listOf("a"), listOf(listOf("a")))))
        assertEquals(Res.STONE, e(Exercise.Multi("p", listOf("a"), listOf(0))))
        assertEquals(Res.FOOD, e(Exercise.Translate("p", listOf("a"))))
        assertEquals(Res.WISDOM, e(Exercise.Translate("p", listOf("a"), grade = "claude")))
        assertEquals(Res.WISDOM, e(Exercise.Scenario("s", "p")))
        assertEquals(Res.WISDOM, e(Exercise.Free("p", "r")))
        assertEquals(Res.FOOD, e(Exercise.Unsupported("x")))
    }

    @Test fun `earn pays full, half and a little for effort`() {
        val s = state().copy(morale = 50) // neutral morale, one villager: multiplier 1.0
        val (after, got) = GameEngine.earn(
            s, listOf(Res.FOOD to Verdict.CORRECT, Res.FOOD to Verdict.ALMOST, Res.WOOD to Verdict.WRONG),
        )
        assertEquals(mapOf(Res.FOOD to 9, Res.WOOD to 1), got)
        assertEquals(9, after.res(Res.FOOD))
        assertEquals(mapOf(Res.FOOD to 9, Res.WOOD to 1), after.stats.totalEarned)
    }

    @Test fun `earn applies production multipliers`() {
        val s = state(Age.TABOR, emptyMap(), BuildingType.FIELD).copy(morale = 50)
        val (_, got) = GameEngine.earn(s, List(10) { Res.FOOD to Verdict.CORRECT })
        assertEquals(69, got[Res.FOOD]) // 60 × 1.15
        val happy = GameEngine.earn(state().copy(morale = 100), List(10) { Res.STONE to Verdict.CORRECT }).second
        assertEquals(66, happy[Res.STONE]) // +10 % from morale
    }

    @Test fun `earn stops at the storage cap and damaged buildings give nothing`() {
        val s = state(res = mapOf(Res.FOOD to 98)).copy(morale = 50)
        val (after, got) = GameEngine.earn(s, List(5) { Res.FOOD to Verdict.CORRECT })
        assertEquals(100, after.res(Res.FOOD)) // the campfire stores 100
        assertEquals(2, got[Res.FOOD])
        val broken = state(Age.TABOR, emptyMap(), BuildingType.FIELD).let {
            it.copy(morale = 50, buildings = it.buildings.map { b -> b.copy(damaged = true) })
        }
        assertEquals(60, GameEngine.earn(broken, List(10) { Res.FOOD to Verdict.CORRECT }).second[Res.FOOD])
    }

    // --- building ------------------------------------------------------------------------

    @Test fun `build pays, takes the next free plot and logs`() {
        val s = state(res = mapOf(Res.FOOD to 60, Res.WOOD to 40))
        val one = GameEngine.build(s, BuildingType.TENT, t0)
        assertEquals(1, one.buildings.size)
        assertEquals(0, one.buildings[0].plot)
        assertEquals(35, one.res(Res.FOOD))
        assertEquals(25, one.res(Res.WOOD))
        val two = GameEngine.build(one, BuildingType.TENT, t0)
        assertEquals(1, two.buildings[1].plot)
        assertTrue(two.log.last().text.contains("Tent"))
    }

    @Test fun `build refuses when plots are full, too poor, too early or at the limit`() {
        val full = state(Age.OGENJ, all(500), BuildingType.TENT, BuildingType.TENT)
        assertSame(full, GameEngine.build(full, BuildingType.FIELD, t0))
        val poor = state(Age.TABOR, emptyMap())
        assertSame(poor, GameEngine.build(poor, BuildingType.TENT, t0))
        val early = state(Age.TABOR, all(500))
        assertSame(early, GameEngine.build(early, BuildingType.CHURCH, t0))
        val lipa = state(Age.ZASELEK, all(300), BuildingType.LIPA)
        assertSame(lipa, GameEngine.build(lipa, BuildingType.LIPA, t0))

        val options = GameEngine.buildOptions(early).associateBy { it.type }
        assertTrue(options.getValue(BuildingType.TENT).available)
        assertNull(options.getValue(BuildingType.TENT).reason)
        assertFalse(options.getValue(BuildingType.CHURCH).available)
        assertTrue(options.getValue(BuildingType.CHURCH).reason!!.contains("Zaselek"))
        assertTrue(GameEngine.buildOptions(poor).first { it.type == BuildingType.TENT }.reason!!.contains("25 🌾"))
        assertEquals("+2 👥", options.getValue(BuildingType.TENT).effect)
    }

    @Test fun `attributes add up building effects`() {
        val s = state(Age.ZASELEK, emptyMap(), BuildingType.TENT, BuildingType.PALISADE, BuildingType.WATCHTOWER, BuildingType.KOZOLEC)
            .copy(villagers = 4)
        val a = GameEngine.attributes(s)
        assertEquals(4, a.populationCap)
        assertEquals(25, a.defence)
        assertEquals(650, a.caps[Res.FOOD]) // 500 at Zaselek + 150 from the hayrack
        assertEquals(500, a.caps[Res.STONE])
        assertEquals(6, a.foodUpkeep)
        assertEquals(4, a.woodUpkeep)
    }

    @Test fun `repair costs half and restores the building`() {
        val s = state(Age.ZASELEK, all(100), BuildingType.PALISADE).let {
            it.copy(buildings = it.buildings.map { b -> b.copy(damaged = true) })
        }
        assertEquals(mapOf(Res.FOOD to 15, Res.WOOD to 20, Res.STONE to 15), GameEngine.repairCost(s, "b0")) // half of 30 🌾 40 🪵 30 🪨
        val fixed = GameEngine.repair(s, "b0")
        assertFalse(fixed.buildings[0].damaged)
        assertEquals(80, fixed.res(Res.WOOD))
        assertTrue(GameEngine.repairCost(fixed, "b0").isEmpty())
        assertSame(fixed, GameEngine.repair(fixed, "b0"))
    }

    @Test fun `repair logs at the given time`() {
        val s = state(Age.ZASELEK, all(100), BuildingType.PALISADE).let {
            it.copy(buildings = it.buildings.map { b -> b.copy(damaged = true) })
        }
        val later = t0 + 5 * DAY_MS
        val fixed = GameEngine.repair(s, "b0", later)
        assertEquals(later, fixed.log.last().at)
        assertTrue(fixed.log.last().text.startsWith("Popravljeno"))
        // the old signature still works, with the last entry's time
        assertEquals(s.log.last().at, GameEngine.repair(s, "b0").log.last().at)
    }

    // --- ages ----------------------------------------------------------------------------

    @Test fun `advance needs buildings, resources and words`() {
        val bare = state(Age.OGENJ)
        val check = GameEngine.advanceCheck(bare, 5)
        assertEquals(Age.TABOR, check.next)
        assertFalse(check.ok)
        assertEquals(listOf("Šotor · Tent", "+20 🌾", "+10 🪵", "10 besed · words (you know 5)"), check.missing)

        // the camp: a tent (the other plot may hold a field)
        val ready = state(Age.OGENJ, mapOf(Res.FOOD to 30, Res.WOOD to 10), BuildingType.TENT, BuildingType.FIELD)
        assertTrue(GameEngine.advanceCheck(ready, 33).ok)
        val tabor = GameEngine.advance(ready, 33, t0)
        assertEquals(Age.TABOR, tabor.age)
        assertEquals(10, tabor.res(Res.FOOD))
        assertEquals(0, tabor.res(Res.WOOD))
        assertSame(bare, GameEngine.advance(bare, 33, t0))
    }

    @Test fun `the way to the next age is counted step by step`() {
        // the camp needs a tent, 20 🌾, 10 🪵 and 10 words: here the tent stands, 12 🌾, all the wood, 5 words
        val s = state(Age.OGENJ, mapOf(Res.FOOD to 12, Res.WOOD to 14), BuildingType.TENT)
        val check = GameEngine.advanceCheck(s, 5)
        assertEquals(listOf(AgeStep.Kind.BUILDING, AgeStep.Kind.RESOURCE, AgeStep.Kind.RESOURCE, AgeStep.Kind.WORDS), check.steps.map { it.kind })
        assertEquals(listOf(1 to 1, 12 to 20, 14 to 10, 5 to 10), check.steps.map { it.have to it.need })
        assertEquals(listOf(true, false, true, false), check.steps.map { it.done })
        assertEquals(Res.FOOD, check.steps[1].res)
        // shares: 1, 0.6, 1 (more than needed counts as done), 0.5
        assertEquals((1f + 0.6f + 1f + 0.5f) / 4, check.progress, 0.001f)
        assertEquals(1f, GameEngine.advanceCheck(state(Age.OGENJ, mapOf(Res.FOOD to 30, Res.WOOD to 10), BuildingType.TENT), 33).progress, 0.001f)
        // a damaged building counts for nothing and says so
        val broken = s.copy(buildings = s.buildings.map { it.copy(damaged = true) })
        assertEquals(0 to true, GameEngine.advanceCheck(broken, 5).steps[0].let { it.have to it.broken })
        assertTrue(GameEngine.advanceCheck(state(Age.MESTO), 5000).steps.isEmpty())
    }

    @Test fun `rusty words hold the next age back until a review polishes them`() {
        val ready = state(Age.OGENJ, mapOf(Res.FOOD to 30, Res.WOOD to 10), BuildingType.TENT)
        // no rust: no step, the camp is ready
        assertTrue(GameEngine.advanceCheck(ready, 33).steps.none { it.kind == AgeStep.Kind.RUSTY })
        // three words failed their last review: however many words are learned, the age waits for them
        val check = GameEngine.advanceCheck(ready, 33, rusty = 3)
        assertFalse(check.ok)
        val step = check.steps.single { it.kind == AgeStep.Kind.RUSTY }
        assertEquals("🔩" to "3 zarjavele besede · 3 rusty words", step.emoji to step.label)
        assertEquals(33 to 36, step.have to step.need) // the words learned of those and the rusty ones
        assertFalse(step.done)
        assertEquals(listOf("3 zarjavele besede: očisti jih (1 min) · 3 rusty words: polish them (1 min)"), check.missing)
        assertTrue("almost there, not a wall", check.progress > 0.9f)
        assertSame(ready, GameEngine.advance(ready, 33, t0, rusty = 3))
        // polished (a short review of them, now): the camp comes
        assertEquals(Age.TABOR, GameEngine.advance(ready, 36, t0, rusty = 0).age)
        // Slovene number forms, and a longer review for many
        assertEquals("1 zarjavela beseda: očisti jo (1 min) · 1 rusty word: polish it (1 min)", GameEngine.advanceCheck(ready, 33, rusty = 1).missing.single())
        assertEquals("2 zarjaveli besedi · 2 rusty words", GameEngine.advanceCheck(ready, 33, rusty = 2).steps.last().label)
        assertEquals("5 zarjavelih besed · 5 rusty words", GameEngine.advanceCheck(ready, 33, rusty = 5).steps.last().label)
        assertEquals(3, GameEngine.polishMinutes(14))
    }

    @Test fun `vas lists church, linden, dwellings, the word milestone and friends`() {
        val s = state(Age.ZASELEK, all(300) + (Res.WISDOM to 100), BuildingType.HUT, BuildingType.LIPA)
        val m = GameEngine.advanceCheck(s, 33).missing
        // Vas costs 500 🌾 320 🪵 130 🪨 110 📜, and for B2 (the default target) 300 learned words and three friends
        assertEquals(
            listOf("Cerkev · Church", "2× Koča/Hiša · Hut/House (1/2)", "+200 🌾", "+20 🪵", "+10 📜", "300 besed · words (you know 33)", "3 prijatelji · 3 friends (0/3)"),
            m,
        )
        val top = GameEngine.advanceCheck(state(Age.MESTO), 5000)
        assertNull(top.next)
        assertFalse(top.ok)
    }

    @Test fun `damaged required buildings block the next age`() {
        val s = state(Age.TABOR, all(200), BuildingType.PALISADE, BuildingType.FIELD).let {
            it.copy(
                buildings = it.buildings.map { b -> if (b.type == BuildingType.PALISADE) b.copy(damaged = true) else b },
                bonds = mapOf("micka" to si.lanisce.lani.game.villagers.Bond(points = 30)),
            )
        }
        assertEquals(listOf("Palisada · Palisade 🔧"), GameEngine.advanceCheck(s, 60).missing)
    }

    // --- upkeep --------------------------------------------------------------------------

    @Test fun `first tick only sets the day`() {
        val s = GameEngine.newGame(3, t0)
        val r = GameEngine.tick(s, day0, t0, pool).state
        assertEquals(day0.toString(), r.lastTick)
        assertEquals(day0.toString(), r.foundedOn)
        assertEquals(s.resources, r.resources)
        assertEquals(3, r.quests.size)
    }

    @Test fun `tick is idempotent on the same day`() {
        val s = state(Age.TABOR, all(100), BuildingType.TENT, BuildingType.TENT).copy(villagers = 3)
        val day = day0.plusDays(1)
        val once = GameEngine.tick(s, day, noon(day), pool)
        val twice = GameEngine.tick(once.state, day, noon(day) + 3_600_000, pool)
        assertEquals(once.state, twice.state)
        assertTrue(twice.news.isEmpty())
        assertEquals(95, once.state.res(Res.FOOD)) // 3 villagers eat 5
        assertEquals(97, once.state.res(Res.WOOD)) // tabor fire burns 3
    }

    @Test fun `upkeep catches up at most 14 days`() {
        val s = state(Age.TABOR, all(150), BuildingType.TENT, BuildingType.TENT).copy(villagers = 1)
        val a = GameEngine.tick(s, day0.plusDays(14), noon(day0.plusDays(14)), pool).state
        val b = GameEngine.tick(s, day0.plusDays(60), noon(day0.plusDays(60)), pool).state
        assertEquals(a.resources, b.resources)
        assertEquals(a.villagers, b.villagers)
        assertEquals(day0.plusDays(60).toString(), b.lastTick)
    }

    @Test fun `two weeks away is recoverable`() {
        val s = state(Age.VAS, mapOf(Res.FOOD to 30, Res.WOOD to 10), BuildingType.HOUSE, BuildingType.HOUSE, BuildingType.HUT)
            .copy(villagers = 11, morale = 60, fire = 60)
        val back = GameEngine.tick(s, day0.plusDays(14), noon(day0.plusDays(14)), Fixtures.pool(canSpeak = false))
        val r = back.state
        assertTrue("villagers ${r.villagers}", r.villagers >= 8)
        assertTrue(r.fire >= GameState.MIN_FIRE)
        assertTrue("morale ${r.morale}", r.morale >= SOFT_MORALE_FLOOR - 5)
        assertEquals(s.buildings, r.buildings)
        assertTrue(back.news.any { it.text.contains("14 days") })
    }

    @Test fun `fire never drops below the floor and a village never empties`() {
        val s = state(Age.ZASELEK).copy(villagers = 1, fire = 12, morale = 5)
        var r = s
        repeat(6) { i -> r = GameEngine.tick(r, day0.plusDays(i * 14L + 14), noon(day0.plusDays(i * 14L + 14)), pool).state }
        assertEquals(GameState.MIN_FIRE, r.fire)
        assertEquals(1, r.villagers)
    }

    @Test fun `low morale for three days drives a villager away, good days bring one`() {
        val sad = state(Age.TABOR, emptyMap(), BuildingType.HUT).copy(villagers = 4, morale = 20)
        val gone = GameEngine.tick(sad, day0.plusDays(3), noon(day0.plusDays(3)), pool).state
        assertEquals(3, gone.villagers)
        val fed = state(Age.TABOR, all(100), BuildingType.HUT).copy(villagers = 2, morale = 60)
        assertEquals(3, GameEngine.tick(fed, day0.plusDays(1), noon(day0.plusDays(1)), pool).state.villagers)
    }

    // --- events --------------------------------------------------------------------------

    private fun firstEvent(s: GameState, pool: ContentPool = this.pool, days: IntRange = 2..60): Pair<Int, GameEvent>? =
        days.firstNotNullOfOrNull { d ->
            val day = day0.plusDays(d.toLong())
            GameEngine.tick(s.copy(lastTick = day.minusDays(1).toString()), day, noon(day), pool).state.event?.let { d to it }
        }

    @Test fun `no events at the campfire or in the first two days`() {
        val camp = state(Age.OGENJ, all(80))
        assertNull(firstEvent(camp))
        val tabor = state(Age.TABOR, all(150))
        for (d in 0..1) {
            val day = day0.plusDays(d.toLong())
            assertNull(GameEngine.tick(tabor.copy(lastTick = day.minusDays(1).toString()), day, noon(day), pool).state.event)
        }
        val spawned = (2..200).count { d -> firstEvent(tabor.copy(seed = d.toLong()), days = d..d) != null }
        assertTrue("spawn rate $spawned/199", spawned in 25..70) // ~23 %
    }

    @Test fun `storms need a voice`() {
        val s = state(Age.VAS, all(300))
        val kinds = (0L..300L).mapNotNull { seed -> firstEvent(s.copy(seed = seed), Fixtures.pool(canSpeak = false), 5..5)?.second?.kind }
        assertTrue(kinds.isNotEmpty())
        assertTrue(EventKind.STORM !in kinds)
    }

    @Test fun `watchtower gives more time`() {
        val base = state(Age.ZASELEK, all(100)).copy(seed = 11)
        val (d, e) = firstEvent(base)!!
        assertEquals(36 * 3_600_000L, e.deadline - e.startedAt)
        val tower = base.copy(buildings = listOf(Building("t", BuildingType.WATCHTOWER, 0)))
        val e2 = firstEvent(tower, days = d..d)!!.second
        assertEquals(48 * 3_600_000L, e2.deadline - e2.startedAt)
    }

    private fun withEvent(s: GameState, kind: EventKind, strength: Int = 2) =
        s.copy(event = GameEvent("ev-x", kind, strength, t0, t0 + 36 * 3_600_000L))

    @Test fun `event challenge is timed with aligned lists`() {
        val s = withEvent(state(Age.ZASELEK, all(100)), EventKind.WOLVES, 3)
        val c = GameEngine.eventChallenge(s, pool)!!
        assertEquals(8, c.exercises.size)
        assertEquals(c.exercises.size, c.skills.size)
        assertEquals(c.exercises.size, c.cardIds.size)
        assertNotNull(c.timeLimitSeconds)
        assertTrue(c.timeLimitSeconds!! in 60..90)
        assertTrue(c.title.contains("Volkovi"))
        assertTrue("fallback is announced", c.intro.contains("No grammar drills"))
        assertTrue(c.skills.all { it == Res.STONE })
        assertNull(GameEngine.eventChallenge(state(), pool))
    }

    @Test fun `defence lowers the pass mark`() {
        val open = withEvent(state(Age.ZASELEK, all(100)), EventKind.WOLVES)
        val walled = withEvent(state(Age.ZASELEK, all(100), BuildingType.PALISADE, BuildingType.WATCHTOWER, BuildingType.SMITHY), EventKind.WOLVES)
        val a = GameEngine.eventChallenge(open, pool)!!.passMark
        val b = GameEngine.eventChallenge(walled, pool)!!.passMark
        assertTrue("$b < $a", b < a)
    }

    @Test fun `winning against wolves brings loot`() {
        val s = withEvent(state(Age.ZASELEK, all(100)), EventKind.WOLVES, 2)
        val (after, r) = GameEngine.resolveEvent(s, 7, 7, t0)
        assertTrue(r.won)
        assertEquals(mapOf(Res.STONE to 16, Res.FOOD to 12), r.rewards)
        assertNull(after.event)
        assertEquals(1, after.stats.eventsWon)
    }

    @Test fun `losing costs stores, may damage a building, never at tabor`() {
        val zaselek = withEvent(state(Age.ZASELEK, all(200), BuildingType.TENT, BuildingType.FIELD), EventKind.BEAR, 5)
        val (after, r) = GameEngine.resolveEvent(zaselek, 0, 7, t0)
        assertFalse(r.won)
        assertTrue(r.losses.getValue(Res.FOOD) > 0)
        assertEquals(200 - r.losses.getValue(Res.FOOD), after.res(Res.FOOD))
        assertEquals(1, after.stats.eventsLost)
        assertEquals(1, after.buildings.count { it.damaged }) // bear at strength 5 always damages
        assertEquals(r.damaged, after.buildings.filter { it.damaged }.map { it.id })

        repeat(20) { seed ->
            val tabor = withEvent(state(Age.TABOR, all(100), BuildingType.TENT).copy(seed = seed.toLong()), EventKind.WOLVES, 5)
            assertTrue(GameEngine.resolveEvent(tabor, 0, 7, t0).second.damaged.isEmpty())
        }
    }

    @Test fun `an event left early is judged against all its questions`() {
        val s = withEvent(state(Age.ZASELEK, all(100)), EventKind.WOLVES, 2)
        val total = GameEngine.eventChallenge(s, pool)!!.exercises.size
        // three right answers, then the learner leaves: not enough for the pass mark of the whole run
        val (after, r) = GameEngine.resolveEvent(s, 3, total, t0)
        assertFalse(r.won)
        assertNull(after.event)
        assertEquals(1, after.stats.eventsLost)
    }

    @Test fun `an expired event resolves as lost on the next tick`() {
        val s = withEvent(state(Age.ZASELEK, all(200)), EventKind.STORM, 2)
        val later = day0.plusDays(2)
        val r = GameEngine.tick(s, later, noon(later), pool)
        assertTrue(r.state.event?.id != "ev-x")
        assertEquals(1, r.state.stats.eventsLost)
        assertTrue(r.news.any { it.text.contains("too late") })
        // before the deadline nothing happens
        val early = GameEngine.tick(s.copy(lastTick = day0.toString()), day0, t0 + 3_600_000, pool).state
        assertEquals("ev-x", early.event?.id)
    }

    @Test fun `merchant trades favourably and costs nothing when lost`() {
        val s = withEvent(state(Age.ZASELEK, mapOf(Res.FOOD to 200, Res.WOOD to 50, Res.STONE to 10, Res.WISDOM to 60)), EventKind.MERCHANT, 2)
        val (after, r) = GameEngine.resolveEvent(s, 6, 7, t0)
        assertEquals(mapOf(Res.FOOD to 30), r.losses)
        assertEquals(mapOf(Res.STONE to 60), r.rewards)
        assertEquals(70, after.res(Res.STONE))
        val (kept, lost) = GameEngine.resolveEvent(s, 0, 7, t0)
        assertTrue(lost.losses.isEmpty() && lost.rewards.isEmpty())
        assertEquals(s.resources, kept.resources)
    }

    @Test fun `festival always rewards, more when you do well`() {
        val s = withEvent(state(Age.VAS, all(100)).copy(morale = 70), EventKind.FESTIVAL, 2)
        val (good, won) = GameEngine.resolveEvent(s, 7, 7, t0)
        val (meh, lost) = GameEngine.resolveEvent(s, 0, 7, t0)
        assertTrue(won.rewards.values.sum() > lost.rewards.values.sum())
        assertTrue(lost.rewards.values.sum() > 0)
        assertTrue(good.morale > meh.morale && meh.morale > 70)
        assertEquals(0, meh.stats.eventsLost)
    }

    // --- quests & challenges ---------------------------------------------------------------

    @Test fun `keeps three local quests that expire after three days`() {
        var s = GameEngine.tick(GameEngine.newGame(5, t0), day0, t0, pool).state
        val open = s.quests.filter { it.source == QuestSource.LOCAL }
        assertEquals(3, open.size)
        assertEquals(3, open.map { it.giver }.distinct().size)
        assertTrue(open.all { it.cardIds.size in 5..6 && it.reward.getValue(it.skill) > 0 })

        val q = open.first()
        s = GameEngine.completeQuest(s, q.id, 5, 6, t0).first
        s = GameEngine.tick(s, day0.plusDays(1), noon(day0.plusDays(1)), pool).state
        assertEquals(3, s.quests.count { !it.done })
        assertTrue(s.quests.none { it.id == q.id })

        s = GameEngine.tick(s, day0.plusDays(4), noon(day0.plusDays(4)), pool).state
        assertTrue(s.quests.none { it.id in open.map { o -> o.id } })
        assertEquals(3, s.quests.size)
    }

    @Test fun `completing a quest pays, failing gives a consolation and keeps it open`() {
        val s = GameEngine.tick(state(Age.TABOR).copy(lastTick = ""), day0, t0, pool).state
        val q = s.quests.first()
        val (won, r) = GameEngine.completeQuest(s, q.id, 4, 6, t0)
        assertTrue(r.won)
        assertEquals(q.reward, r.rewards)
        assertEquals(1, won.stats.questsDone)
        assertTrue(won.quests.first { it.id == q.id }.done)

        val (tried, c) = GameEngine.completeQuest(s, q.id, 3, 6, t0)
        assertFalse(c.won)
        assertEquals(q.reward.mapValues { it.value / 5 }, c.rewards)
        assertFalse(tried.quests.first { it.id == q.id }.done)
    }

    @Test fun `quest challenge uses the quest's cards, untimed`() {
        val s = GameEngine.tick(GameEngine.newGame(9, t0), day0, t0, pool).state
        for (q in s.quests) {
            val c = GameEngine.questChallenge(s, q.id, pool)!!
            assertNull(c.timeLimitSeconds)
            assertEquals(c.exercises.size, c.skills.size)
            assertEquals(c.exercises.size, c.cardIds.size)
            assertTrue(c.cardIds.filterNotNull().all { it in q.cardIds })
            assertTrue(c.passMark in 3..4)
        }
        assertNull(GameEngine.questChallenge(s, "nope", pool))
    }

    @Test fun `a request whose card is gone (merged or split by the tutor) gets another in its place`() {
        val s = GameEngine.tick(GameEngine.newGame(9, t0), day0, t0, pool).state
        val q = s.quests.first { it.source == QuestSource.LOCAL && it.cardIds.size >= 2 }
        val gone = s.copy(quests = s.quests.map { if (it.id == q.id) it.copy(cardIds = it.cardIds.dropLast(1) + "vocab_ja_ne_merged_away") else it })
        val full = GameEngine.questChallenge(s, q.id, pool)!!
        val topped = GameEngine.questChallenge(gone, q.id, pool)!!
        assertEquals(full.exercises.size, topped.exercises.size)
        assertTrue(topped.cardIds.filterNotNull().none { it == "vocab_ja_ne_merged_away" })
    }

    private val topical = Fixtures.cards.map { c ->
        c.copy(category = when (c.id) {
            "vocab_dober_dan", "vocab_zivjo", "vocab_dobro_jutro", "vocab_nasvidenje", "vocab_kako_ste" -> "greetings"
            "vocab_hvala", "vocab_prosim", "vocab_oprostite", "vocab_opravicujem_se", "vocab_ni_za_kaj" -> "politeness"
            "vocab_jaz_sem", "vocab_od_kod_ste" -> "introductions"
            "vocab_moj_moja" -> "grammar_possessives"
            else -> null
        })
    }

    @Test fun `quest cards follow the template's topic, weakest first, else fall back`() {
        val r = kotlin.random.Random(3)
        val (cards, cat) = Quests.cardsFor(listOf("politeness"), topical, 5, r)
        assertEquals("politeness", cat)
        assertEquals(5, cards.size)
        assertTrue(cards.all { it.category == "politeness" })
        // three greetings-only? not enough on topic for six: topped up from the rest, on-topic first
        val (mixed, cat2) = Quests.cardsFor(listOf("greetings"), topical, 6, r)
        assertEquals("greetings", cat2)
        assertEquals(5, mixed.count { it.category == "greetings" })
        assertEquals(6, mixed.distinctBy { it.id }.size)
        // too few on topic (2 introductions): weakest-first from all cards, no category
        val (fallback, none) = Quests.cardsFor(listOf("introductions"), topical, 5, r)
        assertNull(none)
        assertEquals(5, fallback.size)
        assertTrue(Quests.fits("grammar_possessives", listOf("grammar")))
        assertTrue(Quests.fits("Greetings", listOf("greetings")))
        assertFalse(Quests.fits("greetings", listOf("greet")))
        assertFalse(Quests.fits(null, listOf("greetings")))
    }

    @Test fun `new local quests prefer stories that fit the learner's cards`() {
        val p = ContentPool(topical, canSpeak = true)
        repeat(10) { seed ->
            val s = GameEngine.tick(GameEngine.newGame(seed.toLong(), t0), day0, t0, p).state
            val open = s.quests.filter { it.source == QuestSource.LOCAL }
            assertEquals(3, open.size)
            // enough greetings and politeness cards: every quest is built around a topic, mostly from on-topic cards
            val social = setOf("greetings", "politeness", "introductions")
            val byId = topical.associateBy { it.id }
            for (q in open) {
                assertTrue("${q.title}: ${q.category}", q.category in social)
                assertTrue(q.cardIds.count { byId.getValue(it).category in social } >= Quests.MIN_TOPIC_CARDS)
            }
        }
        // without categories nothing changes: quests still come, with no topic
        val plain = GameEngine.tick(GameEngine.newGame(1, t0), day0, t0, pool).state.quests
        assertTrue(plain.isNotEmpty() && plain.all { it.category == null })
    }

    @Test fun `tutor quests come from module quest blocks`() {
        val modules = listOf(
            ModuleInfo("m-clitics", 2, "Clitics at the inn", "A1",
                quest = QuestMeta("Gostilničarka Vida", "🍷", "Vida needs help", skill = "stone", reward = mapOf("stone" to 60, "Wisdom" to 20, "gold" to 5))),
            ModuleInfo("m-plain", 1, "Plain", "A1"),
            ModuleInfo("m-noskill", 1, "Numbers", "A1", quest = QuestMeta("Stari Janez", story = "Count sheep")),
        )
        val s = GameEngine.withTutorQuests(state(Age.ZASELEK), modules, t0)
        val tutor = s.quests.filter { it.source == QuestSource.TUTOR }
        assertEquals(listOf("tutor-m-clitics-v2", "tutor-m-noskill-v1"), tutor.map { it.id })
        val vida = tutor[0]
        assertEquals(Res.STONE, vida.skill)
        assertEquals(mapOf(Res.STONE to 60, Res.WISDOM to 20), vida.reward)
        assertEquals("m-clitics", vida.moduleId)
        assertEquals(mapOf(Res.FOOD to 64), tutor[1].reward) // default 40 × zaselek
        assertNull(GameEngine.questChallenge(s, vida.id, pool))

        // done tutor quests stay out; removed modules drop their quests; new versions come back
        val done = GameEngine.completeQuest(s, vida.id, 5, 5, t0).first
        val again = GameEngine.withTutorQuests(done, modules, t0)
        assertEquals(1, again.quests.count { it.id == vida.id })
        assertTrue(again.quests.first { it.id == vida.id }.done)
        val gone = GameEngine.withTutorQuests(again, modules.take(2), t0)
        assertEquals(listOf("tutor-m-clitics-v2"), gone.quests.filter { it.source == QuestSource.TUTOR }.map { it.id })
        val bumped = GameEngine.withTutorQuests(gone, listOf(modules[0].copy(version = 3)), t0)
        assertEquals(listOf("tutor-m-clitics-v3"), bumped.quests.map { it.id })
    }

    @Test fun `gathering mostly earns the chosen resource`() {
        val s = state(Age.TABOR)
        for (r in Res.entries) {
            val c = GameEngine.gatherChallenge(s, r, pool)
            assertTrue(c.exercises.size in 6..8)
            assertEquals(c.exercises.size, c.skills.size)
            assertEquals(c.exercises.size, c.cardIds.size)
            assertNull(c.timeLimitSeconds)
            assertTrue("$r: ${c.skills}", c.skills.count { it == r } * 2 > c.skills.size)
        }
        val wood = GameEngine.gatherChallenge(s, Res.WOOD, pool)
        assertTrue(wood.exercises.all { it is Exercise.Dictation || (it is Exercise.Choice && it.audio != null) })
        val mute = GameEngine.gatherChallenge(s, Res.WOOD, Fixtures.pool(canSpeak = false))
        assertTrue(mute.intro.contains("No voice"))
        assertTrue(mute.exercises.none { it is Exercise.Dictation || (it is Exercise.Choice && it.audio != null) })
    }

    @Test fun `module exercises are used for grammar and wisdom`() {
        val modules = listOf(
            "m1" to Exercise.Cloze("Moja partnerka ___ iz Gorice.", listOf("je")),
            "m1" to Exercise.Reorder("I am Jan", listOf("sem", "Jan"), listOf(listOf("Sem", "Jan"))),
            "m1" to Exercise.Multi("p", listOf("a", "b"), listOf(0)),
            "m1" to Exercise.Cloze("Iz ___.", listOf("Berlina")),
            "m2" to Exercise.Scenario("inn", "Order a coffee"),
            "m2" to Exercise.Unsupported("future"),
            "m2" to Exercise.Flashcard("a", "b"),
        )
        val p = Fixtures.pool(modules = modules)
        val stone = GameEngine.gatherChallenge(state(), Res.STONE, p)
        assertTrue(stone.exercises.take(7).count { it is Exercise.Cloze || it is Exercise.Multi } >= 3)
        // Module drills exist, so don't claim there are none (the rest is quietly filled from cards).
        assertFalse(stone.intro.contains("No grammar drills"))
        val noModules = GameEngine.gatherChallenge(state(), Res.STONE, Fixtures.pool())
        assertTrue(noModules.intro.contains("No grammar drills"))
        val wisdom = GameEngine.gatherChallenge(state(), Res.WISDOM, p)
        assertTrue(wisdom.exercises.any { it is Exercise.Scenario })
        assertTrue(wisdom.exercises.none { it is Exercise.Unsupported })
        assertTrue(wisdom.skills.all { it == Res.WISDOM })
        // Each exercise says where it is from (for a question to the tutor): its module, else its card.
        for (c in listOf(stone, wisdom)) {
            assertEquals(c.exercises.size, c.moduleIds.size)
            c.exercises.indices.forEach { i ->
                val from = modules.filter { it.second == c.exercises[i] }.map { it.first }
                if (from.isNotEmpty()) assertTrue("$i", c.moduleIds[i] in from) else assertNull("$i", c.moduleIds[i])
                assertTrue("$i: a module's or a card's", (c.moduleIds[i] == null) != (c.cardIds[i] == null))
            }
        }
    }

    @Test fun `a card's notes never show in a challenge's prompt or options`() {
        // The note names the answer ("da"): reviews ask with the meaning only (ReviewPlanner.meaning), and so do challenges.
        val cards = listOf(
            ReviewCard("vocab_da", "da / ja", "yes (da = written/standard, ja = spoken)"),
            ReviewCard("vocab_dober_dan", "dober dan", "good day (polite hello)"),
        ) + Fixtures.cards
        val shown = { c: Challenge ->
            c.exercises.flatMap { e ->
                when (e) {
                    is Exercise.Translate -> listOf(e.prompt)
                    is Exercise.Reorder -> listOf(e.prompt)
                    is Exercise.Choice -> e.options + e.prompt
                    else -> emptyList()
                }
            }
        }
        for (r in Res.entries) {
            val c = GameEngine.gatherChallenge(state(), r, ContentPool(cards, canSpeak = true))
            val texts = shown(c)
            assertTrue("$r: ${texts.filter { "(" in it }}", texts.none { "written/standard" in it || "polite hello" in it })
        }
        val wisdom = GameEngine.gatherChallenge(state(), Res.WISDOM, ContentPool(cards.take(2), canSpeak = true))
        assertTrue(wisdom.exercises.filterIsInstance<Exercise.Translate>().any { it.prompt == "yes" && it.accept == listOf("da", "ja") })
    }

    @Test fun `empty pool gives an empty but valid challenge`() {
        val c = GameEngine.gatherChallenge(state(), Res.FOOD, ContentPool(emptyList()))
        assertTrue(c.exercises.isEmpty() && c.skills.isEmpty() && c.cardIds.isEmpty())
    }

    // --- determinism & persistence ---------------------------------------------------------

    @Test fun `same inputs give the same outputs`() {
        fun run(): GameState {
            var s = GameEngine.newGame(77, t0).copy(age = Age.TRG, resources = all(300))
            for (d in 0..20L) s = GameEngine.tick(s, day0.plusDays(d), noon(day0.plusDays(d)), pool).state
            return s
        }
        assertEquals(run(), run())
        val s = state(Age.ZASELEK)
        assertEquals(GameEngine.gatherChallenge(s, Res.STONE, pool), GameEngine.gatherChallenge(s, Res.STONE, pool))
        val other = GameEngine.newGame(78, t0).copy(age = Age.TRG, resources = all(300))
        var o = other
        for (d in 0..20L) o = GameEngine.tick(o, day0.plusDays(d), noon(day0.plusDays(d)), pool).state
        assertTrue(o.quests != run().quests || o.event != run().event)
    }

    // --- the land ----------------------------------------------------------------------------

    @Test fun `a gathering run leaves its mark on the land, dated, with a chronicle line`() {
        val s = state(Age.TABOR)
        val wood = GameEngine.gathered(s, Res.WOOD, correct = 4, total = 7, today = day0, now = t0 + 1)
        assertEquals(listOf(day0.toString()), wood.world.felled)
        assertEquals("🪓" to "Posekal si drevo · You felled a tree", wood.log.last().let { it.emoji to it.text })
        // a very good run fells two; stone and food mark their own lists
        val great = GameEngine.gathered(wood, Res.WOOD, correct = 7, total = 7, today = day0.plusDays(1), now = t0 + 2)
        assertEquals(listOf(day0.toString(), day0.plusDays(1).toString(), day0.plusDays(1).toString()), great.world.felled)
        assertTrue(great.log.last().text.contains("dve drevesi"))
        val stone = GameEngine.gathered(great, Res.STONE, 3, 7, day0, t0 + 3)
        assertEquals(listOf(day0.toString()), stone.world.quarried)
        assertEquals(great.world.felled, stone.world.felled)
        val food = GameEngine.gathered(stone, Res.FOOD, 2, 7, day0, t0 + 4)
        assertEquals(listOf(day0.toString()), food.world.picked)
        assertEquals("🧺", food.log.last().emoji)
        // nothing for wisdom, nor for a run given up before a right answer
        assertSame(food, GameEngine.gathered(food, Res.WISDOM, 7, 7, day0, t0 + 5))
        assertSame(food, GameEngine.gathered(food, Res.WOOD, 0, 2, day0, t0 + 5))
        assertEquals(2, GameEngine.gatherMarks(3, 3)) // all of a short run is a great run too
        assertEquals(1, GameEngine.gatherMarks(1, 7))
        assertEquals(0, GameEngine.gatherMarks(0, 0))
    }

    @Test fun `land marks are capped, the oldest going first`() {
        var s = state(Age.TABOR)
        for (d in 0 until WorldMarks.MAX + 5) s = GameEngine.gathered(s, Res.WOOD, 3, 7, day0.plusDays(d.toLong()), t0 + d)
        assertEquals(WorldMarks.MAX, s.world.felled.size)
        assertEquals(day0.plusDays(5).toString(), s.world.felled.first())
        assertEquals(day0.plusDays((WorldMarks.MAX + 4).toLong()).toString(), s.world.felled.last())
        // and they survive the state's json round trip
        val text = json.encodeToString(GameState.serializer(), s)
        assertEquals(s.world, json.decodeFromString(GameState.serializer(), text).world)
    }

    @Test fun `game state survives a json round trip`() {
        var s = GameEngine.tick(GameEngine.newGame(4, t0), day0, t0, pool).state
        s = withEvent(s.copy(age = Age.ZASELEK, buildings = listOf(Building("b", BuildingType.KOZOLEC, 0, damaged = true))), EventKind.BEAR)
        s = GameEngine.earn(s, listOf(Res.WISDOM to Verdict.CORRECT)).first
        val text = json.encodeToString(GameState.serializer(), s)
        assertEquals(s, json.decodeFromString(GameState.serializer(), text))
        // older saves without the newer fields still load
        val old = json.decodeFromString(GameState.serializer(), """{"seed":1,"age":"TABOR","unknown":true}""")
        assertEquals(Age.TABOR, old.age)
        assertEquals("", old.foundedOn)
    }

    @Test fun `the last free plots are kept for what the next age needs`() {
        // Tabor has five plots; Zaselek needs a palisade (round the clearing: it takes none) and a field
        val taken = GameState(age = Age.TABOR, resources = Res.entries.associateWith { 500 }, buildings = listOf(
            Building("a", BuildingType.TENT, 0), Building("b", BuildingType.TENT, 1), Building("c", BuildingType.HUT, 2),
            Building("d", BuildingType.HUT, 3),
        ))
        val opts = GameEngine.buildOptions(taken).associateBy { it.type }
        assertFalse(opts.getValue(BuildingType.HUT).available)
        assertTrue(opts.getValue(BuildingType.HUT).reason!!.contains("Njiva"))
        assertFalse(opts.getValue(BuildingType.HUT).reason!!.contains("Palisada"))
        assertTrue(opts.getValue(BuildingType.PALISADE).available)
        assertTrue(opts.getValue(BuildingType.FIELD).available)
        val walled = GameEngine.build(taken, BuildingType.PALISADE, t0)
        assertEquals(NO_PLOT, walled.buildings.last().plot)
        assertEquals(4, walled.plotsTaken)
        // the palisade took no plot: the last one is still the field's
        assertTrue(GameEngine.buildOptions(walled).first { it.type == BuildingType.FIELD }.available)
        assertFalse(GameEngine.buildOptions(walled).first { it.type == BuildingType.HUT }.available)
        assertEquals(4, GameEngine.build(walled, BuildingType.FIELD, t0).buildings.last().plot)
    }

    @Test fun `the palisade goes up round the clearing even when every plot is built`() {
        val full = GameState(age = Age.TABOR, resources = Res.entries.associateWith { 500 }, buildings = listOf(
            Building("a", BuildingType.TENT, 0), Building("b", BuildingType.TENT, 1), Building("c", BuildingType.HUT, 2),
            Building("d", BuildingType.HUT, 3), Building("e", BuildingType.FIELD, 4),
        ))
        val opts = GameEngine.buildOptions(full).associateBy { it.type }
        assertFalse(opts.getValue(BuildingType.KOZOLEC).available) // no free plot
        assertTrue(opts.getValue(BuildingType.PALISADE).available)
        val walled = GameEngine.build(full, BuildingType.PALISADE, t0)
        assertEquals(listOf(0, 1, 2, 3, 4, NO_PLOT), walled.buildings.map { it.plot })
        assertEquals(5, walled.plotsTaken)
    }

    @Test fun `an older save's palisade gives its plot back`() {
        // before the palisade stood round the clearing it stood on a plot, here plot 1 of Tabor's five
        val old = state(Age.TABOR, all(500), BuildingType.TENT, BuildingType.PALISADE, BuildingType.HUT)
        assertEquals(1, old.buildings[1].plot)
        // the engine and the counts already look past it …
        assertEquals(2, old.plotsTaken)
        assertEquals(1, GameEngine.build(old, BuildingType.FIELD, t0).buildings.last().plot)
        // … and the next tick moves it off its plot for good, the rest as it was
        val ticked = GameEngine.tick(old, day0, t0, pool).state
        assertEquals(listOf(0, NO_PLOT, 2), ticked.buildings.map { it.plot })
        assertEquals(old.buildings.map { it.copy(plot = 0) }, ticked.buildings.map { it.copy(plot = 0) })
        assertEquals(1, GameEngine.build(ticked, BuildingType.FIELD, t0).buildings.last().plot)
        // a save that has it right stays the same object
        assertSame(ticked.buildings, GameEngine.tick(ticked, day0, t0, pool).state.buildings)
    }
}
