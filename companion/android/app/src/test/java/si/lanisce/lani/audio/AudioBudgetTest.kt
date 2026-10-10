package si.lanisce.lani.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioBudgetTest {
    private fun f(name: String, mb: Long, used: Long, id: Any = name) = StoredFile(name, mb * MB, used, id)

    @Test fun `the cap's limit, off keeps the old 50 MB for the cache alone, unlimited none`() {
        assertEquals(50 * MB, AudioBudget.limit(AudioCap.OFF))
        assertTrue(AudioBudget.cacheOnly(AudioCap.OFF))
        assertEquals(250 * MB, AudioBudget.limit(AudioCap.MB250))
        assertFalse(AudioBudget.cacheOnly(AudioCap.MB250))
        assertNull(AudioBudget.limit(AudioCap.UNLIMITED))
        assertEquals(AudioCap.MB250, AudioCap.DEFAULT)
        assertEquals(AudioCap.MB250, AudioCap.of("nonsense"))
        assertEquals(AudioCap.OFF, AudioCap.of("off"))
        assertFalse(AudioCap.OFF.prefetch)
        assertTrue(AudioCap.UNLIMITED.prefetch)
    }

    @Test fun `lru drops the clips played longest ago until everything fits`() {
        val cache = listOf(f("new", 40, 300), f("old", 30, 100), f("mid", 30, 200))
        assertEquals(setOf("old"), AudioBudget.evict(cache, emptyList(), 70 * MB))
        assertEquals(setOf("old", "mid"), AudioBudget.evict(cache, emptyList(), 40 * MB))
        assertTrue(AudioBudget.evict(cache, emptyList(), 100 * MB).isEmpty())
    }

    @Test fun `the day's clips are never dropped, the next oldest go instead`() {
        val cache = listOf(f("today", 30, 100), f("played", 30, 200), f("lately", 30, 300))
        assertEquals(setOf("played"), AudioBudget.evict(cache, emptyList(), 60 * MB, keep = setOf("today")))
        // even when the day's alone are over: they stay, the rest goes
        assertEquals(setOf("played", "lately"), AudioBudget.evict(cache, emptyList(), 25 * MB, keep = setOf("today")))
    }

    @Test fun `the car's library counts in the cap, and a clip both have counts once and isn't dropped`() {
        val road = listOf(f("r1", 100, 0), f("shared-road", 10, 0, id = "inode-7"))
        val cache = listOf(f("shared", 10, 50, id = "inode-7"), f("a", 30, 100), f("b", 30, 200), f("c", 30, 300))
        // all together: 110 (road, the shared one once) + 90 = 200 MB; a cap of 150 drops 50: a and b (oldest), never the shared one
        assertEquals(200 * MB, AudioBudget.unique(cache + road))
        assertEquals(setOf("a", "b"), AudioBudget.evict(cache, road, 150 * MB))
    }

    @Test fun `the clip cache keeps a little of its own whatever the car's library takes`() {
        val road = listOf(f("r", 300, 0))
        val cache = listOf(f("a", 10, 100), f("b", 10, 200), f("c", 10, 300))
        // the road alone is over a 250 MB cap: the cache goes down to MIN_CACHE (20 MB), not to nothing
        assertEquals(setOf("a"), AudioBudget.evict(cache, road, 250 * MB))
    }

    @Test fun `room is the cap less the car's library and the day's clips`() {
        val road = listOf(f("r", 100, 0), f("both", 10, 0, id = 1))
        val cache = listOf(f("today", 20, 0), f("both-cache", 10, 0, id = 1), f("old", 50, 0))
        assertEquals((250 - 110 - 20) * MB, AudioBudget.room(cache, road, 250 * MB, keep = setOf("today", "both-cache")))
    }

    @Test fun `room counts down and refuses what doesn't fit`() {
        val r = Room(10)
        assertTrue(r.take(6))
        assertFalse(r.take(5))
        assertTrue(r.full)
        assertTrue(r.take(4))
        assertEquals(0L, r.remaining)
        val none = Room(null)
        assertTrue(none.take(Long.MAX_VALUE))
        assertFalse(none.full)
    }
}
