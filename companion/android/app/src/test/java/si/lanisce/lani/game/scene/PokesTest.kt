package si.lanisce.lani.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Taps on something in a scene ([Poke], [Pokes]): they go through its reactions in turn, then it rests; a later tap starts over. */
class PokesTest {
    /** The kitchen's cat, as its painter has it: a stretch, a purr, a dash off and back. */
    private val cat = Poke("cat", listOf(1.8, 2.8, 22.0), rest = 6.0)
    private val pot = Poke("pot", listOf(1.3, 1.9), rest = 3.0)

    @Test fun `taps in a row go through the reactions in turn`() {
        val p = Pokes(listOf(cat, pot))
        assertTrue(p.tap("cat", 10.0))
        assertEquals(mapOf("cat" to Poked(0, 10.0)), p.at(10.5))
        // during the stretch, and just after it
        assertTrue(p.tap("cat", 11.2))
        assertEquals(Poked(1, 11.2), p.at(11.3)["cat"])
        assertTrue(p.tap("cat", 11.2 + 2.8 + 3.0))
        assertEquals(1, p.at(17.1).size)
        assertEquals(2, p.at(17.1).getValue("cat").step)
    }

    @Test fun `a reaction just begun isn't cut short`() {
        val p = Pokes(listOf(cat))
        p.tap("cat", 10.0)
        assertFalse(p.tap("cat", 10.0 + Poke.MIN_S / 2))
        assertEquals(Poked(0, 10.0), p.at(10.4)["cat"])
    }

    @Test fun `after a pause the taps start over`() {
        val p = Pokes(listOf(cat))
        p.tap("cat", 10.0)
        p.tap("cat", 11.0) // the purr, till 13.8
        assertTrue(p.tap("cat", 13.8 + Poke.AGAIN_S + 0.1))
        assertEquals(0, p.at(18.0).getValue("cat").step)
    }

    @Test fun `after the last reaction it rests, then starts over`() {
        val p = Pokes(listOf(pot))
        p.tap("pot", 10.0)
        p.tap("pot", 11.0) // it boils over, till 12.9
        for (t in listOf(11.6, 12.5, 13.0, 15.8)) assertFalse("rests at $t", p.tap("pot", t))
        assertEquals(Poked(1, 11.0), p.at(12.0)["pot"])
        assertTrue(p.tap("pot", 16.0))
        assertEquals(Poked(0, 16.0), p.at(16.1)["pot"])
    }

    @Test fun `the cat comes back after its dash, and only then may be tapped again`() {
        val p = Pokes(listOf(cat))
        p.tap("cat", 0.0); p.tap("cat", 1.0); p.tap("cat", 4.0)
        // away twenty seconds and more: it plays on till it is back on the bench
        assertEquals(2, p.at(4.0 + 20.0).getValue("cat").step)
        assertTrue(p.at(4.0 + 22.0).isEmpty())
        assertFalse(p.tap("cat", 4.0 + 23.0))
        assertTrue(p.tap("cat", 4.0 + 22.0 + 6.0))
        assertEquals(0, p.at(32.5).getValue("cat").step)
    }

    @Test fun `each thing goes its own way, and nothing plays once it is over`() {
        val p = Pokes(listOf(cat, pot))
        p.tap("cat", 10.0)
        assertEquals(setOf("cat"), p.at(10.5).keys)
        p.tap("pot", 11.0)
        assertEquals(setOf("cat", "pot"), p.at(11.5).keys)
        assertEquals(setOf("pot"), p.at(12.0).keys)
        assertTrue(p.at(12.4).isEmpty())
    }

    @Test fun `a tap on what has no poke does nothing`() {
        val p = Pokes(listOf(pot))
        assertFalse(p.tap("cat", 1.0))
        assertTrue(p.at(1.5).isEmpty())
        assertTrue(Pokes(emptyList()).at(0.0).isEmpty())
    }

    @Test fun `the time since a poke is never negative`() {
        assertEquals(0f, Poked(0, 5.0).age(4.9))
        assertEquals(1.5f, Poked(0, 5.0).age(6.5))
        assertNull(pot.after(Poked(1, 0.0), 1.0))
    }
}
