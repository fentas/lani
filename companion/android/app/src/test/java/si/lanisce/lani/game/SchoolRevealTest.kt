package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Course
import si.lanisce.lani.data.CourseLevel
import si.lanisce.lani.data.CourseUnit
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.UnitRef
import si.lanisce.lani.data.UnitTest

/**
 * What the school's screen shows of each level (Jan, 2026-10-27: "no need to show it all directly"; COURSE.md, "What the
 * school shows"): the units done and begun, the current one and the next [School.AHEAD], the rest folded into one line;
 * planned units only folded; the levels open and the first closed one, no further.
 */
class SchoolRevealTest {
    private val linden = Building("lipa-1", BuildingType.LIPA, plot = 3)
    private val school = Building("school-1", BuildingType.SCHOOL, plot = 4)

    private fun ref(id: String, planned: Boolean = false) = UnitRef(id, mapOf("sl" to "Enota $id", "en" to "Unit $id"), planned)
    private fun unit(id: String, level: String, homework: Boolean = false) = CourseUnit(
        id, "sl", level, "📘", mapOf("sl" to "Enota $id", "en" to "Unit $id"),
        test = UnitTest(80, listOf(Exercise.Cloze(text = "Jaz ___ doma.", accept = listOf("sem")))),
        homework = if (homework) listOf(si.lanisce.lani.data.Worksheet("$id-domaca", mapOf("en" to "Homework"), listOf(Exercise.Cloze(text = "Mi ___ doma.", accept = listOf("smo"))), tutor = true)) else emptyList(),
    )

    /**
     * A course as the outline grows: A1 eight units written (a1 … a8), A2 six written (b1 … b6) and eight planned (b7 …
     * b14), B1 three planned, B2 none yet; [homework] those units with the tutor's homework.
     */
    private fun course(vararg homework: String): Course {
        val a1 = (1..8).map { "a$it" }
        val a2 = (1..6).map { "b$it" }
        val units = a1.map { unit(it, "A1", it in homework) } + a2.map { unit(it, "A2", it in homework) }
        return Course(
            "sl",
            listOf(
                CourseLevel("A1", a1.map { ref(it) }),
                CourseLevel("A2", a2.map { ref(it) } + (7..14).map { ref("b$it", planned = true) }),
                CourseLevel("B1", (1..3).map { ref("c$it", planned = true) }),
                CourseLevel("B2", emptyList()),
            ),
            units.associateBy { it.id },
        )
    }

    private val course = course()

    private fun village(vararg b: Building, age: Age = Age.ZASELEK) = GameState(seed = 1, age = age, buildings = b.toList())

    private fun with(s: GameState, vararg units: Pair<String, UnitProgress>): GameState {
        val c = s.course ?: CourseProgress()
        return s.copy(course = c.copy(units = c.units + units.map { (id, p) -> School.key("sl", id) to p }))
    }

    private fun done(s: GameState, vararg ids: String) = with(s, *ids.map { it to UnitProgress(started = "2026-10-01", done = "2026-10-02") }.toTypedArray())
    private fun begun(s: GameState, vararg ids: String) = with(s, *ids.map { it to UnitProgress(started = "2026-10-20", intro = 2) }.toTypedArray())

    private fun List<UnitRef>.ids() = map { it.id }
    private fun List<School.LevelView>.of(level: String) = first { it.state.level == level }

    @Test fun `nothing done - the first unit and the next two, the rest folded, A2 closed with every unit folded`() {
        val views = School.reveal(village(linden), course)
        assertEquals(listOf("A1", "A2"), views.map { it.state.level }) // B1 and B2 not before A2 is reached
        val a1 = views.of("A1")
        assertEquals(listOf("a1", "a2", "a3"), a1.rows.ids())
        assertEquals(listOf("a4", "a5", "a6", "a7", "a8"), a1.folded.ids())
        assertFalse(a1.complete)
        val a2 = views.of("A2")
        assertFalse(a2.state.open)
        assertTrue(a2.rows.isEmpty())
        assertEquals((1..14).map { "b$it" }, a2.folded.ids())
    }

