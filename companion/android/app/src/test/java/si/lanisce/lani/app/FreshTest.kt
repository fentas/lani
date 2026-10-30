package si.lanisce.lani.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.scene.TimeOfDay
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.l10n.Learner
import java.time.LocalDate

/**
 * When Home coming back reads everything again ([Fresh], docs/plans/05-built/nav-perf.md): only when a reading now would be
 * for something else than the last whole one: the bridge's version moved, another day or time of day, another pair,
 * learner or culture, or the last one is older than ten minutes.
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
}
