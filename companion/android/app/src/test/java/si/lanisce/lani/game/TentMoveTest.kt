package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.game.BuildingType.FIELD
import si.lanisce.lani.game.BuildingType.HOUSE
import si.lanisce.lani.game.BuildingType.HUT
import si.lanisce.lani.game.BuildingType.KOZOLEC
import si.lanisce.lani.game.BuildingType.PALISADE
import si.lanisce.lani.game.BuildingType.TENT
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.parseVillagers
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.villagers.VillagerLogic
import java.io.File

/**
 * "Šotor na novo mesto · A new place for the tent" (companion/GAME.md, "The tent moves to the pond"): once people sleep
 * under a roof, Pastir Luka asks, once, to move the tent to the pond; his description is the run; passed, the tent leaves
 * its plot and stands at the pond, a tent for every rule still.
 */
class TentMoveTest {
    private val day = Fixtures.day0
    private val t0 = Fixtures.noon(day)
    private val pool = Fixtures.pool()

    /** A hamlet with a tent on plot 1 and a hut, Luka and Micka living here. */
    private fun hamlet(vararg types: BuildingType = arrayOf(FIELD, TENT, HUT, KOZOLEC), age: Age = Age.ZASELEK, residents: List<String> = listOf("luka", "micka")) =
        GameEngine.newGame(7, t0).copy(
            age = age, resources = Res.entries.associateWith { 150 }, villagers = 4, morale = 60,
            buildings = types.mapIndexed { i, t -> Building("b$i", t, i) } + Building("p", PALISADE, NO_PLOT),
            residents = residents.map { Resident(it, day.toString()) },
            lastTick = day.minusDays(1).toString(), foundedOn = day.minusDays(30).toString(), log = emptyList(),
        )

    private fun tick(s: GameState, d: java.time.LocalDate = day, present: Set<String>? = null) =
        GameEngine.tick(s.copy(lastTick = d.minusDays(1).toString()), d, Fixtures.noon(d), pool.copy(present = present)).state

    private fun asked(s: GameState) = s.quests.firstOrNull { it.id == TentMove.ID }

    // --- when Luka asks ------------------------------------------------------------------------------------------

    @Test fun `Luka asks once people sleep under a roof, from the hamlet on, while he lives here`() {
        assertTrue(TentMove.due(hamlet()))
        assertTrue(TentMove.due(hamlet(FIELD, TENT, HOUSE)))
        // no hut nor house: people still need the tent
        assertFalse(TentMove.due(hamlet(FIELD, TENT, KOZOLEC)))
        // a camp with a hut: not yet (from Zaselek)
        assertFalse(TentMove.due(hamlet(age = Age.TABOR)))
        // no tent to move
        assertFalse(TentMove.due(hamlet(FIELD, HUT, KOZOLEC)))
        // Luka doesn't live here, or nobody has a name yet (an older bridge)
        assertFalse(TentMove.due(hamlet(residents = listOf("micka"))))
        assertFalse(TentMove.due(hamlet(residents = emptyList())))
        // a village of later ages asks too (an older save)
        assertTrue(TentMove.due(hamlet(age = Age.MESTO)))
    }

    @Test fun `the request comes with a new day, once, and the chronicle says so`() {
        val s = tick(hamlet())
        val q = asked(s)!!
        assertEquals("Pastir Luka", q.giver)
        assertEquals("🐑", q.emoji)
        assertEquals("Šotor na novo mesto · A new place for the tent", q.title)
        assertTrue(q.story, q.story.startsWith("Ljudje zdaj spijo pod pravo streho"))
        assertEquals(QuestSource.LOCAL, q.source)
        assertEquals(Res.WOOD, q.skill)
        // it pays like a request: 30 × the age of its skill, 10 × of what the village is shortest of
        assertEquals((Quests.BASE_REWARD * Quests.ageMultiplier(Age.ZASELEK)).toInt(), q.reward.getValue(Res.WOOD))
        assertEquals(2, q.reward.size)
        assertEquals(0L, q.expiresAt)
        assertTrue(s.log.any { it.emoji == "🐑" && it.text == "Pastir Luka te prosi za pomoč · asks for your help" })
        // and only once: the next days bring no second one
        val later = tick(tick(s, day.plusDays(1)), day.plusDays(2))
        assertEquals(1, later.quests.count { it.id == TentMove.ID })
        // not on a tick the same day for a village that got its hut today
        val same = GameEngine.tick(hamlet().copy(lastTick = day.toString()), day, t0, pool).state
        assertNull(asked(same))
    }