    @Test fun `A1 done and A2 started - A1 complete, A2's done, begun, current and next two, B1 closed, B2 not shown`() {
        var s = done(village(linden, school, age = Age.VAS), *(1..8).map { "a$it" }.toTypedArray())
        s = begun(done(s, "b1"), "b2")
        val views = School.reveal(s, course)
        assertEquals(listOf("A1", "A2", "B1"), views.map { it.state.level })
        val a1 = views.of("A1")
        assertTrue(a1.complete)
        assertEquals((1..8).map { "a$it" }, a1.rows.ids())
        assertTrue(a1.folded.isEmpty())
        val a2 = views.of("A2")
        assertTrue(a2.state.open)
        assertFalse(a2.complete)
        // b1 done, b2 begun (the current one), then the next two
        assertEquals(listOf("b1", "b2", "b3", "b4"), a2.rows.ids())
        assertEquals(listOf("b5", "b6") + (7..14).map { "b$it" }, a2.folded.ids())
        val b1 = views.of("B1")
        assertFalse(b1.state.open)
        assertTrue(b1.state.needs.contains(School.Need.SchoolLevel(2)))
        assertTrue(b1.rows.isEmpty())
        assertEquals(listOf("c1", "c2", "c3"), b1.folded.ids())
    }

    @Test fun `jumped ahead - every unit done or begun stays in view, the current one is the first not done`() {
        val s = begun(done(village(linden), "a1", "a2", "a6"), "a8")
        val a1 = School.reveal(s, course).of("A1")
        // current a3, then a4 and a5; a6 done and a8 begun further on stay; a7 folded
        assertEquals(listOf("a1", "a2", "a3", "a4", "a5", "a6", "a8"), a1.rows.ids())
        assertEquals(listOf("a7"), a1.folded.ids())
        // the current one is the unit suggested next
        assertEquals("a3", School.next(s, course)!!.id)
        // done ahead of the current one, the next two are the two not done after it
        val ahead = School.reveal(done(village(linden), "a2", "a3"), course).of("A1")
        assertEquals(listOf("a1", "a2", "a3", "a4", "a5"), ahead.rows.ids())
    }

    @Test fun `planned units only in the folded list, in the course's order, also when nothing written is left`() {
        val all = (1..8).map { "a$it" } + (1..6).map { "b$it" }
        val s = done(village(linden, school, age = Age.VAS), *all.toTypedArray())
        for (view in School.reveal(s, course)) {
            assertTrue(view.state.level, view.rows.none { it.planned })
            // what is shown and what is folded are the level's units, each once, in order
            assertEquals(course.refs(view.state.level).ids(), course.refs(view.state.level).ids().filter { it in (view.rows + view.folded).ids() })
            assertEquals(course.refs(view.state.level).size, view.rows.size + view.folded.size)
        }
        val a2 = School.reveal(s, course).of("A2")
        assertTrue(a2.complete) // every written unit done: the planned ones count for nothing
        assertEquals((1..6).map { "b$it" }, a2.rows.ids())
        assertEquals((7..14).map { "b$it" }, a2.folded.ids())
        assertTrue(a2.folded.all { it.planned })
    }

    @Test fun `a level closed - before the class, A1's units all folded behind what opens it, nothing after it`() {
        val camp = village(age = Age.TABOR)
        val views = School.reveal(camp, course)
        assertEquals(listOf("A1"), views.map { it.state.level })
        val a1 = views.single()
        assertFalse(a1.state.open)
        assertEquals(listOf<School.Need>(School.Need.Hamlet), a1.state.needs)
        assertTrue(a1.rows.isEmpty())
        assertEquals((1..8).map { "a$it" }, a1.folded.ids())
        assertFalse(a1.complete)
        // A1 done, the school not built: A2 shows with what it waits for, its units folded
        val noSchool = School.reveal(done(village(linden), *(1..8).map { "a$it" }.toTypedArray()), course)
        assertEquals(listOf("A1", "A2"), noSchool.map { it.state.level })
        assertEquals(listOf<School.Need>(School.Need.School), noSchool.of("A2").state.needs)
        assertTrue(noSchool.of("A2").rows.isEmpty())
    }

