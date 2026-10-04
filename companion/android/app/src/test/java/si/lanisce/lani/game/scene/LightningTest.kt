package si.lanisce.lani.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.render.Env
import si.lanisce.lani.game.render.PixelCanvas
import si.lanisce.lani.game.render.scene.Weather

class LightningTest {
    private val storm = Sky(rain = 0.8f, gloom = 0.9f, lightning = true)

    /** How bright the scene painter draws the flash at [t] (game/render/scene/Weather.kt). */
    private fun drawn(t: Double, sky: Sky = storm): Float =
        Weather(PixelCanvas(8, 8), 8, 8, 1, t, Env(12f, 6, false), sky).flash()

    @Test fun `a scene's flash is drawn when the schedule says, a flicker then an echo`() {
        val l = Lightning.SCENE
        for (n in 0 until 60) {
            val at = l.at(n)
            assertEquals("flash $n", 1f, drawn(at + 0.03), 0f)
            assertEquals("its echo", 0.55f, drawn(at + 0.18), 0f)
            assertEquals("between them", 0f, drawn(at + 0.12), 0f)
            assertEquals("before it", 0f, drawn(at - 0.05), 0f)
            assertEquals("after it", 0f, drawn(at + 0.3), 0f)
            assertEquals("its bolt", n, l.index(at + 0.03))
        }
        assertEquals("no lightning, no flash", 0f, drawn(l.at(3) + 0.03, Sky(rain = 1f)), 0f)
        // every 5 to 10 s
        for (n in 0 until 200) assertTrue("gap $n", l.at(n + 1) - l.at(n) in 4.8..9.8)
    }

    @Test fun `the village flashes on the dot, more often in its storm`() {
        assertEquals(5.3, Lightning.STORM.period, 0.0)
        assertEquals(8.1, Lightning.KEPT.period, 0.0)
        assertEquals(Lightning.STORM, Lightning.village(storm = true))
        assertEquals(Lightning.KEPT, Lightning.village(storm = false))
        for (n in 0 until 50) {
            assertEquals(n * 5.3, Lightning.STORM.at(n), 1e-9)
            assertEquals(n * 8.1, Lightning.KEPT.at(n), 1e-9)
        }
        // what the village renderer draws: the first 6 % of each period
        val t = 3 * 5.3 + 0.1
        assertTrue(Lightning.STORM.since(t) / Lightning.STORM.period < Lightning.VILLAGE_SHOWS)
        assertEquals(3, Lightning.STORM.index(t))
    }

    @Test fun `the flashes of a stretch of the clock are those that begin in it, each in one stretch only`() {
        val l = Lightning.SCENE
        assertEquals(emptyList<Int>(), l.between(5.0, 5.0))
        assertEquals(emptyList<Int>(), l.between(6.0, 5.0))
        val n = 7
        assertEquals(listOf(n), l.between(l.at(n), l.at(n) + 0.01))
        assertEquals(emptyList<Int>(), l.between(l.at(n) + 0.01, l.at(n + 1)))
        // cut into stretches of any length, every flash of a long while turns up once, in order
        val whole = l.between(0.0, 1000.0)
        assertEquals((0 until whole.size).toList(), whole)
        for (step in listOf(0.043, 0.5, 3.3, 11.0)) {
            val cut = ArrayList<Int>()
            var t = 0.0
            while (t < 1000.0) {
                cut += l.between(t, minOf(t + step, 1000.0))
                t += step
            }
            assertEquals("stretches of $step s", whole, cut)
        }
    }
}
