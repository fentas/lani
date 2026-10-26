package si.lanisce.lani.tutor

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The effort per task: which levels a model takes, the presets and our recommendation, and the per-message effort
 * within a thread (where the effort message goes, the beta, the thread's history never changed where a reply came).
 */
class EffortTest {
    private val prefix = Prefix(JsonArray(listOf(ToolSpec("reply", "", obj("type" to "object")).definition())), "core")

    @Test fun `the levels a model takes, and a level it doesn't fitted below`() {
        assertEquals(Effort.LEVELS, ModelCatalog.efforts("claude-haiku-5-5"))
        assertEquals(Effort.LEVELS, ModelCatalog.efforts("claude-sonnet-5-5"))
        assertEquals(Effort.LEVELS, ModelCatalog.efforts("claude-opus-5-5"))
        assertEquals(listOf("low", "medium", "high", "max"), ModelCatalog.efforts("claude-sonnet-4-6"))
        assertTrue(ModelCatalog.efforts("claude-haiku-4-5").isEmpty())
        assertEquals("high", ModelCatalog.fit("xhigh", "claude-sonnet-4-6"))
        assertEquals("xhigh", ModelCatalog.fit("xhigh", "claude-haiku-5-5"))
        assertNull(ModelCatalog.fit("high", "claude-haiku-4-5"))
        assertEquals("medium", ModelCatalog.defaultEffort("claude-haiku-5-5"))
        assertEquals("medium", ModelCatalog.defaultEffort("claude-opus-5-5"))
        assertEquals("high", ModelCatalog.defaultEffort("claude-sonnet-5-5"))
        assertNull(ModelCatalog.defaultEffort("claude-haiku-4-5"))
    }

    @Test fun `per-message effort on the 5_5 models and Opus 5, not on Sonnet 5 or Haiku 4_5`() {
        for (m in listOf("claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5", "claude-opus-5", "claude-haiku-5-5-20261007")) assertTrue(m, ModelCatalog.perMessageEffort(m))
        for (m in listOf("claude-sonnet-5", "claude-haiku-4-5", "claude-opus-4-8", "claude-fable-5")) assertFalse(m, ModelCatalog.perMessageEffort(m))
    }

    @Test fun `our recommendation is Balanced, the presets set model and effort together`() {
        val c = ModelCatalog(listOf(ModelInfo("claude-opus-5-5"), ModelInfo("claude-sonnet-5-5"), ModelInfo("claude-haiku-5-5")))
        fun choice(p: Preset, t: Task) = c.modelFor(t, p).let { it to ModelCatalog.effortFor(t, p, it) }
        // the matrix (2026-10-19 to 10-22): chat on Sonnet at xhigh, grading on Haiku at high, routine on Haiku at xhigh,
        // role-play on Sonnet at low, content on Opus at medium
        assertEquals("claude-haiku-5-5" to "xhigh", choice(Preset.BALANCED, Task.ROUTINE))
        assertEquals("claude-haiku-5-5" to "high", choice(Preset.BALANCED, Task.GRADING))
        assertEquals("claude-sonnet-5-5" to "xhigh", choice(Preset.BALANCED, Task.CHAT))
        assertEquals("claude-sonnet-5-5" to "low", choice(Preset.BALANCED, Task.ROLEPLAY))
        assertEquals("claude-opus-5-5" to "medium", choice(Preset.BALANCED, Task.CONTENT))
        for (t in Task.entries) assertEquals(t.name, c.resolve(t.family) to t.effort, choice(Preset.BALANCED, t))
        // Economy: Haiku, chat at xhigh, grading and routine at high, role-play at low; content on Sonnet at xhigh
        assertEquals("claude-haiku-5-5" to "xhigh", choice(Preset.ECONOMY, Task.CHAT))
        assertEquals("claude-haiku-5-5" to "high", choice(Preset.ECONOMY, Task.GRADING))
        assertEquals("claude-haiku-5-5" to "high", choice(Preset.ECONOMY, Task.ROUTINE))
        assertEquals("claude-haiku-5-5" to "low", choice(Preset.ECONOMY, Task.ROLEPLAY))
        assertEquals("claude-sonnet-5-5" to "xhigh", choice(Preset.ECONOMY, Task.CONTENT))
        // Best: Opus at medium, content at high
        for (t in listOf(Task.CHAT, Task.GRADING, Task.ROUTINE, Task.ROLEPLAY)) assertEquals(t.name, "claude-opus-5-5" to "medium", choice(Preset.BEST, t))
        assertEquals("claude-opus-5-5" to "high", choice(Preset.BEST, Task.CONTENT))
        // the panel's effort for a task wins, fitted to the model
        assertEquals("low", ModelCatalog.effortFor(Task.ROUTINE, Preset.BALANCED, "claude-haiku-5-5", mapOf(Task.ROUTINE to "low")))
        assertNull(ModelCatalog.effortFor(Task.ROUTINE, Preset.BALANCED, "claude-haiku-4-5", mapOf(Task.ROUTINE to "low")))
    }

    @Test fun `with OpenAI the presets pick GPT-6_1 Sol for chat, grading and routine, never GPT-6 Luna`() {
        // the matrix: Sol at low as economy (Σ25), Sol at medium otherwise (Σ27); Astra scored no higher; Luna not acceptable
        for (t in listOf(Task.CHAT, Task.GRADING, Task.ROUTINE)) {
            assertEquals(t.name, Family.SOL to "low", Preset.ECONOMY.family(t, ProviderId.OPENAI) to Preset.ECONOMY.effort(t, ProviderId.OPENAI))
            assertEquals(t.name, Family.SOL to "medium", Preset.BALANCED.family(t, ProviderId.OPENAI) to Preset.BALANCED.effort(t, ProviderId.OPENAI))
            assertEquals(t.name, Family.SOL to "medium", Preset.BEST.family(t, ProviderId.OPENAI) to Preset.BEST.effort(t, ProviderId.OPENAI))
        }
        // role-play and content keep their tier's family, at the efforts they had
        assertEquals(Family.SOL to "low", Preset.ECONOMY.family(Task.ROLEPLAY, ProviderId.OPENAI) to Preset.ECONOMY.effort(Task.ROLEPLAY, ProviderId.OPENAI))
        assertEquals(Family.SOL to "low", Preset.BALANCED.family(Task.ROLEPLAY, ProviderId.OPENAI) to Preset.BALANCED.effort(Task.ROLEPLAY, ProviderId.OPENAI))
        assertEquals(Family.ASTRA to "medium", Preset.BALANCED.family(Task.CONTENT, ProviderId.OPENAI) to Preset.BALANCED.effort(Task.CONTENT, ProviderId.OPENAI))
        assertEquals(Family.ASTRA to "high", Preset.BEST.family(Task.CONTENT, ProviderId.OPENAI) to Preset.BEST.effort(Task.CONTENT, ProviderId.OPENAI))
        for (p in Preset.entries) for (t in Task.entries) assertFalse("$p $t", Preset.valueOf(p.name).family(t, ProviderId.OPENAI).matrixOnly)
        // the effort follows the model's provider: Sol's own on an OpenAI model, the preset's on Claude's
        assertEquals("medium", ModelCatalog.effortFor(Task.CHAT, Preset.BALANCED, "gpt-6.1-sol"))
        assertEquals("xhigh", ModelCatalog.effortFor(Task.CHAT, Preset.BALANCED, "claude-sonnet-5-5"))
        assertEquals("high", ModelCatalog.effortFor(Task.CHAT, Preset.BALANCED, "gpt-6.1-sol", mapOf(Task.CHAT to "high")))
        // through OpenRouter: an OpenAI model at OpenAI's picks' level, the others at the Anthropic preset's, fitted
        assertEquals("medium", ModelCatalog.effortFor(Task.ROUTINE, Preset.BALANCED, "openai/gpt-6.1-sol"))
        assertEquals("xhigh", ModelCatalog.effortFor(Task.CHAT, Preset.BALANCED, "anthropic/claude-sonnet-5.5"))
        // Gemini plays each task's tier, at the Anthropic preset's effort fitted to the model (no xhigh there: high)
        assertEquals(Family.FLASH_LITE, Preset.BALANCED.family(Task.GRADING, ProviderId.GEMINI))
        assertEquals(Family.FLASH, Preset.BALANCED.family(Task.CHAT, ProviderId.GEMINI))
        assertEquals(Family.ARGON, Preset.BEST.family(Task.CHAT, ProviderId.GEMINI))
        assertEquals("high", ModelCatalog.effortFor(Task.CHAT, Preset.BALANCED, "gemini-3.8-flash"))
    }

    @Test fun `GPT-6 Luna is for the matrix only, direct or through OpenRouter`() {
        assertTrue(Family.LUNA.matrixOnly)
        assertTrue(Family.matrixOnly("gpt-6-luna"))
        assertTrue(Family.matrixOnly("gpt-6-luna-2026-08-01"))
        assertTrue(Family.matrixOnly("openai/gpt-6-luna"))
        for (m in listOf("gpt-6.1-sol", "gpt-6-astra", "openai/gpt-6.1-sol", "claude-haiku-5-5", "google/gemini-3.8-flash", "some-lab/luna-7b")) assertFalse(m, Family.matrixOnly(m))
        // OpenRouter's small tier is Sol now, not Luna
        assertEquals(OpenRouterModels.SOL, OpenRouterModels.forTier(0))
        assertTrue(OpenRouterModels.DEFAULTS.none { Family.matrixOnly(it) })
    }

    @Test fun `room for thinking - routine and grading 16k, content 32k, more at xhigh and max`() {
        assertEquals(16_000, Task.ROUTINE.maxTokens)
        assertEquals(16_000, Task.GRADING.maxTokens)
        assertEquals(32_000, Task.CONTENT.maxTokens)
        assertEquals(16_000, Task.ROUTINE.maxTokensAt("high"))
        assertEquals(32_000, Task.ROUTINE.maxTokensAt("xhigh"))
        assertEquals(64_000, Task.CONTENT.maxTokensAt("max"))
    }

    // --- the per-message effort in a thread ------------------------------------------------------------------------

    private val u1 = userText("<notebook/>", "<event kind=\"chat\">Živjo</event>")
    private val a1 = TutorLoop.assistant(listOf(thinking("s1"), text("Živjo!")))
    private val next = listOf(text("<event kind=\"answer\" conversation_id=\"ex-1\">Jaz sem Jan.</event>"))

    private fun roles(m: List<JsonObject>) = m.map { Effort.of(it)?.let { e -> "effort:$e" } ?: it.role() }

    @Test fun `a turn at another level - an effort message after the last answer, before its user message`() {
        val m = Effort.append(listOf(u1, a1), next, top = "medium", want = "high")
        assertEquals(listOf("user", "assistant", "effort:high", "user"), roles(m))
        assertEquals(listOf(u1, a1), m.take(2)) // the history a reply came after: as it was
        assertEquals(Effort.message("high"), m[2])
        assertEquals("""{"role":"system","content":[],"output_config":{"effort":"high"}}""", m[2].toString())
        assertEquals("high", Effort.inEffect(m, "medium"))
        // the same level as in effect: nothing added
        assertEquals(listOf("user", "assistant", "user"), roles(Effort.append(listOf(u1, a1), next, "medium", "medium")))
        assertEquals(listOf("user", "assistant", "user"), roles(Effort.append(listOf(u1, a1), next, "medium", null)))
    }

    @Test fun `the level holds until changed - the next chat goes back to the thread's by a message of its own`() {
        val graded = Effort.append(listOf(u1, a1), next, "medium", "high") + TutorLoop.assistant(listOf(text("8/10")))
        val chat = Effort.append(graded, listOf(text("<event kind=\"chat\">Hvala</event>")), "medium", "medium")
        assertEquals(listOf("user", "assistant", "effort:high", "user", "assistant", "effort:medium", "user"), roles(chat))
        assertEquals(graded, chat.take(graded.size))
        assertEquals("medium", Effort.inEffect(chat, "medium"))
        assertTrue(Effort.byMessage(chat))
    }

    @Test fun `an unanswered end - the next turn joins its user message, its effort message placed again, not stacked`() {
        val stopped = Effort.append(listOf(u1, a1), next, "medium", "high") // that turn failed: no answer
        val more = listOf(text("<event kind=\"chat\">Še?</event>"))
        val low = Effort.append(stopped, more, "medium", "low")
        assertEquals(listOf("user", "assistant", "effort:low", "user"), roles(low))
        assertEquals(next + more, low.last().blocks())
        // back at the thread's level: the unanswered turn's effort message goes
        assertEquals(listOf("user", "assistant", "user"), roles(Effort.append(stopped, more, "medium", "medium")))
        // a restarted thread's first message (no answer yet) takes the turn's blocks, the effort message before it
        assertEquals(listOf("effort:high", "user"), roles(Effort.append(listOf(userText("<notebook/>")), next, "medium", "high")))
    }

    @Test fun `never between a tool_use and its results - after a user message with tool_results, which it applies to`() {
        val use = TutorLoop.assistant(listOf(toolUse("t1", "learner_state")))
        val results = TutorLoop.toolResults(listOf(toolUse("t1", "learner_state")), listOf(ToolOutcome("{}", false, ToolCallRecord("t1", "learner_state", obj(), "read", "{}"))))
        val limit = listOf(u1, use, results) // the turn used its calls: its tools' results end the thread
        val m = Effort.append(limit, next, "medium", "high")
        assertEquals(listOf("user", "assistant", "user", "effort:high"), roles(m))
        assertEquals(listOf("tool_result", "text"), m[2].blocks().map { it.str("type") })
        // that turn stopped too: the next one joins the same message, the effort message placed again at the end
        val again = Effort.append(m, listOf(text("Še?")), "medium", "high")
        assertEquals(listOf("user", "assistant", "user", "effort:high"), roles(again))
        assertEquals(3, again[2].blocks().size)
    }

    @Test fun `the request - top-level effort the thread's, the effort message in its messages, the beta only then`() {
        val m = Effort.append(listOf(u1, a1), next, "medium", "high")
        val body = prefix.request("claude-sonnet-5-5", Task.GRADING, m, effort = "medium")
        assertEquals("medium", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals(m, body["messages"]!!.jsonArray.map { it.jsonObject })
        assertTrue(Effort.inRequest(body))
        assertEquals(listOf("thinking-binding-controls-2026-08-01", "mid-conversation-output-config-2026-07-01"), AnthropicTransport.betas(body))
        val plain = prefix.request("claude-sonnet-5-5", Task.CHAT, listOf(u1), effort = "medium")
        assertFalse(Effort.inRequest(plain))
        assertEquals(listOf("thinking-binding-controls-2026-08-01"), AnthropicTransport.betas(plain))
        // a model that takes no effort: thinking adaptive is left out with it (Haiku 4.5), and a null effort sends none
        val none = prefix.request("claude-sonnet-5-5", Task.CHAT, listOf(u1), effort = null)
        assertTrue("output_config" !in none && "thinking" in none)
    }

    @Test fun `the numbers by model and effort - spend, output, the thinking's estimate, how the turns replied`() {
        fun call(id: String, turn: String, model: String, effort: String?, out: Long, visible: Int, cost: Double) =
            CallRecord(id, turn, model, "chat", Task.CHAT, 0, Usage(output = out), cost = cost, effort = effort, visibleChars = visible)
        val calls = listOf(
            call("c1", "t1", "claude-haiku-5-5", "low", 887, 1_488, 0.0005),
            call("c2", "t2", "claude-haiku-5-5", "high", 1_200, 800, 0.0007),
            call("c3", "t2", "claude-haiku-5-5", "high", 300, 400, 0.0002),
        )
        val turns = listOf(
            TurnRecord("t1", listOf("e"), TurnKind.ALSO, Task.ROUTINE, "claude-haiku-5-5", "review_done", 0, status = TurnStatus.DONE, callIds = listOf("c1"), effort = "low", replyFromText = true, replies = listOf(ShadowReply("main", "Bravo", 0))),
            TurnRecord("t2", listOf("e"), TurnKind.ALSO, Task.ROUTINE, "claude-haiku-5-5", "review_done", 0, status = TurnStatus.DONE, callIds = listOf("c2", "c3"), effort = "high", replies = listOf(ShadowReply("main", "Bravo!", 0))),
        )
        val s = DayStats.of(DayLog("2026-10-16", turns = turns, calls = calls), 0)
        assertEquals(1, s.repliesFromText)
        val low = s.byModelEffort.getValue("claude-haiku-5-5 · low")
        assertEquals(RunStats(calls = 1, turns = 1, spend = 0.0005, output = 887, thinking = 887 - 372, repliesFromText = 1), low)
        val high = s.byModelEffort.getValue("claude-haiku-5-5 · high")
        assertEquals(2, high.calls)
        assertEquals(1_500L, high.output)
        assertEquals((1_200L - 200) + (300 - 100), high.thinking)
        assertEquals(1, high.repliesByTool)
        assertEquals(0.0009, high.spend, 1e-12)
        assertEquals("claude-haiku-4-5 · —", Effort.key("claude-haiku-4-5", null))
    }
}
