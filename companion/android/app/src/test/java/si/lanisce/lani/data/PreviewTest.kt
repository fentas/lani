package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewTest {
    @Test fun `first non-blank line without markdown`() {
        assertEquals("Bravo, Jan! Danes", previewLine("\n\n**Bravo**, Jan! _Danes_\n> več"))
    }

    @Test fun `a blank reply is empty instead of throwing`() {
        // The background check used first { }, which threw here and blocked every later notification.
        assertEquals("", previewLine(""))
        assertEquals("", previewLine("  \n **  ** \n"))
    }

    @Test fun `long lines are cut`() {
        assertEquals("abc", previewLine("abcdef", max = 3))
    }
}
