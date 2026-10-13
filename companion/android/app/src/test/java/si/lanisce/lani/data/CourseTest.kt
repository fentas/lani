package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The school's course as the app reads it (companion/COURSE.md): GET /course, and the four worksheet types. */
class CourseTest {
    private val c = CourseFixture.course

    @Test fun `the outline in order, a planned unit planned, an unlisted unit after its level's`() {
        assertEquals(listOf("A1", "A2", "B1", "B2"), c.levels.map { it.level })
        assertEquals(listOf("biti", "spol", "osebni-zaimki", "extra"), c.refs("A1").map { it.id })
        assertTrue(c.ref("osebni-zaimki")!!.planned)
        assertEquals(listOf("ti-vi"), c.ref("osebni-zaimki")!!.grammar)
        assertFalse(c.ref("biti")!!.planned)
        // what counts for the level: the written units, not the planned one
        assertEquals(listOf("biti", "spol", "extra"), c.playable("A1").map { it.id })
        assertEquals("A2", c.levelOf("dajalnik"))
        assertTrue(c.playable("B1").isEmpty())
    }

    @Test fun `a unit - its pages, intro, worksheets, test and the tutor's homework`() {
        val u = c.units.getValue("biti")
        assertEquals(listOf("biti", "zanikanje"), u.grammar)
        assertEquals(2, u.worksheets.size)
        assertEquals(80, u.test.pass)
        assertEquals(2, u.test.exercises.size)
        val hw = u.homework.single()
        assertTrue(hw.tutor)
        assertEquals(1760000000000L, hw.publishedAt) // a fractional mtime is read
        assertNotNull(u.sheet("biti-domaca"))
        // a type of a newer app is skipped, never the whole sheet lost
        assertTrue(u.worksheets[1].exercises.last() is Exercise.Unsupported)
    }

    @Test fun `the intro's steps, the table they build, their quick questions`() {
        val intro = c.units.getValue("biti").intro!!
        assertEquals(2, intro.head.size)
        assertEquals(3, intro.steps.size)
        assertEquals(3, intro.steps[0].rows.size)
        assertTrue(intro.steps[0].ask is Exercise.Choice)
        assertTrue(intro.steps[1].ask is Exercise.Cloze)
        // a row longer than the head is left out
        assertTrue(intro.steps[2].rows.isEmpty())
        assertNull(intro.steps[2].ask)
        assertEquals("I'm at home.", intro.steps[0].examples.single().meaning("en"))
        assertNull(c.units.getValue("spol").intro)
    }

    @Test fun `the four types read as they are written`() {
        val sheet1 = c.units.getValue("biti").worksheets[0].exercises
        val sort = sheet1[0] as Exercise.Sort
        assertEquals(listOf("moški", "ženski"), sort.columns.map { it.label })
        val table = sheet1[1] as Exercise.Table
        assertEquals("sem", table.rows[0].cells[0].given)
        assertFalse(table.rows[0].cells[0].toFill)
        assertEquals(listOf("smo"), table.rows[0].cells[1].accept)
        assertEquals("pres.1pl", table.rows[0].cells[1].form)
        val sheet2 = c.units.getValue("biti").worksheets[1].exercises
        val gap = sheet2[0] as Exercise.GapText
        assertEquals(3, gap.gaps.size)
        assertTrue(gap.bank)
        assertEquals("mnozina", gap.gaps[2].grammar)
        val tr = sheet2[1] as Exercise.Transform
        assertEquals("ne", tr.cue)
        assertEquals("neg.3sg", tr.items[1].form)
    }

    @Test fun `a table cell is written back as it was read`() {
        val cells = listOf(TableCell(given = "sem"), TableCell(accept = listOf("smo"), form = "pres.1pl"))
        for (cell in cells) assertEquals(cell, json.decodeFromString(TableCell.serializer(), json.encodeToString(TableCell.serializer(), cell)))
        assertEquals("\"sem\"", json.encodeToString(TableCell.serializer(), cells[0]))
    }

    @Test fun `no levels is no course`() {
        assertNull(Courses.parse("""{"schema":"lani.course/v0","language":"sl","levels":[]}"""))
        assertNull(Courses.parse("not json"))
    }
}
