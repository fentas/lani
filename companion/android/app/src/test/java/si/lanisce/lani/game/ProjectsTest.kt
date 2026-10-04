package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Resident
import si.lanisce.lani.game.villagers.Visit

/** "Skupni projekti · Village projects" (GAME.md): steps, one a day, what they cost, the leader, what a finished one brings. */
class ProjectsTest {
    private val t0 = noon(day0)
    private val people = listOf("luka", "france", "marko", "nejc", "tine", "tone", "anton", "micka")

    private fun village(age: Age = Age.ZASELEK, res: Int = 400, who: List<String> = people, help: Int = 20, goods: Map<String, Int> = mapOf("potica" to 3)) = GameState(
        age = age, resources = Res.entries.associateWith { res }, residents = who.map { Resident(it, "2026-08-01") }, villagers = who.size,
        help = help, chest = Inventory(goods = goods), lastTick = day0.toString(),
    )

    private fun mlaj() = Projects.spec("mlaj")!!

    /** Plays [steps] steps of [id], one a day from [from]; returns the state and the day after the last. */
    private fun steps(s0: GameState, id: String, steps: Int, from: java.time.LocalDate = day0): GameState {
        var s = s0
        repeat(steps) { i ->
            s = s.copy(resources = GameEngine.attributes(s).caps) // the stores full every morning
            val (n, r) = Projects.finishStep(s, id, 5, 5, from.plusDays(i.toLong()), noon(from.plusDays(i.toLong())))
            assertTrue("step ${i + 1}: ${r.message}", r.won)
            s = n
        }
        return s
    }

    @Test fun `the catalog, projects from Zaselek on, 5 to 7 steps, a leader of the cast, a landmark each`() {
        val all = Catalog.projects
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(all.size, all.map { it.landmark }.toSet().size)
        for (age in listOf(Age.ZASELEK, Age.VAS, Age.TRG, Age.MESTO)) assertTrue("a project for $age", all.any { it.age == age })
        for (p in all) {
            assertTrue(p.id, p.age >= Age.ZASELEK)
            assertTrue("${p.id}: ${p.steps.size} steps", p.steps.size in 5..7)
            assertTrue("${p.id}'s leader ${p.leader} is of the cast", Catalog.tools.containsKey(p.leader))
            assertEquals("${p.id} ends with the feast", StepKind.FEAST, p.steps.last().kind)
            assertTrue(p.id, p.steps.any { it.help })
            for (t in listOf(p.name, p.about, p.done, p.memory) + p.steps.flatMap { listOf(it.task, it.line) }) assertTrue("$t: Slovene · English", " · " in t)
            for (step in p.steps) for ((r, n) in Catalog.stepCost(p.age, step.kind)) {
                assertTrue("${p.id}: $n ${r.emoji} fits the age's store", n <= Catalog.baseCap[p.age.ordinal])
            }
        }
    }

    @Test fun `what a step costs, by the project's age`() {
        assertEquals(mapOf(Res.FOOD to 100, Res.WOOD to 240), Catalog.stepCost(Age.ZASELEK, StepKind.TIMBER))
        assertEquals(mapOf(Res.FOOD to 190, Res.WOOD to 115, Res.STONE to 340), Catalog.stepCost(Age.VAS, StepKind.STONE))
        assertEquals(mapOf(Res.FOOD to 570, Res.WOOD to 230, Res.WISDOM to 115), Catalog.stepCost(Age.VAS, StepKind.FEAST))
        assertEquals(listOf(4, 6, 8, 10), listOf(Age.ZASELEK, Age.VAS, Age.TRG, Age.MESTO).map { Catalog.stepHelp(it) })
    }

    @Test fun `a step passed pays its cost, tells its line and the leader grows closer`() {
        val s = village()
        val o = Projects.option(s, mlaj(), day0)
        assertTrue(o.reason, o.available)
        assertEquals(Catalog.stepCost(Age.ZASELEK, StepKind.TIMBER), o.cost)
        val (n, r) = Projects.finishStep(s, "mlaj", 4, 5, day0, t0)
        assertTrue(r.won)
        assertEquals(1, n.projects["mlaj"])
        assertEquals(day0.toString(), n.projectDay)
        assertEquals(400 - o.cost.getValue(Res.FOOD), n.res(Res.FOOD))
        assertEquals(400 - o.cost.getValue(Res.WOOD), n.res(Res.WOOD))
        assertTrue(n.log.any { it.text.startsWith("Luka in fantje so v gozdu izbrali visoko smreko.") })
        assertEquals(Catalog.PROJECT_STEP_POINTS, n.bonds["luka"]?.points)
        assertTrue(r.message.contains("smreko"))
    }

