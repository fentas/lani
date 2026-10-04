package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Test
import si.lanisce.lani.ui.minutesSince

class ScreenClockTest {
    private var t = 0L
    private val clock = ScreenClock { t }
    private val minute = 60_000L

    @Test
    fun `counts only the time on screen`() {
        clock.shown()
        t += 2 * minute
        clock.hidden()
        t += 4 * 60 * minute // the phone sleeps for four hours
        clock.shown()
        t += 3 * minute
        assertEquals(5 * minute, clock.now())
    }

    @Test
    fun `a word pack left open overnight is its minutes on screen, not 240`() {
        clock.shown()
        val started = clock.now()
        t += 1 * minute
        clock.hidden()
        t += 8 * 60 * minute
        clock.shown()
        t += 2 * minute + 10_000
        assertEquals(4, minutesSince(started, clock.now()))
    }

    @Test
    fun `showing twice or hiding twice changes nothing`() {
        clock.shown()
        t += minute
        clock.shown()
        t += minute
        clock.hidden()
        clock.hidden()
        t += minute
        assertEquals(2 * minute, clock.now())
    }

    @Test
    fun `a start from before a restart counts at least a minute`() {
        assertEquals(1, minutesSince(started = 10 * minute, now = 0))
    }
}
