package si.lanisce.lani.game.culture

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Projects
import java.time.MonthDay

/**
 * The maypole's season in every culture pack (projects.json, `stands`; companion/GAME.md "Village projects"): raised on
 * the eve of May Day, taken down after May, two weeks at least, with the chronicle's lines in the pack's language and the
 * base languages; the other projects stand all year.
 */
class MaypoleSeasonContentTest {
    @After fun back() {
        Cultures.use(Cultures.DEFAULT)
    }

    @Test fun `every culture's maypole stands in May, the rest all year`() {
        for (id in listOf("primorska", "friuli", "kaernten", "lakeland")) {
            val c = Cultures.load(id)
            Cultures.use(c)
            val st = Projects.spec("mlaj")!!.stands
            assertTrue("$id: the maypole has its season", st != null)
            assertEquals(id, MonthDay.of(4, 30), st!!.raised)
            assertEquals(id, MonthDay.of(5, 31), st.until)
            assertEquals(id, 14, st.minDays)
            for (lang in listOf("sl", "en", "de", "it")) {
                assertTrue("$id up in $lang", st.upText.by[lang]?.isNotBlank() == true)
                assertTrue("$id down in $lang", st.downText.by[lang]?.isNotBlank() == true)
            }
            assertEquals("$id: the pack's language first", c.language.code, st.downText.by.keys.first())
            for (p in c.projects) if (p.id != "mlaj") assertNull("$id: ${p.id} stands all year", p.stands)
        }
    }
}
