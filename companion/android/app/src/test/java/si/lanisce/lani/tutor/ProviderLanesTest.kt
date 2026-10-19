package si.lanisce.lani.tutor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The matrix's lanes of Gemini and OpenRouter (docs/plans/05-built/providers.md, "Lanes"): their ids carry the provider,
 * an OpenRouter model's directory keeps one level, the plan skips what has no credential, what the list lacks, what
 * the model doesn't take, Argon on the free tier.
 */
class ProviderLanesTest {
    private val gemini = GeminiModels.listed
    private val routed = OpenRouterTransport.parseModels(ROUTER_MODELS)!!.also(RoutedModels::learn)

    @Test fun `lane ids carry the provider - an OpenRouter model's slash stays one directory`() {
        assertEquals("gemini:gemini-4-argon@high", Lanes.id("gemini-4-argon", "high"))
        assertEquals("openrouter:google/gemini-4-argon@medium", Lanes.id("google/gemini-4-argon", "medium"))
        assertEquals("claude-sonnet-5-5@low", Lanes.id("claude-sonnet-5-5", "low"))
        assertEquals("openai:gpt-6.1-sol@medium", Lanes.id("gpt-6.1-sol", "medium"))
        assertEquals("openrouter:google~gemini-4-argon@medium", Lanes.dir("openrouter:google/gemini-4-argon@medium"))
        for (id in listOf("gemini:gemini-4-argon@high", "openrouter:google/gemini-4-argon@medium", "openrouter:google/gemma-4-31b-it:free@none", "claude-haiku-5-5@max", "openai:gpt-6-luna@xhigh")) {
            val (model, effort) = Lanes.parse(id)
            assertEquals(id, Lanes.id(model, effort))
            assertEquals("from its directory too: $id", model to effort, Lanes.parse(Lanes.dir(id)))
        }
        assertEquals("google/gemma-4-31b-it:free" to "none", Lanes.parse("openrouter:google/gemma-4-31b-it:free@none"))
        assertEquals(Family.ROUTED, Lanes.familyOf("google/gemini-4-argon"))
        assertEquals(Family.ARGON, Lanes.familyOf("gemini-4-argon"))
        assertEquals("Flash-Lite", Family.FLASH_LITE.label)
    }

    @Test fun `the plan - no credential, Argon on the free tier, a model the list lacks or an effort it doesn't take`() {
        val m = MatrixSettings(
            enabled = true,
            lanes = MatrixSettings.GEMINI_LANES + LaneSpec(Family.FLASH, "minimal") + MatrixSettings.OPENROUTER_LANES +
                LaneSpec.routed("mistralai/not-listed", "medium") + LaneSpec.routed(OpenRouterModels.GEMMA, Effort.NONE) + LaneSpec.routed(OpenRouterModels.GEMMA, "high"),
        )
        val none = Lanes.plan(m, emptyList())
        assertTrue(none.filter { it.family.provider == ProviderId.GEMINI }.all { it.id == null && it.skipped == "no Gemini key (Google AI Studio)" })
        assertTrue(none.filter { it.family == Family.ROUTED }.all { it.id == null && it.skipped == "not signed in with OpenRouter" })
        val plan = Lanes.plan(m, emptyList(), mapOf(ProviderId.GEMINI to gemini, ProviderId.OPENROUTER to routed))
        fun of(model: String, effort: String) = plan.single { it.model == model && it.effort == effort }
        assertEquals("gemini:gemini-4-argon@high", of("gemini-4-argon", "high").id)
        assertEquals("gemini:gemini-3.8-flash@medium", of("gemini-3.8-flash", "medium").id)
        assertEquals("gemini-3.8-flash takes no minimal", of("gemini-3.8-flash", "minimal").skipped)
        assertEquals("openrouter:google/gemini-4-argon@medium", of("google/gemini-4-argon", "medium").id)
        assertTrue(of("mistralai/not-listed", "medium").skipped!!.contains("has no mistralai/not-listed"))
        assertEquals("a model without reasoning: one lane at none", "openrouter:google/gemma-4-31b-it@none", of(OpenRouterModels.GEMMA, Effort.NONE).id)
        assertNull(of(OpenRouterModels.GEMMA, "high").id)
        // the free tier: no Argon
        val free = Lanes.plan(m, emptyList(), mapOf(ProviderId.GEMINI to gemini), geminiFree = true)
        assertTrue(free.filter { it.family == Family.ARGON }.all { it.id == null && it.skipped!!.contains("no free tier") })
        assertEquals("gemini:gemini-3.8-flash@medium", free.single { it.family == Family.FLASH && it.effort == "medium" }.id)
    }

    @Test fun `providers by model id, families by provider`() {
        assertEquals(ProviderId.OPENROUTER, ProviderId.of("anthropic/claude-sonnet-5.5"))
        assertEquals(ProviderId.GEMINI, ProviderId.of("gemini-3.8-flash"))
        assertEquals(ProviderId.OPENAI, ProviderId.of("gpt-6-luna"))
        assertEquals(ProviderId.ANTHROPIC, ProviderId.of("claude-haiku-5-5"))
        assertEquals("a routed Claude isn't Anthropic's family", Family.ROUTED, Family.ofModel("anthropic/claude-sonnet-5.5"))
        assertEquals(listOf(Family.FLASH_LITE, Family.FLASH, Family.ARGON), Family.of(ProviderId.GEMINI))
        assertEquals(GeminiFormat, ThreadFormat.of("gemini-4-argon"))
        assertEquals(ChatFormat, ThreadFormat.of("google/gemini-4-argon"))
        // a routed model without its reported cost: priced by the vendor's own row
        val prices = Prices.parse(Bundle.text("prices.json")!!)
        assertEquals(prices.of("claude-sonnet-5-5"), prices.of("anthropic/claude-sonnet-5.5"))
        assertEquals(prices.of("gpt-6-luna"), prices.of("openai/gpt-6-luna"))
    }
}

/** Gemini's model list as the key lists it (GEMINI_MODELS). */
private object GeminiModels {
    val listed: List<ModelInfo> = listOf(
        ModelInfo("gemini-4-argon"), ModelInfo("gemini-3.8-flash"), ModelInfo("gemini-3.5-flash-lite"),
    )
}