    @Test fun `it waits, never expiring, while Luka is away, and is not one of the three`() {
        val s = tick(hamlet())
        assertNotNull(asked(s))
        // a week on, it's still open (the others expired and came anew)
        var w = s
        for (i in 1..7) w = tick(w, day.plusDays(i.toLong()))
        assertNotNull(asked(w))
        assertFalse(asked(w)!!.done)
        // Luka away for a day: the request waits for him (a request of someone else who's away goes)
        val away = tick(s, day.plusDays(1), present = setOf("Babica Micka"))
        assertNotNull(asked(away))
        // three requests besides it, none of them Luka's (he has his)
        val open = w.quests.filter { it.source == QuestSource.LOCAL && !it.done && it.id != TentMove.ID }
        assertEquals(Quests.OPEN_LOCAL, open.size)
        assertTrue(open.none { it.giver == "Pastir Luka" })
    }

    // --- the run --------------------------------------------------------------------------------------------------

    @Test fun `the run is Luka's description, heard in his voice or read, and every answer is what he said`() {
        val s = tick(hamlet())
        val t = Cultures.current.quests.tentMove!!
        val said = t.said.target
        assertEquals("Šotor bo stal ob ribniku, pod veliko smreko, blizu pomola. Pred šotorom bo ognjišče, drva pa bodo za šotorom.", said)
        val c = GameEngine.questChallenge(s, TentMove.ID, pool)!!
        assertEquals("🐑 Šotor na novo mesto · A new place for the tent", c.title)
        assertEquals(t.questions.size, c.exercises.size)
        assertEquals(4, c.exercises.size)
        assertEquals(3, c.passMark)
        assertTrue(c.skills.all { it == Res.WOOD })
        assertTrue(c.cardIds.all { it == null })
        for ((i, e) in c.exercises.withIndex()) {
            val ch = e as Exercise.Choice
            val q = t.questions[i]
            assertEquals(said, ch.audio) // heard: the question is on the screen, his words are not
            assertEquals(q.ask.bi(), ch.prompt)
            assertEquals("Poslušaj Luko · Listen to Luka", ch.instruction)
            assertEquals(q.options.toSet(), ch.options.toSet())
            assertEquals(q.options.first(), ch.options[ch.answer])
            assertEquals(q.explain.bi(), ch.explain)
            // the answer is what he said (its last word's stem: "blizu pomola" → pomol)
            val answer = q.options.first().substringAfterLast(' ')
            assertTrue("${q.ask.target}: $answer", answer.take(4) in said)
        }
        val asks = c.exercises.map { (it as Exercise.Choice).prompt.substringBefore(" · ") }
        assertEquals(listOf("Kje bo stal šotor?", "Pod čim bo stal?", "Kaj bo blizu šotora?", "Kje bo ognjišče?"), asks)
        // the explanations name the preposition and its case
        val explained = t.questions.map { it.explain.target }
        for (case in listOf("mestnik", "orodnik", "rodilnik")) assertTrue(case, explained.any { case in it })
        // without a voice, it's read
        val read = GameEngine.questChallenge(s, TentMove.ID, pool.copy(canSpeak = false))!!
        for (e in read.exercises) {
            val ch = e as Exercise.Choice
            assertNull(ch.audio)
            assertTrue(ch.prompt, ch.prompt.startsWith("🐑 Luka: »$said«\n\n"))
            assertEquals("Preberi, kaj pravi Luka · Read what Luka says", ch.instruction)
        }
        // the same run every time
        assertEquals(c.exercises, GameEngine.questChallenge(s, TentMove.ID, pool)!!.exercises)
    }

