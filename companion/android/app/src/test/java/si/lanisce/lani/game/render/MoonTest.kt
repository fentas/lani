package si.lanisce.lani.game.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * The moon of a date ([Moon]): its age against the full and new moons of 2026 (to a day: the mean month runs up to some
 * 14 hours off the true moon), its lit share, side and phase name, the terminator's shape, and when it is up.
 */
class MoonTest {
    private val s = Moon.SYNODIC.toFloat()

    private fun utc(iso: String): Long = LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC).toEpochMilli()

    /** Days from [a] to [b] around the month: -s/2 .. s/2. */
    private fun apart(a: Float, b: Float): Float = ((b - a) % s + s * 1.5f) % s - s / 2

    // the full and new moons of 2026 (UTC)
    private val full2026 = listOf(
        "2026-01-03T10:03", "2026-02-01T22:09", "2026-03-03T11:38", "2026-04-02T02:12", "2026-05-01T17:23", "2026-05-31T08:45",
        "2026-06-29T23:57", "2026-07-29T14:36", "2026-08-28T04:18", "2026-09-26T16:49", "2026-10-26T04:12", "2026-11-24T14:53",
        "2026-12-24T01:28",
    )
    private val new2026 = listOf(
        "2026-01-18T19:52", "2026-02-17T12:01", "2026-03-19T01:23", "2026-04-17T11:52", "2026-05-16T20:01", "2026-06-15T02:54",
        "2026-07-14T09:44", "2026-08-12T17:37", "2026-09-11T03:27", "2026-10-10T15:50", "2026-11-09T07:02", "2026-12-09T00:52",
    )

    @Test fun `the reference new moon is age 0`() {
        assertEquals(0f, abs(apart(0f, Moon.age(utc("2000-01-06T18:14")))), 0.01f)
    }

    @Test fun `the full moons of 2026 are full, to a day`() {
        for (d in full2026) {
            val age = Moon.age(utc(d))
            assertTrue("$d: ${"%.2f".format(age)} days old", abs(apart(Moon.FULL, age)) <= 1f)
            assertEquals(d, Moon.Phase.FULL, Moon.phase(age))
            assertTrue("$d: lit ${Moon.lit(age)}", Moon.lit(age) > 0.97f)
        }
    }

    @Test fun `the new moons of 2026 are new, to a day`() {
        for (d in new2026) {
            val age = Moon.age(utc(d))
            assertTrue("$d: ${"%.2f".format(age)} days old", abs(apart(0f, age)) <= 1f)
            assertEquals(d, Moon.Phase.NEW, Moon.phase(age))
            assertTrue("$d: lit ${Moon.lit(age)}", Moon.lit(age) < 0.03f)
        }
    }

    @Test fun `the age runs from 0 up to the synodic month, a day a day`() {
        val t0 = utc("2026-09-26T12:00")
        for (k in 0 until 60) {
            val a = Moon.age(t0 + k * 86_400_000L)
            assertTrue("$a", a >= 0f && a < s)
            val b = Moon.age(t0 + (k + 1) * 86_400_000L)
            assertEquals("a day on from day $k", 1f, apart(a, b), 0.001f)
        }
        // before 2000 too
        assertTrue(Moon.age(utc("1969-07-20T20:17")) in 0f..s)
    }

    @Test fun `the phases in order, a quarter lit half`() {
        val names = Moon.Phase.entries
        for ((k, p) in names.withIndex()) assertEquals(p, Moon.phase(s * k / 8f))
        assertEquals(Moon.Phase.NEW, Moon.phase(s - 0.5f))
        assertEquals(Moon.Phase.WAXING_CRESCENT, Moon.phase(3f))
        assertEquals(Moon.Phase.WANING_CRESCENT, Moon.phase(26f))
        assertEquals(0.5f, Moon.lit(s / 4), 0.001f)
        assertEquals(0.5f, Moon.lit(s * 3 / 4), 0.001f)
        assertEquals(1f, Moon.lit(Moon.FULL), 0.001f)
        assertTrue(Moon.waxing(3f) && Moon.waxing(12f) && !Moon.waxing(17f) && !Moon.waxing(26f))
        // the lit share grows to full and falls back
        for (a in 1..14) assertTrue("day $a", Moon.lit(a.toFloat()) > Moon.lit(a - 1f))
        for (a in 16..29) assertTrue("day $a", Moon.lit(a.toFloat()) < Moon.lit(a - 1f))
    }

    @Test fun `the terminator lights a waxing moon on the right and a waning one on the left`() {
        // a waxing crescent: its right limb lit, its left and middle dark
        assertTrue(Moon.light(0.95f, 0f, 3f) > 0f)
        assertTrue(Moon.light(0f, 0f, 3f) <= 0f && Moon.light(-0.95f, 0f, 3f) <= 0f)
        // the first quarter: the right half; the last quarter: the left half
        assertTrue(Moon.light(0.1f, 0.3f, s / 4) > 0f && Moon.light(-0.1f, 0.3f, s / 4) < 0f)
        assertTrue(Moon.light(-0.1f, 0.3f, s * 3 / 4) > 0f && Moon.light(0.1f, 0.3f, s * 3 / 4) < 0f)
        // a waxing gibbous: all but a sliver on the left; a waning crescent: the left limb alone
        assertTrue(Moon.light(-0.5f, 0f, 11f) > 0f && Moon.light(-0.98f, 0f, 11f) <= 0f)
        assertTrue(Moon.light(-0.95f, 0f, 26.5f) > 0f && Moon.light(0f, 0f, 26.5f) <= 0f)
        // full: all of it; the terminator bends like an ellipse, the tips of a crescent reach the poles
        assertTrue(Moon.light(-0.99f, 0f, Moon.FULL) > 0f)
        assertTrue(Moon.light(0.05f, -0.999f, 3f) > 0f)
    }

    /** The moon's way at [hour] on a day of [month] (the sun's noon from [Env]). */
    private fun arc(age: Float, hour: Float, month: Int = 9): Float {
        val e = Env(hour, month, false, 0f, age)
        return e.moonArc
    }

    private fun up(age: Float, hour: Float, month: Int = 9) = arc(age, hour, month) >= 0f

    @Test fun `it is up about as the phase has it`() {
        // new: with the sun, by day
        assertTrue(up(0.3f, 13f) && !up(0.3f, 1f))
        // the first quarter: from about noon to about midnight, highest at dusk
        val fq = s / 4
        assertTrue(up(fq, 17f) && up(fq, 21f) && !up(fq, 3f) && !up(fq, 9f))
        // full: all night, rising at sunset and setting at sunrise; not by day
        assertTrue(up(Moon.FULL, 23f) && up(Moon.FULL, 1f) && up(Moon.FULL, 4f) && !up(Moon.FULL, 13f))
        for (month in 1..12) {
            val e = Env(12f, month, false)
            assertTrue("full moon in month $month rises at sunset", abs(arc(Moon.FULL, e.sunset + 0.3f, month)) < 0.1f)
            assertTrue("full moon in month $month sets at sunrise", arc(Moon.FULL, e.sunrise + 0.3f, month) < 0f)
        }
        // the last quarter: from about midnight to about noon, highest at dawn
        val lq = s * 3 / 4
        assertTrue(up(lq, 3f) && up(lq, 7f) && !up(lq, 16f) && !up(lq, 21f))
        // it rises about 50 minutes later each day
        fun rise(age: Float): Float = (0 until 24 * 60).first { m -> !up(age, (m - 1) / 60f) && up(age, m / 60f) } / 60f
        val later = rise(8f) - rise(7f)
        assertTrue("rises $later h later", later in 0.6f..1.2f)
        // highest at its middle, low near either end
        assertEquals(1f, Moon.height(0.5f), 0.001f)
        assertTrue(Moon.height(0.05f) < 0.2f && Moon.height(0.95f) < 0.2f && Moon.height(-1f) == 0f)
    }

    @Test fun `the winter full moon is up longer than the summer one`() {
        fun hoursUp(month: Int) = (0 until 24 * 4).count { up(Moon.FULL, it / 4f, month) } / 4f
        assertTrue("${hoursUp(12)} h in December, ${hoursUp(6)} h in June", hoursUp(12) > hoursUp(6) + 4f)
    }

    @Test fun `a new moon isn't drawn even while it is up`() {
        assertFalse(Env(13f, 9, false, 0f, 0.2f).moonShows)
        assertTrue(Env(13f, 9, false, 0f, 3f).moonShows)
        assertFalse(Env(3f, 9, false, 0f, 3f).moonShows)
        assertTrue(Env(23f, 9, false, 0f, Moon.FULL).moonShows)
    }
}
