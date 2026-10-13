package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.CourseFixture
import si.lanisce.lani.data.Grading.Verdict.ALMOST
import si.lanisce.lani.data.Grading.Verdict.CORRECT
import si.lanisce.lani.data.Grading.Verdict.WRONG
import si.lanisce.lani.data.json
import si.lanisce.lani.game.Fixtures.day0
import si.lanisce.lani.game.Fixtures.noon

/** The school (companion/GAME.md, "The school"): the class from the hamlet on, the levels and their gates, the units, the progress kept. */
class SchoolTest {
    private val course = CourseFixture.course
    private val t0 = noon(day0)
    private val linden = Building("lipa-1", BuildingType.LIPA, plot = 3)
    private val school = Building("school-1", BuildingType.SCHOOL, plot = 4)

    private fun village(vararg b: Building, age: Age = Age.ZASELEK, food: Int = 200, wood: Int = 200) =
        GameState(seed = 1, age = age, resources = mapOf(Res.FOOD to food, Res.WOOD to wood, Res.STONE to 100, Res.WISDOM to 100), buildings = b.toList())

    /** [s] with its class begun and the levels open kept (as the app keeps them: School.opened). */
    private fun classroom(s: GameState = village(linden)) = School.opened(s, course, day0, t0)

    /** [ids] of the course done on [day0]. */
    private fun done(s: GameState, vararg ids: String): GameState = ids.fold(s) { acc, id ->
        School.testDone(acc, course.units.getValue(id), List(10) { CORRECT }, day0, t0).first
    }

    // --- the class, from the hamlet on ------------------------------------------------------------------------------

    @Test fun `the class begins with the hamlet, no words, no price, no linden needed`() {
        val camp = village(age = Age.TABOR)
        assertFalse(School.open(camp))
        assertEquals(listOf<School.Need>(School.Need.Hamlet), School.level(camp, course, "A1").needs)
        assertNull(School.next(camp, course))
        assertSame(camp, School.opened(camp, course, day0, t0))
        // the hamlet, without a linden, poor: the class meets by the fire
        val hamlet = village(food = 0, wood = 0)
        assertTrue(School.open(hamlet))
        assertEquals(School.Place.FIRE, School.place(hamlet))
        assertTrue(School.isOpen(hamlet, course, "A1"))
        assertEquals("biti", School.next(hamlet, course)!!.id)
        // under the linden once it stands (a damaged one doesn't count), in the school once that stands
        assertEquals(School.Place.LINDEN, School.place(village(linden)))
        assertEquals(School.Place.FIRE, School.place(village(linden.copy(damaged = true))))
        assertEquals(School.Place.SCHOOL, School.place(village(linden, school, age = Age.VAS)))
    }

    @Test fun `the class's beginning is kept once, the teacher named in the chronicle, nothing paid`() {
        val s = village(food = 10, wood = 0)
        val mojca = si.lanisce.lani.l10n.Localized { "Učiteljica Mojca" }
        val n = School.opened(s, course, day0, t0, mojca)
        assertEquals(day0.toString(), n.course!!.classroom)
        assertEquals(setOf("sl/A1"), n.course!!.opened.keys)
        assertEquals(s.resources, n.resources)
        assertEquals("📚", n.log.last().emoji)
        assertTrue(n.log.last().text.contains("Mojca"))
        // once: again it stays as it is, also days later
        assertSame(n, School.opened(n, course, day0.plusDays(3), t0))
        // a village whose school stands has its class indoors: no beginning to keep, A1 open all the same
        val vas = School.opened(village(school, age = Age.VAS), course, day0, t0)
        assertNull(vas.course!!.classroom)
        assertTrue("sl/A1" in vas.course!!.opened)
    }

    @Test fun `the school opens the class too, without a classroom`() {
        val s = village(school, age = Age.VAS)
        assertTrue(School.open(s))
        assertTrue(School.isOpen(s, course, "A1"))
        assertEquals(School.Place.FIRE, School.place(village(school.copy(damaged = true), age = Age.VAS)))
    }

