package si.lanisce.lani.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Whether the background check treats this phone's learner as a child (data/BackgroundCheck.kt [PhoneChild]): a phone
 * paired by a typed token, the app not opened since this version, is a child's from the node's own town row, asked once.
 */
class PhoneChildTest {
    private val asked = mutableListOf<String>()
    private val kept = mutableListOf<Boolean>()

    private fun town(child: Boolean) = TownsList(TownSelf(id = "0123456789abcdef0123456789abcdef", name = "Borgo", learner = "Teo", child = child))

    private fun of(said: Boolean?, paired: Boolean = false, hosted: Boolean = false, town: suspend () -> TownsList?) = runBlocking {
        PhoneChild.of(said, paired, hosted, town = { asked += "GET /towns"; town() }, keep = { kept += it })
    }

    @Test fun `a typed token's phone not opened since - the town row says a child's, kept, asked once`() {
        assertTrue(of(said = null) { town(child = true) })
        assertEquals(listOf("GET /towns"), asked)
        assertEquals(listOf(true), kept)
        // the next check reads what was kept: no request
        assertTrue(of(said = kept.last()) { error("not asked") })
        assertEquals(1, asked.size)
    }

    @Test fun `an adult's town row is kept as an adult's`() {
        assertFalse(of(said = null) { town(child = false) })
        assertEquals(listOf(false), kept)
    }

    @Test fun `what the app, the pairing or the hosted profile said wins, no request`() {
        assertTrue(of(said = true) { error("not asked") })
        assertTrue(of(said = false, paired = true) { error("not asked") })
        assertTrue(of(said = null, hosted = true) { error("not asked") })
        assertFalse(of(said = false) { error("not asked") })
        assertTrue(asked.isEmpty() && kept.isEmpty())
    }

    @Test fun `an older node without towns is an adult's, as the app says, kept`() {
        assertFalse(of(said = null) { null })
        assertEquals(listOf(false), kept)
    }

    @Test fun `a failed request keeps nothing, so the next check asks again`() {
        assertFalse(of(said = null) { throw IOException("offline") })
        assertTrue(kept.isEmpty())
        assertTrue(of(said = null) { town(child = true) })
        assertEquals(2, asked.size)
    }
}