    @Test fun `all done - every unit a row, nothing folded`() {
        val small = Course("sl", listOf(CourseLevel("A1", listOf(ref("a1"), ref("a2"), ref("a3"), ref("a4"), ref("a5")))), (1..5).associate { "a$it" to unit("a$it", "A1") })
        val s = done(village(linden), "a1", "a2", "a3", "a4", "a5")
        val a1 = School.reveal(s, small).single()
        assertTrue(a1.complete)
        assertEquals(listOf("a1", "a2", "a3", "a4", "a5"), a1.rows.ids())
        assertTrue(a1.folded.isEmpty())
    }

    @Test fun `a level of three units or fewer - nothing to fold`() {
        val small = Course("sl", listOf(CourseLevel("A1", listOf(ref("a1"), ref("a2"), ref("a3")))), (1..3).associate { "a$it" to unit("a$it", "A1") })
        val a1 = School.reveal(village(linden), small).single()
        assertEquals(listOf("a1", "a2", "a3"), a1.rows.ids())
        assertTrue(a1.folded.isEmpty())
        assertFalse(a1.complete)
        // a level open with nothing written yet: its planned units folded, no row
        val planned = Course("sl", listOf(CourseLevel("A1", listOf(ref("p1", planned = true), ref("p2", planned = true)))), emptyMap())
        val p = School.reveal(village(linden), planned).single()
        assertTrue(p.rows.isEmpty())
        assertEquals(listOf("p1", "p2"), p.folded.ids())
        assertFalse(p.complete)
    }

    @Test fun `a unit with the tutor's homework stays in view`() {
        val a1 = School.reveal(village(linden), course("a7")).of("A1")
        assertEquals(listOf("a1", "a2", "a3", "a7"), a1.rows.ids())
        assertEquals(listOf("a4", "a5", "a6", "a8"), a1.folded.ids())
    }

    @Test fun `a level once open stays shown as open, new units before it folded as any`() {
        // A2 kept open; A1 got a unit more (a9), not done: A1 shows it as its current one
        val more = course.copy(
            levels = course.levels.map { if (it.level == "A1") it.copy(units = it.units + ref("a9")) else it },
            units = course.units + ("a9" to unit("a9", "A1")),
        )
        var s = done(village(linden, school, age = Age.VAS), *(1..8).map { "a$it" }.toTypedArray())
        s = School.opened(s, course, java.time.LocalDate.of(2026, 10, 27), 0L)
        val views = School.reveal(s, more)
        assertEquals(listOf("A1", "A2", "B1"), views.map { it.state.level })
        val a1 = views.of("A1")
        assertFalse(a1.complete)
        assertEquals((1..9).map { "a$it" }, a1.rows.ids())
        assertEquals(listOf("b1", "b2", "b3"), views.of("A2").rows.ids())
    }

    @Test fun `the teacher is preparing the next lessons while every unit written is done and more are planned`() {
        // A1 under way: something to do, nothing to prepare
        assertFalse(School.preparing(village(linden), course))
        // an outline with every unit planned (the Italian course on 2026-10-28): the class meets, nothing to open, preparing
        val outline = Course("it", listOf(CourseLevel("A1", (1..18).map { ref("u$it", planned = true) }), CourseLevel("A2", emptyList())), emptyMap())
        assertTrue(School.preparing(village(linden), outline))
        assertEquals(null, School.next(village(linden), outline))
        // six written and done, twelve planned: preparing; one of the six not done: not
        val six = Course(
            "sl", listOf(CourseLevel("A1", (1..6).map { ref("a$it") } + (7..18).map { ref("p$it", planned = true) })),
            (1..6).map { unit("a$it", "A1") }.associateBy { it.id },
        )
        assertTrue(School.preparing(done(village(linden), *(1..6).map { "a$it" }.toTypedArray()), six))
        assertFalse(School.preparing(done(village(linden), *(1..5).map { "a$it" }.toTypedArray()), six))
        // before the class begins: nothing to say yet
        assertFalse(School.preparing(village(age = Age.TABOR), outline))
        // every unit written and done, none planned: all done, not preparing
        assertFalse(School.preparing(done(village(linden), *(1..8).map { "a$it" }.toTypedArray()), Course("sl", listOf(CourseLevel("A1", (1..8).map { ref("a$it") })), course.units)))
    }
}