    @Test fun `the village does one step a day, across all projects`() {
        val (n, _) = Projects.finishStep(village(res = 500), "mlaj", 5, 5, day0, t0)
        for (p in listOf("mlaj", "most")) {
            val o = Projects.option(n, Projects.spec(p)!!, day0)
            assertFalse(o.available)
            assertEquals(ProjectOption.Why.TODAY, o.waiting)
        }
        assertNull(GameEngine.projectChallenge(n, "most", Fixtures.pool(), day0))
        // a second try the same day changes nothing
        val (again, r) = Projects.finishStep(n, "most", 5, 5, day0, t0)
        assertFalse(r.won)
        assertEquals(n, again)
        // tomorrow the next step
        val next = day0.plusDays(1)
        assertTrue(Projects.option(n, mlaj(), next).available)
        assertEquals(2, Projects.finishStep(n, "mlaj", 5, 5, next, noon(next)).first.projects["mlaj"])
    }

    @Test fun `a failed practice costs nothing, and the step can be tried again today`() {
        val s = village()
        val (n, r) = Projects.finishStep(s, "mlaj", 2, 5, day0, t0)
        assertFalse(r.won)
        assertEquals(s, n)
        assertTrue(r.message.contains("poskusiva še enkrat"))
        assertTrue(Projects.option(n, mlaj(), day0).available)
    }

    @Test fun `a project waits for its leader`() {
        val away = village(who = people - "luka")
        val o = Projects.option(away, mlaj(), day0)
        assertEquals(ProjectOption.Why.LEADER, o.waiting)
        assertTrue(o.reason!!.contains("Luka"))
        assertNull(GameEngine.projectChallenge(away, "mlaj", Fixtures.pool(), day0))
        // visiting today, he leads
        val visiting = away.copy(visitor = Visit("luka", day0.toString()))
        assertTrue(Projects.option(visiting, mlaj(), day0).available)
        // nobody has a name yet (an older bridge): everyone is here
        assertTrue(Projects.option(village(who = emptyList()), mlaj(), day0).available)
    }

    @Test fun `a project needs its age`() {
        val o = Projects.option(village(), Projects.spec("kapelica")!!, day0)
        assertEquals(ProjectOption.Why.AGE, o.waiting)
        assertEquals(listOf("mlaj", "most", "mlin"), Projects.open(village()).map { it.id })
        assertEquals(9, Projects.open(village(Age.VAS)).size)
    }

    @Test fun `help steps cost 🤝, the feast step a treat from the chest`() {
        // step 2 of the maypole brings the neighbours: 4 🤝 at Zaselek
        val one = steps(village(), "mlaj", 1)
        val day = day0.plusDays(1)
        val o = Projects.option(one, mlaj(), day)
        assertEquals(4, o.help)
        assertTrue(o.available)
        assertEquals(one.help - 4, Projects.finishStep(one, "mlaj", 5, 5, day, noon(day)).first.help)
        assertEquals(ProjectOption.Why.HELP, Projects.option(one.copy(help = 3), mlaj(), day).waiting)
        // the last step treats the helpers: the chest's food good
        val four = steps(village(), "mlaj", 4)
        val last = day0.plusDays(4)
        val feast = Projects.option(four, mlaj(), last)
        assertEquals("potica", feast.treat?.id)
        assertEquals(ProjectOption.Why.TREAT, Projects.option(four.copy(chest = Inventory()), mlaj(), last).waiting)
        assertEquals(ProjectOption.Why.RESOURCES, Projects.option(four.copy(resources = emptyMap()), mlaj(), last).waiting)
    }