    @Test fun `every question has four pictured places or things, the answer among them once`() {
        for (id in listOf("primorska", "friuli")) {
            val t = Cultures.load(id).quests.tentMove!!
            assertEquals(id, 4, t.questions.size)
            for (q in t.questions) {
                assertEquals(q.options.toString(), 4, q.options.distinct().size)
                // pictures on all options or on none: a picture never gives the answer away
                val pictured = q.options.map { !it.first().isLetter() }
                assertTrue(q.options.toString(), pictured.all { it } || pictured.none { it })
            }
        }
    }

    // --- the move -------------------------------------------------------------------------------------------------

    @Test fun `passed, the tent leaves its plot for the pond, Luka thanks, and the chronicle records it`() {
        val s = tick(hamlet())
        val tent = TentMove.tent(s)!!
        assertEquals(1, tent.plot)
        val (after, r) = GameEngine.completeQuest(s, TentMove.ID, 3, 4, t0, giverId = "luka")
        assertTrue(r.won)
        assertEquals(Catalog.HELP_QUEST, r.help)
        assertEquals("tolminc", r.thanks?.id)
        assertEquals(NO_PLOT, after.buildings.first { it.id == tent.id }.plot)
        assertEquals(tent.copy(plot = NO_PLOT), TentMove.atPond(after))
        assertTrue(TentMove.moved(after))
        assertEquals("Luka je šotor prestavil k ribniku, pod veliko smreko. · Luka moved the tent to the pond, under the big spruce.", after.log.last().text)
        assertEquals("⛺", after.log.last().emoji)
        assertTrue(after.quests.first { it.id == TentMove.ID }.done)
        // it pays like a request
        assertEquals(s.res(Res.WOOD) + s.quests.first { it.id == TentMove.ID }.reward.getValue(Res.WOOD), after.res(Res.WOOD))
        // the plot is free: a village with every other plot built builds on it
        assertEquals(s.plotsTaken - 1, after.plotsTaken)
        val full = hamlet(TENT, HUT, FIELD, KOZOLEC, FIELD, BuildingType.BEEHIVE, BuildingType.WELL, KOZOLEC, FIELD).copy(resources = Res.entries.associateWith { 900 })
        assertFalse(GameEngine.buildOptions(full).first { it.type == HUT }.available)
        val built = GameEngine.build(TentMove.move(full, t0), HUT, t0)
        assertEquals(0, built.buildings.last().plot)
        assertEquals(HUT, built.buildings.last().type)
        // and the request doesn't come again
        val later = tick(after, day.plusDays(1))
        assertNull(later.quests.firstOrNull { it.id == TentMove.ID && !it.done })
        assertFalse(TentMove.due(later))
        assertEquals(NO_PLOT, TentMove.atPond(later)!!.plot) // the palisade's migration leaves it where it is
    }

    @Test fun `not passed, the tent stays where it is and the request stays open`() {
        val s = tick(hamlet())
        val (after, r) = GameEngine.completeQuest(s, TentMove.ID, 2, 4, t0)
        assertFalse(r.won)
        assertFalse(TentMove.moved(after))
        assertEquals(1, TentMove.tent(after)!!.plot)
        assertFalse(asked(after)!!.done)
        assertNotNull(GameEngine.questChallenge(after, TentMove.ID, pool))
    }

    @Test fun `moving is done once, and a village with two tents moves the first`() {
        val two = hamlet(FIELD, TENT, HUT, TENT)
        val moved = TentMove.move(two, t0)
        assertEquals(listOf(NO_PLOT, 3), moved.buildings.filter { it.type == TENT }.map { it.plot })
        assertEquals(moved, TentMove.move(moved, t0 + 1))
        assertEquals(hamlet(FIELD, HUT), TentMove.move(hamlet(FIELD, HUT), t0))
    }

