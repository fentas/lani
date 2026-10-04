package si.lanisce.lani.game.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.scene.Lightning
import si.lanisce.lani.game.scene.Sky
import kotlin.random.Random

class ThunderTest {
    private val storm = Sky(rain = 0.8f, gloom = 0.9f, lightning = true)

    /** The thunder the player takes, looking at the clock in slices as it goes (a block, now and then a second asleep). */
    private fun heardOver(lightning: Lightning, seconds: Double, seed: Int = 1): List<Cue> {
        val random = Random(seed)
        val out = ArrayList<Cue>()
        var t = 0.0
        while (t < seconds) {
            val next = t + if (random.nextInt(20) == 0) 1.0 else 0.043
            out += Thunder.after(lightning, t, next)
            t = next
        }
        return out
    }

    @Test fun `every flash the picture draws has its thunder once, 2,5 to 6 s after it, however the clock is looked at`() {
        for (l in listOf(Lightning.SCENE, Lightning.STORM, Lightning.KEPT)) {
            val seconds = 600.0
            val flashes = l.between(0.0, seconds)
            assertTrue(flashes.size > seconds / 10)
            for (seed in 1..3) {
                val cues = heardOver(l, seconds, seed)
                assertEquals(flashes.size, cues.size)
                for ((n, c) in flashes.zip(cues)) {
                    val delay = c.at - l.at(n)
                    assertTrue("flash $n: $delay s", delay in Thunder.SOONEST..Thunder.LATEST)
                    assertEquals(Thunder.of(l, n), c)
                }
            }
        }
        // an empty stretch of the clock has none
        assertEquals(emptyList<Cue>(), Thunder.after(Lightning.SCENE, 4.0, 4.0))
    }

    @Test fun `thunder where the picture flashes, none where it doesn't`() {
        val clear = Sky.CLEAR
        assertNull(Thunder.lightning(Place.Scene("forest"), clear))
        assertNull(Thunder.lightning(Place.Scene("forest"), Sky(rain = 1f, gloom = 1f)))
        assertSame(Lightning.SCENE, Thunder.lightning(Place.Scene("tent"), storm))
        assertSame(Lightning.SCENE, Thunder.lightning(Place.Scene("kitchen"), storm))
        // a newer art is drawn by a placeholder, without weather
        assertNull(Thunder.lightning(Place.Scene("lighthouse"), storm))
        assertSame(Lightning.STORM, Thunder.lightning(Place.Village(storm = true), Soundscape.STORM))
        assertSame(Lightning.STORM, Thunder.lightning(Place.Village(storm = true), clear))
        assertSame(Lightning.KEPT, Thunder.lightning(Place.Village(), storm))
        assertNull(Thunder.lightning(Place.Village(), Sky(rain = 0.5f)))
    }

    @Test fun `all of it in the open and in the tent, about half in a room, a fifth in the cellar`() {
        assertEquals(1f, Thunder.heard(Place.Village()), 0f)
        assertEquals(1f, Thunder.heard(Place.Scene("field")), 0f)
        assertEquals(1f, Thunder.heard(Place.Scene("tent")), 0f)
        assertEquals(1f, Thunder.heard(Place.Scene("lighthouse")), 0f)
        val kitchen = Thunder.heard(Place.Scene("kitchen"))
        assertEquals(0.5f, kitchen, 0.02f)
        val attic = Thunder.heard(Place.Scene("attic"))
        val cellar = Thunder.heard(Place.Scene("cellar"))
        assertEquals(0.2f, cellar, 0.02f)
        assertTrue("$cellar < $kitchen < $attic < 1", cellar < kitchen && kitchen < attic && attic < 1f)
        // a room hears more of the thunder than of the rain: it comes through the walls
        assertTrue(kitchen > Soundscape.SPOTS.getValue("kitchen").weather)
        assertEquals(kitchen, Thunder.of(Lightning.SCENE, 4, kitchen).gain / Thunder.of(Lightning.SCENE, 4).gain, 1e-5f)
    }

    @Test fun `a farther bolt's thunder comes later, softer and deeper, and the rumbles take turns`() {
        val all = (0 until 400).map { Thunder.of(Lightning.SCENE, it) to Lightning.SCENE.at(it) }
        for ((c, _) in all) {
            assertTrue(c.gain in 0.55f..1f)
            assertTrue(c.rate in 0.9f..1.06f)
        }
        val byDelay = all.sortedBy { (c, flash) -> c.at - flash }
        for ((a, b) in byDelay.zipWithNext()) {
            assertTrue("later is softer", b.first.gain <= a.first.gain + 1e-6f)
            assertTrue("later is deeper", b.first.rate <= a.first.rate + 1e-6f)
        }
        val nearest = byDelay.first().let { (c, f) -> c.at - f }
        val farthest = byDelay.last().let { (c, f) -> c.at - f }
        assertTrue("$nearest … $farthest", nearest < 3.0 && farthest > 5.5)
        for (n in 0 until 100) assertTrue(Thunder.of(Lightning.SCENE, n).sound != Thunder.of(Lightning.SCENE, n + 1).sound)
        assertEquals(Sound.THUNDER.toSet(), all.map { it.first.sound }.toSet())
    }
}