    // --- the levels -------------------------------------------------------------------------------------------------

    @Test fun `A2 waits for the school and A1's units, planned ones not counted`() {
        var s = classroom()
        val a2 = School.level(s, course, "A2")
        assertFalse(a2.open)
        assertTrue(School.Need.School in a2.needs)
        assertTrue(a2.needs.contains(School.Need.Units("A1", 3)))
        // the units of A1 done, the school still missing
        s = done(s, "biti", "spol", "extra")
        assertTrue(School.levelDone(s, course, "A1"))
        assertEquals(listOf<School.Need>(School.Need.School), School.level(s, course, "A2").needs)
        // the school up: A2 open
        s = s.copy(buildings = s.buildings + school, age = Age.VAS)
        assertTrue(School.isOpen(s, course, "A2"))
        val a1 = School.level(s, course, "A1")
        assertEquals(3, a1.done)
        assertEquals(3, a1.of)
        assertEquals(1, a1.planned)
        assertTrue(a1.complete)
    }

    @Test fun `B1 and B2 wait for the school's levels, which wait for the course`() {
        var s = done(village(linden, school, age = Age.VAS).copy(resources = Res.entries.associateWith { 790 }), "biti", "spol", "extra")
        assertTrue(School.isOpen(s, course, "A2"))
        assertFalse(School.isOpen(s, course, "B1"))
        assertTrue(School.level(s, course, "B1").needs.contains(School.Need.SchoolLevel(2)))
        assertTrue(School.level(s, course, "B1").needs.contains(School.Need.Units("A2", 1)))
        // the school's upgrade to level 2 waits for A2's units
        val gate = School.gate(s, course, school.id)!!
        assertEquals(CourseGate("school-1", 2, "A2", 1), gate)
        val up = GameEngine.upgradeOption(s, school.id, course = gate)!!
        assertFalse(up.available)
        assertFalse(up.mobaAvailable)
        assertSame(s, GameEngine.upgrade(s, school.id, t0, course = gate))
        s = done(s, "dajalnik")
        val open = School.gate(s, course, school.id)!!
        assertTrue(open.done)
        assertTrue(GameEngine.upgradeOption(s, school.id, course = open)!!.available)
        s = GameEngine.upgrade(s, school.id, t0, course = open)
        assertEquals(2, School.schoolBuilding(s)!!.level)
        assertTrue(School.isOpen(s, course, "B1"))
        // B1 has no units yet: it holds the school's next level back for nothing
        assertTrue(School.gate(s, course, school.id)!!.done)
        // another building has no gate, nor a school without a course
        assertNull(School.gate(s, course, linden.id))
        assertNull(School.gate(s, null, school.id))
        assertEquals(listOf("A2", "B1", "B2", "B2"), (2..5).map { School.upgradeNeeds(it) })
    }

    @Test fun `a school upgraded before the course opens nothing ahead of the course`() {
        val s = classroom(village(linden, school.copy(level = 3), age = Age.VAS))
        assertTrue(School.isOpen(s, course, "A1"))
        assertFalse(School.isOpen(s, course, "A2")) // A1's units first
        assertFalse(School.isOpen(s, course, "B1"))
        assertEquals(listOf<School.Need>(School.Need.Units("A2", 1)), School.level(done(s, "biti", "spol", "extra"), course, "B1").needs)
        assertTrue(School.isOpen(done(s, "biti", "spol", "extra", "dajalnik"), course, "B2")) // B1 has no units: done at once
    }

