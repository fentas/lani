package si.lanisce.lani.tutor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bundled price table, the model per task by the presets, and which ids play them. */
class PricingTest {
    private val prices = Prices.bundled()

    @Test fun `the plan's all-Sonnet cold start, priced`() {
        // a cold start: the 12k prefix written for an hour, a 20k thread for five minutes (plan 5, "The all-Sonnet line")
        val cold = Usage(cacheWrite1h = 12_000, cacheWrite5m = 20_000)
        assertEquals(12_000 * 4.0 / 1e6 + 20_000 * 2.5 / 1e6, prices.cost("claude-sonnet-5-5", cold)!!, 1e-12)
        // a warm call: 30k read, 2k written, 500 out
        val warm = Usage(cacheRead = 30_000, cacheWrite5m = 2_000, output = 500)
        assertEquals(30_000 * 0.10 / 1e6 + 2_000 * 2.5 / 1e6 + 500 * 10.0 / 1e6, prices.cost("claude-sonnet-5-5", warm)!!, 1e-12)
        assertEquals(1_000_000 * 20.0 / 1e6, prices.cost("claude-opus-5-5", Usage(output = 1_000_000))!!, 1e-9)
    }

    @Test fun `Haiku 5_5 costs five times as much over 100k-token prompts`() {
        val small = Usage(input = 50_000, output = 1_000)
        val big = Usage(input = 150_000, output = 1_000)
        assertEquals((50_000 * 0.10 + 1_000 * 0.50) / 1e6, prices.cost("claude-haiku-5-5", small)!!, 1e-12)
        assertEquals(5 * (150_000 * 0.10 + 1_000 * 0.50) / 1e6, prices.cost("claude-haiku-5-5", big)!!, 1e-12)
    }

    @Test fun `a model the table lacks is priced by its family, an unknown one not at all`() {
        assertEquals(prices.cost("claude-sonnet-5-5", Usage(input = 1000)), prices.cost("claude-sonnet-5-9", Usage(input = 1000)))
        assertNull(prices.cost("gpt-x", Usage(input = 1000)))
    }

    @Test fun `Balanced, Haiku for routine and grading, Sonnet for chat, Opus for content`() {
        val c = ModelCatalog(listOf(ModelInfo("claude-opus-5-5"), ModelInfo("claude-sonnet-5-5"), ModelInfo("claude-haiku-5-5"), ModelInfo("claude-haiku-4-5")))
        assertEquals("claude-haiku-5-5", c.modelFor(Task.ROUTINE, Preset.BALANCED))
        assertEquals("claude-haiku-5-5", c.modelFor(Task.GRADING, Preset.BALANCED))
        assertEquals("claude-sonnet-5-5", c.modelFor(Task.CHAT, Preset.BALANCED))
        assertEquals("claude-opus-5-5", c.modelFor(Task.CONTENT, Preset.BALANCED))
        assertEquals("claude-opus-5-5", c.modelFor(Task.ROUTINE, Preset.BEST))
        assertEquals("claude-haiku-5-5", c.modelFor(Task.CHAT, Preset.ECONOMY))
        // the panel's override, when the key lists it
        assertEquals("claude-haiku-4-5", c.modelFor(Task.ROUTINE, Preset.BALANCED, mapOf(Task.ROUTINE to "claude-haiku-4-5")))
        assertEquals("claude-haiku-5-5", c.modelFor(Task.ROUTINE, Preset.BALANCED, mapOf(Task.ROUTINE to "claude-nope")))
        assertEquals(listOf(20_000, 30_000, 60_000), Preset.entries.map { it.threadLimit })
    }

    @Test fun `ids the key doesn't list fall back, never to a legacy model by default`() {
        // no Haiku 5.5 but Haiku 4.5 (legacy): the family above's
        val noHaiku = ModelCatalog(listOf(ModelInfo("claude-haiku-4-5", createdAt = "2025-10-01"), ModelInfo("claude-sonnet-5-5", createdAt = "2026-10-01")))
        assertEquals("claude-sonnet-5-5", noHaiku.resolve(Family.HAIKU))
        // a newer Haiku of the family than the plan knows
        val newer = ModelCatalog(listOf(ModelInfo("claude-haiku-5-6", createdAt = "2027-01-01"), ModelInfo("claude-sonnet-5-5")))
        assertEquals("claude-haiku-5-6", newer.resolve(Family.HAIKU))
        // before the key was checked: the plan's ids
        assertEquals("claude-sonnet-5-5", ModelCatalog(emptyList()).resolve(Family.SONNET))
        assertTrue(ModelCatalog.legacy("claude-haiku-4-5"))
        assertTrue(ModelCatalog.legacy("claude-3-5-sonnet-latest"))
        assertFalse(ModelCatalog.legacy("claude-haiku-5-5"))
        assertFalse(ModelCatalog.legacy("claude-opus-5"))
    }
}
