package si.lanisce.lani.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.Learner
import java.time.LocalDate

/**
 * When Home coming back reads again ([Fresh], docs/plans/05-built/nav-perf.md, changes-areas.md, nav-perf-2.md): nothing
 * while the bridge's version is the same and what no version says is under ten minutes old; only what moved, and what no
 * version says once it's due, while the reading is for the same day, time of day, pair, learner and culture and the last
 * whole one is under an hour old; everything else when any of those changed.
 */
class FreshTest {
    private val day = LocalDate.of(2026, 10, 30)
    private val read = Fresh("v1", day, TimeOfDay.AFTERNOON, LangPair.DEFAULT, Learner.of("Ana", "f"), "primorska", at = 1_000_000L)

    @Test fun `the same version a moment later is fresh`() {
        assertTrue(read.sameAs(read.copy(at = read.at + 5_000)))
        assertTrue(read.sameAs(read.copy(at = read.at + Fresh.MAX_AGE_MS - 1)))
    }

    @Test fun `anything else reads again`() {
        assertFalse("the bridge's version moved", read.sameAs(read.copy(version = "v2", at = read.at + 1)))
        assertFalse("another day", read.sameAs(read.copy(day = day.plusDays(1), at = read.at + 1)))
        assertFalse("another time of day (the people's whereabouts)", read.sameAs(read.copy(time = TimeOfDay.EVENING, at = read.at + 1)))
        assertFalse("another pair", read.sameAs(read.copy(pair = LangPair.parse("sl-de")!!, at = read.at + 1)))
        assertFalse("another learner", read.sameAs(read.copy(learner = Learner.of("Marko", "m"), at = read.at + 1)))
        assertFalse("another culture", read.sameAs(read.copy(culture = "friuli", at = read.at + 1)))
        assertFalse("ten minutes on", read.sameAs(read.copy(at = read.at + Fresh.MAX_AGE_MS)))
        assertFalse("a clock gone back (another boot)", read.sameAs(read.copy(at = read.at - 1)))
    }

    @Test fun `another version of the same day, pair, learner and culture reads only what moved, an hour everything`() {
        val moved = read.copy(version = "v2", areas = mapOf("learner" to "L2"), at = read.at + 5_000)
        assertFalse(read.sameAs(moved))
        assertTrue("only what moved", read.within(moved))
        assertTrue("ten minutes on: still only what moved (and what no version says)", read.within(moved.copy(at = read.at + Fresh.MAX_AGE_MS)))
        assertFalse("an hour after the last whole reading: everything", read.within(moved.copy(at = read.at + Fresh.WHOLE_AGE_MS)))
        assertFalse("another day: everything", read.within(moved.copy(day = day.plusDays(1))))
        assertFalse("another time of day: everything", read.within(moved.copy(time = TimeOfDay.EVENING)))
        assertFalse("another pair: everything", read.within(moved.copy(pair = LangPair.parse("sl-de")!!)))
        assertFalse("another learner: everything", read.within(moved.copy(learner = Learner.of("Marko", "m"))))
        assertFalse("another culture: everything", read.within(moved.copy(culture = "friuli")))
    }

    @Test fun `why everything is read again is named, the first that differs`() {
        val moved = read.copy(version = "v2", at = read.at + 5_000)
        assertEquals(null, read.unlike(moved))
        assertEquals("another day", read.unlike(moved.copy(day = day.plusDays(1), culture = "friuli")))
        assertEquals("another time of day", read.unlike(moved.copy(time = TimeOfDay.EVENING)))
        assertEquals("another pair", read.unlike(moved.copy(pair = LangPair.parse("sl-de")!!)))
        assertEquals("another learner", read.unlike(moved.copy(learner = Learner.of("Marko", "m"))))
        assertEquals("another culture", read.unlike(moved.copy(culture = "friuli")))
        assertEquals("an hour after the last whole reading", read.unlike(moved.copy(at = read.at + Fresh.WHOLE_AGE_MS)))
        assertEquals("a clock gone back (another boot)", "an hour after the last whole reading", read.unlike(moved.copy(at = read.at - 1)))
    }

    @Test fun `what no version says is due ten minutes after it was last asked, whatever the whole reading's age`() {
        assertFalse("a moment on", read.due(read.copy(at = read.at + 5_000)))
        assertFalse("just under ten minutes", read.due(read.copy(at = read.at + Fresh.MAX_AGE_MS - 1)))
        assertTrue("ten minutes", read.due(read.copy(at = read.at + Fresh.MAX_AGE_MS)))
        assertTrue("a clock gone back (another boot)", read.due(read.copy(at = read.at - 1)))
        // a reading of what no version says, half an hour after the whole one: the next ten minutes count from it
        val asked = read.copy(asked = read.at + 30 * 60_000L)
        val later = read.at + 35 * 60_000L
        assertFalse(asked.due(read.copy(at = later)))
        assertTrue("the same version reads nothing then", asked.sameAs(read.copy(at = later)))
        assertTrue(asked.due(read.copy(at = read.at + 40 * 60_000L)))
        assertFalse("not fresh once due, the same version or not", asked.sameAs(read.copy(at = read.at + 40 * 60_000L)))
        assertTrue("only what no version says, not everything", asked.within(read.copy(at = read.at + 40 * 60_000L)))
    }
}
