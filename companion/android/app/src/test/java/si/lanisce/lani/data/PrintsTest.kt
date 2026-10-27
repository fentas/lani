package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the school prints (companion/COURSE.md, "Printed sheets"): the bridge's path for it and the print job's name. */
class PrintsTest {
    private val c = CourseFixture.course

    @Test fun `a unit's sheet - numbered among its level's written units, as the generator numbers its files`() {
        assertEquals(PrintJob.Sheet("biti", "A1", 1), Prints.sheet(c, "biti"))
        assertEquals(PrintJob.Sheet("spol", "A1", 2), Prints.sheet(c, "spol"))
        // after the planned one, which the sheets leave out: the third
        assertEquals(PrintJob.Sheet("extra", "A1", 3), Prints.sheet(c, "extra"))
        assertEquals(PrintJob.Sheet("dajalnik", "A2", 1), Prints.sheet(c, "dajalnik"))
        assertNull("planned: nothing to print yet", Prints.sheet(c, "osebni-zaimki"))
        assertNull(Prints.sheet(c, "no-such-unit"))
    }

    @Test fun `a level's booklet - only with a written unit`() {
        assertEquals(PrintJob.Booklet("A1"), Prints.booklet(c, "A1"))
        assertNull(Prints.booklet(c, "B1"))
        assertNull(Prints.booklet(c, "C2"))
    }

    @Test fun `the cases overview - on the page skloni of the Slovene book and a unit that starts from it, of no level`() {
        val o = Prints.overview("sl", "skloni")
        assertEquals(PrintJob.Overview("skloni", "Pregled sklonov"), o)
        assertNull(o!!.level)
        assertNull("a page without an overview", Prints.overview("sl", "biti"))
        assertNull("another language's book", Prints.overview("it", "skloni"))
        assertNull(Prints.overview("sl", null))
        assertEquals("/course/overview/skloni?base=de", Prints.path(o, "de"))
        assertEquals("/course/overview/skloni", Prints.path(o, "sl"))
        assertEquals("Lani – Pregled sklonov", Prints.jobName(o))
    }

    @Test fun `the bridge's path - in the learner's base when the sheets have it`() {
        val biti = Prints.sheet(c, "biti")!!
        assertEquals("/course/sheet/biti?base=en", Prints.path(biti, "en"))
        assertEquals("/course/sheet/biti?base=de", Prints.path(biti, "de"))
        assertEquals("/course/sheet/biti?base=it", Prints.path(biti, "it"))
        // a base the sheets don't explain in: the bridge's choice (the learner's own, English where it has none)
        assertEquals("/course/sheet/biti", Prints.path(biti, "sl"))
        assertEquals("/course/sheet/biti", Prints.path(biti, null))
        assertEquals("/course/booklet/A1?base=en", Prints.path(PrintJob.Booklet("A1"), "en"))
        // an id is a path segment, whatever it holds
        assertEquals("/course/sheet/a%20b%2Fc", Prints.path(PrintJob.Sheet("a b/c", "A1", 1), null))
    }

    @Test fun `the job's name - what Save as PDF names the file`() {
        assertEquals("Lani – A1 – 01 biti", Prints.jobName(Prints.sheet(c, "biti")!!))
        assertEquals("Lani – A1 – 03 extra", Prints.jobName(Prints.sheet(c, "extra")!!))
        assertEquals("Lani – A2 – 12 kdaj-cas", Prints.jobName(PrintJob.Sheet("kdaj-cas", "A2", 12)))
        assertEquals("Lani – A1 – vsi listi", Prints.jobName(PrintJob.Booklet("A1")))
    }
}