    @Test fun `a level once open stays open, though new units come to the one before`() {
        var s = done(village(linden, school, age = Age.VAS), "biti", "spol", "extra")
        s = School.opened(s, course, day0, t0)
        assertEquals(setOf("sl/A1", "sl/A2"), s.course!!.opened.keys)
        assertTrue(s.log.any { it.emoji == "🏫" })
        // the outline grows: A1 has a unit more, not done
        val more = course.copy(units = course.units + ("osebni-zaimki" to course.units.getValue("spol").copy(id = "osebni-zaimki")),
            levels = course.levels.map { l -> if (l.level == "A1") l.copy(units = l.units.map { if (it.id == "osebni-zaimki") it.copy(planned = false) else it }) else l })
        assertFalse(School.levelDone(s, more, "A1"))
        assertTrue(School.isOpen(s, more, "A2"))
        // and nothing new to keep
        assertSame(s, School.opened(s, more, day0, t0))
    }

    // --- the units --------------------------------------------------------------------------------------------------

    @Test fun `the next unit is suggested, any open one can be done, in any order`() {
        var s = classroom()
        assertEquals("biti", School.next(s, course)!!.id)
        assertEquals(School.Status.NEXT, School.status(s, course, course.ref("biti")!!))
        assertEquals(School.Status.OPEN, School.status(s, course, course.ref("spol")!!))
        assertEquals(School.Status.PLANNED, School.status(s, course, course.ref("osebni-zaimki")!!))
        assertEquals(School.Status.LOCKED, School.status(s, course, course.ref("dajalnik")!!))
        // the second unit first: the first stays the one suggested
        s = done(s, "spol")
        assertEquals(School.Status.DONE, School.status(s, course, course.ref("spol")!!))
        assertEquals("biti", School.next(s, course)!!.id)
        s = done(s, "biti")
        assertEquals("extra", School.next(s, course)!!.id)
        s = done(s, "extra")
        assertNull(School.next(s, course)) // A2 waits for the school
    }

    @Test fun `a unit's steps - intro, worksheets, test`() {
        val u = course.units.getValue("biti")
        var s = classroom()
        assertEquals(School.Step.Intro, School.nextStep(u, School.progress(s, "sl", "biti")))
        s = School.introProgress(s, "sl", "biti", 2, done = false, today = day0)
        val p = School.progress(s, "sl", "biti")!!
        assertEquals(2, p.intro)
        assertEquals(day0.toString(), p.started)
        assertEquals(School.Step.Intro, School.nextStep(u, p))
        // skipped ("Poznam to"): on to the worksheets, its place kept
        s = School.introProgress(s, "sl", "biti", 1, done = true, today = day0)
        assertEquals(2, School.progress(s, "sl", "biti")!!.intro)
        assertEquals(School.Step.Sheet("biti-1"), School.nextStep(u, School.progress(s, "sl", "biti")))
        s = School.sheetDone(s, "sl", "biti", "biti-1", listOf(CORRECT, WRONG), day0)
        assertEquals(School.Step.Sheet("biti-2"), School.nextStep(u, School.progress(s, "sl", "biti")))
        s = School.sheetDone(s, "sl", "biti", "biti-2", listOf(CORRECT), day0)
        assertEquals(School.Step.Test, School.nextStep(u, School.progress(s, "sl", "biti")))
        // a unit without an intro starts with its first worksheet
        assertEquals(School.Step.Sheet("spol-1"), School.nextStep(course.units.getValue("spol"), null))
    }

    @Test fun `a sheet keeps its best try and counts the tries`() {
        var s = classroom()
        s = School.sheetDone(s, "sl", "biti", "biti-1", listOf(CORRECT, CORRECT, WRONG, WRONG), day0)
        s = School.sheetDone(s, "sl", "biti", "biti-1", listOf(CORRECT, CORRECT, CORRECT, ALMOST), day0.plusDays(1))
        s = School.sheetDone(s, "sl", "biti", "biti-1", listOf(WRONG, WRONG, WRONG, WRONG), day0.plusDays(2))
        val best = School.progress(s, "sl", "biti")!!.sheets.getValue("biti-1")
        assertEquals(SheetBest(3, 1, 4, day0.plusDays(1).toString(), 3), best)
        assertSame(s, School.sheetDone(s, "sl", "biti", "biti-1", emptyList(), day0))
    }

