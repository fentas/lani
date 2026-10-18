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

    @Test fun `OpenAI's prices - cached input, cache writes at 1_25 times input, long prompts, the ChatGPT plan at 0`() {
        // GPT-6.1 Sol: $2 in, $0.10 cached, $2.50 writes, $10 out (developers.openai.com/api/docs/pricing, 2026-10-18)
        val warm = Usage(input = 200, cacheWrite5m = 800, cacheRead = 12_000, output = 300, reasoning = 220)
        assertEquals((200 * 2.0 + 800 * 2.5 + 12_000 * 0.10 + 300 * 10.0) / 1e6, prices.cost("gpt-6.1-sol", warm)!!, 1e-12)
        assertEquals((1_000 * 10.0 + 1_000 * 50.0) / 1e6, prices.cost("gpt-6-astra", Usage(input = 1_000, output = 1_000))!!, 1e-12)
        assertEquals((100_000 * 0.01) / 1e6, prices.cost("gpt-6-luna", Usage(cacheRead = 100_000))!!, 1e-12)
        // over 272k: 2x input and cache, 1.5x output
        val long = Usage(input = 300_000, output = 1_000)
        assertEquals((2 * 300_000 * 2.0 + 1.5 * 1_000 * 10.0) / 1e6, prices.cost("gpt-6.1-sol", long)!!, 1e-12)
        // a snapshot of a family prices as the family
        assertEquals(prices.cost("gpt-6-luna", warm), prices.cost("gpt-6-luna-2026-08-01", warm))
        // the ChatGPT plan: nothing per token, the price as the API-equivalent
        assertEquals(Priced(0.0, prices.cost("gpt-6.1-sol", warm)), prices.priced("gpt-6.1-sol", warm, Billing.CHATGPT_PLAN))
        assertEquals(Priced(prices.cost("gpt-6.1-sol", warm)), prices.priced("gpt-6.1-sol", warm, Billing.API_KEY))
        // the Claude rows as before
        assertEquals(Priced(prices.cost("claude-sonnet-5-5", warm)), prices.priced("claude-sonnet-5-5", warm, Billing.API_KEY))
    }

    @Test fun `a usage without reasoning tokens is written as before`() {
        assertEquals("""{"input":1,"cacheWrite5m":2,"cacheWrite1h":3,"cacheRead":4,"output":5}""", tutorJson.encodeToString(Usage.serializer(), Usage(1, 2, 3, 4, 5)))
        assertEquals("""{"input":1,"cacheWrite5m":0,"cacheWrite1h":0,"cacheRead":0,"output":5,"reasoning":3}""", tutorJson.encodeToString(Usage.serializer(), Usage(1, output = 5, reasoning = 3)))
        assertEquals(Usage(1, output = 5, reasoning = 3), tutorJson.decodeFromString(Usage.serializer(), """{"input":1,"output":5,"reasoning":3}"""))
    }
}