    @Test fun `a finished project adds its bonus for good, and its helpers grow closer`() {
        val s0 = village()
        val done = steps(s0, "mlaj", 5)
        assertTrue(Projects.finished(done, mlaj()))
        assertTrue(Projects.option(done, mlaj(), day0.plusDays(5)).finished)
        assertEquals(Chest.count(s0, "potica") - 1, Chest.count(done, "potica")) // the feast step's treat
        // the maypole: +3 morale for good
        assertEquals(moraleTarget(s0) + 3, moraleTarget(done))
        assertTrue(done.log.any { it.text.startsWith("Mlaj: Mlaj stoji!") })
        // Luka: 5 steps and the finish; the helpers who live here: the finish, with the memory
        assertEquals(5 * Catalog.PROJECT_STEP_POINTS + Catalog.PROJECT_DONE_POINTS, done.bonds["luka"]?.points)
        for (h in listOf("marko", "nejc", "tine")) {
            assertEquals(h, Catalog.PROJECT_DONE_POINTS, done.bonds[h]?.points)
            assertEquals("naš mlaj", done.bonds[h]?.memories?.last()?.sl)
        }
        // the bridge's helpers: Tone lives here, the others' points don't come to someone who doesn't
        val before = village(who = people - "anton", help = 40)
        val bridge = steps(before, "most", 6)
        assertNull(bridge.bonds["anton"])
        assertEquals(Catalog.PROJECT_DONE_POINTS, bridge.bonds["tone"]?.points)
        // the bridge: +5 % wood from every answer
        assertEquals(GameEngine.attributes(before).production.getValue(Res.WOOD) + 0.05f, GameEngine.attributes(bridge).production.getValue(Res.WOOD), 0.001f)
    }

    @Test fun `the map's landmarks follow the steps`() {
        assertTrue(Projects.landmarks(village()).isEmpty())
        val two = steps(village(), "mlaj", 2)
        assertEquals(listOf(Landmark("mlaj", "mlaj", listOf("lipa", "fire"), 2, 5)), Projects.landmarks(two))
        assertFalse(Projects.landmarks(two).single().finished)
        assertTrue(Projects.landmarks(steps(village(), "mlaj", 5)).single().finished)
    }

    @Test fun `a step's practice is the leader's skill, with a request's pass mark`() {
        val c = GameEngine.projectChallenge(village(), "mlaj", Fixtures.pool(), day0)
        assertNotNull(c)
        assertEquals(Catalog.PROJECT_EXERCISES, c!!.exercises.size)
        assertTrue(c.skills.all { it == Res.WOOD })
        assertEquals(3, c.passMark)
        assertTrue(c.intro.startsWith("Izbrati smreko · Choose a spruce"))
    }

    @Test fun `old saves load, with no projects, festivals, surprise or purchases`() {
        val old = json.decodeFromString(
            GameState.serializer(),
            """{"seed":3,"age":"VAS","lastTick":"2026-08-31","lastFeast":"2026-08-30","chest":{"goods":{"potica":2}},"bonds":{"luka":{"points":40}}}""",
        )
        assertTrue(old.projects.isEmpty())
        assertEquals("", old.projectDay)
        assertTrue(old.festivals.isEmpty())
        assertNull(old.surprise)
        assertTrue(old.bought.isEmpty())
        // the new state survives the round trip
        val s = steps(village(), "mlaj", 1).copy(festivals = mapOf("trgatev" to "2026-09-26"), surprise = Surprise("2026-09-01", Surprises.LETTER, 4), bought = mapOf("pedlar/sol" to 1), boughtOn = "2026-09-01")
        assertEquals(s, json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), s)))
        // an old save ticked today already gets today's surprise all the same
        val ticked = GameEngine.tick(old.copy(lastTick = day0.toString()), day0, t0, Fixtures.pool()).state
        assertEquals(day0.toString(), ticked.surprise?.on)
        // an unknown surprise kind (a newer app's) is no surprise here
        assertNull(Surprises.today(old.copy(surprise = Surprise(day0.toString(), "juggler")), day0))
    }

    @Test fun `project points are friendship like any other`() {
        // five steps with Luka make him a friend at 25 + 5 = 30 points: level 2, and his gift
        val done = steps(village(), "mlaj", 5)
        assertEquals(Bonds.FRIEND, Bonds.level(done, "luka"))
        assertNotNull(done.chest.tools["luka"])
    }
}