    @Test fun `the test passed - the unit done once, its bonus, a line in the chronicle`() {
        val u = course.units.getValue("biti")
        var s = School.sheetDone(classroom(), "sl", "biti", "biti-1", listOf(CORRECT), day0)
        // not passed: a try, nothing done
        val (failed, doneNow) = School.testDone(s, u, listOf(CORRECT, CORRECT, WRONG, WRONG), day0, t0)
        assertFalse(doneNow)
        assertNull(School.progress(failed, "sl", "biti")!!.done)
        assertEquals(1, School.progress(failed, "sl", "biti")!!.test!!.tries)
        // passed at 80 %: done, the bonus paid in the scarcest resource (stone here: 100 of the hamlet's 500)
        val bonus = School.unitBonus(failed)
        assertEquals(mapOf(Res.STONE to 32), bonus) // 20 × (1 + 0.3 × 2), rounded
        val stone = failed.res(Res.STONE)
        val (passed, first) = School.testDone(failed, u, listOf(CORRECT, CORRECT, CORRECT, CORRECT, WRONG), day0, t0)
        assertTrue(first)
        val p = School.progress(passed, "sl", "biti")!!
        assertEquals(day0.toString(), p.done)
        assertFalse(p.testedOut) // a worksheet of it was done
        assertEquals(2, p.test!!.tries)
        assertEquals(stone + 32, passed.res(Res.STONE))
        assertEquals("🟰", passed.log.last().emoji)
        // passed again: no bonus, no line
        s = passed
        val (again, twice) = School.testDone(s, u, List(5) { CORRECT }, day0.plusDays(1), t0)
        assertFalse(twice)
        assertEquals(s.resources, again.resources)
        assertEquals(day0.toString(), School.progress(again, "sl", "biti")!!.done)
        // the scarcest is the lowest share of its cap
        assertEquals(setOf(Res.FOOD), School.unitBonus(s.copy(resources = s.resources + (Res.FOOD to 0))).keys)
    }

    @Test fun `tested out - the test passed straight away, without its worksheets`() {
        val u = course.units.getValue("spol")
        val (s, first) = School.testDone(classroom(), u, listOf(CORRECT, ALMOST), day0, t0) // 1.5 of 2 at 50 %
        assertTrue(first)
        val p = School.progress(s, "sl", "spol")!!
        assertTrue(p.testedOut)
        assertEquals(School.Step.Done, School.nextStep(u, p))
    }

    // --- the progress saved -----------------------------------------------------------------------------------------

    @Test fun `the progress survives the save, and a save from before has none`() {
        var s = School.introProgress(classroom(), "sl", "biti", 3, done = true, today = day0)
        s = School.sheetDone(s, "sl", "biti", "biti-1", listOf(CORRECT, ALMOST, WRONG), day0)
        s = done(s, "spol")
        val text = json.encodeToString(GameState.serializer(), s)
        assertEquals(s.course, json.decodeFromString(GameState.serializer(), text).course)
        assertTrue(text.contains("\"course\""))
        assertTrue(text.contains("\"sl/biti\""))
        val old = json.decodeFromString(GameState.serializer(), """{"seed":1,"age":"ZASELEK","buildings":[{"id":"lipa-1","type":"LIPA","plot":3}]}""")
        assertNull(old.course)
        // a hamlet from before the school: its class begins as the app opens it
        assertTrue(School.open(old))
        assertEquals(day0.toString(), School.opened(old, course, day0, t0).course!!.classroom)
        // a village without a school's course doesn't write the field
        assertFalse(json.encodeToString(GameState.serializer(), village(linden)).contains("\"course\""))
        // a newer app's fields are left alone by this one (ignoreUnknownKeys): the course still reads
        val newer = json.decodeFromString(GameState.serializer(), text.replace("\"course\":{", "\"course\":{\"v\":2,\"later\":true,"))
        assertNotNull(newer.course)
    }
}