    @Test fun `the tent at the pond is still a tent for every rule`() {
        val camp = TentMove.move(hamlet(TENT, FIELD, age = Age.OGENJ), t0)
        // the camp needs a tent: it's there
        val step = GameEngine.advanceCheck(camp, 100).steps.first { it.kind == AgeStep.Kind.BUILDING }
        assertEquals(1, step.have)
        assertEquals(GameEngine.attributes(hamlet(TENT, FIELD, age = Age.OGENJ)).populationCap, GameEngine.attributes(camp).populationCap)
        // it counts towards the tents' limit, it can be upgraded and repaired
        val four = TentMove.move(hamlet(TENT, TENT, TENT, TENT, HUT, age = Age.VAS), t0)
        assertFalse(GameEngine.buildOptions(four.copy(resources = Res.entries.associateWith { 900 })).first { it.type == TENT }.available)
        val tent = TentMove.atPond(four)!!
        val up = GameEngine.upgrade(four.copy(resources = Res.entries.associateWith { 900 }), tent.id, t0)
        assertEquals(2, up.buildings.first { it.id == tent.id }.level)
        assertEquals(NO_PLOT, up.buildings.first { it.id == tent.id }.plot)
        val broken = four.copy(buildings = four.buildings.map { if (it.id == tent.id) it.copy(damaged = true) else it }, resources = Res.entries.associateWith { 900 })
        assertFalse(GameEngine.repair(broken, tent.id, t0).buildings.first { it.id == tent.id }.damaged)
        // the tent's scene still opens: the village has its tent
        assertTrue(si.lanisce.lani.game.scene.TownMarkers.has(four, si.lanisce.lani.game.scene.TownPlace.At(TENT)))
    }

    @Test fun `the campfire's scene has its tent only while one stands in the village`() {
        val campfire = { s: GameState -> si.lanisce.lani.game.scene.SceneFixtures.there("campfire", "tent", si.lanisce.lani.game.scene.SceneWorld.of(s)) }
        assertTrue(campfire(hamlet()))
        assertFalse(campfire(TentMove.move(hamlet(), t0)))
        assertTrue(campfire(TentMove.move(hamlet(FIELD, TENT, HUT, TENT), t0)))
    }

    @Test fun `Luka remembers it`() {
        val q = asked(tick(hamlet()))!!
        val m = VillagerLogic.questMemory(q, null, day)
        assertEquals("dan, ko sva šotor prestavila k ribniku", m.sl)
        assertEquals("the day we moved the tent to the pond", m.en)
    }

    // --- the second learner's village -----------------------------------------------------------------------------

    @Test fun `in the friuli village Pastore Davide asks, in Italian`() {
        val pair = L10n.pair
        try {
            Cultures.use("friuli")
            L10n.pair = LangPair(Lang.IT, Lang.SL)
            val s = tick(hamlet(residents = listOf("davide", "rosa")))
            val q = asked(s)!!
            assertEquals("Pastore Davide", q.giver)
            assertEquals("La tenda in un posto nuovo · Šotor na novo mesto", q.title)
            val c = GameEngine.questChallenge(s, TentMove.ID, pool)!!
            val first = c.exercises.first() as Exercise.Choice
            assertEquals("Mettiamo la tenda vicino allo stagno, sotto il grande abete, accanto al pontile. Davanti alla tenda facciamo il fuoco, e dietro la tenda mettiamo la legna.", first.audio)
            assertEquals("Dove mettiamo la tenda? · Kje bo stal šotor?", first.prompt)
            assertEquals("🪷 vicino allo stagno", first.options[first.answer])
            for ((i, e) in c.exercises.withIndex()) {
                val answer = Cultures.current.quests.tentMove!!.questions[i].options.first().substringAfterLast(' ')
                assertTrue(answer, answer.take(4) in (e as Exercise.Choice).audio!!)
            }
            val (after, r) = GameEngine.completeQuest(s, TentMove.ID, 4, 4, t0, giverId = "davide")
            assertTrue(r.won)
            assertTrue(TentMove.moved(after))
            assertEquals("Davide ha portato la tenda vicino allo stagno, sotto il grande abete. · Davide je šotor prestavil k ribniku, pod veliko jelko.", after.log.last().text)
            // the giver is of the friuli cast
            val dir = listOf(File("../../cultures/friuli/villagers"), File("../cultures/friuli/villagers")).first { it.isDirectory }
            val cast = parseVillagers(dir.listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }.joinToString(",", "[", "]") { it.readText() })
            assertTrue(cast.any { it.name == "Pastore Davide" && it.id == Quests.idOf("Pastore Davide") })
        } finally {
            Cultures.use(Cultures.DEFAULT)
            L10n.pair = pair
        }
    }
}
