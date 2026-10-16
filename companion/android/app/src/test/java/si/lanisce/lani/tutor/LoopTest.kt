package si.lanisce.lani.tutor

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/** The tool loop with a fake transport: tool calls, errors, max_tokens, refusal, the call limit, resuming. */
class LoopTest {
    private val prefix = Prefix(JsonArray(listOf(ToolSpec("reply", "", obj("type" to "object")).definition())), "core")

    private class Runner(val delayMs: Long = 0) : ToolRunner {
        val ran = ConcurrentHashMap<String, Long>()
        override suspend fun run(use: ToolUse): ToolOutcome {
            ran[use.id] = System.nanoTime()
            if (delayMs > 0) delay(delayMs)
            return ToolOutcome("ok ${use.name}", false, ToolCallRecord(use.id, use.name, use.input, "read", "ok"))
        }
    }

    private fun turn() = mutableListOf(userText("<event kind=\"chat\">Živjo</event>"))

    @Test fun `parallel tool calls run together and answer in one message, in order`() = runBlocking {
        val t = ScriptedTransport { body, n ->
            if (n == 1) message("tool_use", thinking(), text("Pogledam."), toolUse("a", "learner_state"), toolUse("b", "guide", obj("topic" to "feedback")))
            else message("end_turn", text("done"))
        }
        val runner = Runner(delayMs = 200)
        val m = turn()
        val started = System.currentTimeMillis()
        val r = TutorLoop(t, runner).run(prefix, "claude-sonnet-5-5", Task.CHAT, m)
        assertTrue("ran together", System.currentTimeMillis() - started < 390)
        assertEquals(LoopEnd.Done, r.end)
        assertEquals(2, r.calls)
        // user, assistant (tool_use), user (both results), assistant (end)
        assertEquals(listOf("user", "assistant", "user", "assistant"), m.map { it.role() })
        val results = m[2].blocks()
        assertEquals(listOf("a", "b"), results.map { it["tool_use_id"]!!.jsonPrimitive.content })
        // the second call sends the first answer back unchanged: its thinking block with its signature
        val sent = (t.bodies[1]["messages"] as JsonArray)[1].jsonObject
        assertEquals(thinking(), sent.blocks().first())
        // the request: auto tool choice, adaptive thinking with the task's effort, the 1-hour breakpoint, automatic caching
        val body = t.bodies[0]
        assertEquals("auto", body["tool_choice"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("adaptive", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("medium", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals("1h", body["system"]!!.jsonArray.last().jsonObject["cache_control"]!!.jsonObject["ttl"]!!.jsonPrimitive.content)
        assertEquals("ephemeral", body["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(true, body["tools"]!!.jsonArray[0].jsonObject["eager_input_streaming"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test fun `at most eight calls a turn`() = runBlocking {
        var n = 0
        val t = ScriptedTransport { _, _ -> n++; message("tool_use", toolUse("x$n", "learner_state")) }
        val m = turn()
        val r = TutorLoop(t, Runner()).run(prefix, "claude-haiku-5-5", Task.ROUTINE, m)
        assertEquals(LoopEnd.Limit, r.end)
        assertEquals(8, r.calls)
        assertEquals(8, t.bodies.size)
        // the last step is saved: the tools' results, so a next turn's event joins that message
        assertEquals("user", m.last().role())
    }

    @Test fun `max_tokens is retried once with more room, then the turn ends truncated`() = runBlocking {
        val t = ScriptedTransport { _, _ -> message("max_tokens", text("Ker je to …")) }
        val m = turn()
        val r = TutorLoop(t, Runner()).run(prefix, "claude-sonnet-5-5", Task.CHAT, m)
        assertEquals(LoopEnd.Truncated, r.end)
        assertEquals(listOf(16_000, 32_000), t.bodies.map { it["max_tokens"]!!.jsonPrimitive.int })
        assertEquals("assistant", m.last().role()) // what was said stays
    }

    @Test fun `a tool call cut off by max_tokens never runs`() = runBlocking {
        var n = 0
        val t = ScriptedTransport { _, _ -> n++; if (n == 1) message("max_tokens", toolUse("cut", "reply")) else message("end_turn", text("ok")) }
        val runner = Runner()
        val r = TutorLoop(t, runner).run(prefix, "claude-sonnet-5-5", Task.CHAT, turn())
        assertEquals(LoopEnd.Done, r.end)
        assertTrue(runner.ran.isEmpty())
    }

    @Test fun `a refusal is not retried and appends nothing`() = runBlocking {
        val t = ScriptedTransport { _, _ -> AssistantMessage("m", null, emptyList(), "refusal", obj("type" to "refusal", "category" to "cyber"), Usage()) }
        val m = turn()
        val r = TutorLoop(t, Runner()).run(prefix, "claude-sonnet-5-5", Task.CHAT, m)
        assertTrue(r.end is LoopEnd.Refused)
        assertEquals(1, t.bodies.size)
        assertEquals(1, m.size)
    }

    @Test fun `429 waits for retry-after, 529 backs off, 401 is not retried`() = runBlocking {
        var n = 0
        val waits = mutableListOf<Long>()
        val t = object : TutorTransport {
            override suspend fun models() = emptyList<ModelInfo>()
            override suspend fun stream(body: JsonObject, onDelta: () -> Unit): StreamResult {
                n++
                when (n) {
                    1 -> throw ApiError.RateLimited(3_000, "429")
                    2 -> throw ApiError.Overloaded(529, "overloaded")
                    else -> return StreamResult(message("end_turn", text("ok")), 1, 2)
                }
            }
        }
        val outcomes = mutableListOf<CallOutcome>()
        val r = TutorLoop(t, Runner(), sleep = { waits += it }).run(prefix, "claude-sonnet-5-5", Task.CHAT, turn(), onCall = { outcomes += it })
        assertEquals(LoopEnd.Done, r.end)
        assertEquals(listOf(3_000L, 2_000L), waits) // retry-after, then backoff (attempt 1: 2 s)
        assertEquals(listOf(true, true, false), outcomes.map { it.error != null })

        val auth = object : TutorTransport {
            var calls = 0
            override suspend fun models() = emptyList<ModelInfo>()
            override suspend fun stream(body: JsonObject, onDelta: () -> Unit): StreamResult { calls++; throw ApiError.Auth("401") }
        }
        val failed = TutorLoop(auth, Runner(), sleep = { error("no wait for a 401") }).run(prefix, "claude-sonnet-5-5", Task.CHAT, turn())
        assertTrue((failed.end as LoopEnd.Failed).error is ApiError.Auth)
        assertEquals(1, auth.calls)
    }

    @Test fun `a long retry-after goes back to the queue instead of blocking the turn`() = runBlocking {
        val t = object : TutorTransport {
            override suspend fun models() = emptyList<ModelInfo>()
            override suspend fun stream(body: JsonObject, onDelta: () -> Unit): StreamResult = throw ApiError.RateLimited(120_000, "429")
        }
        val r = TutorLoop(t, Runner(), sleep = { error("no wait") }).run(prefix, "claude-sonnet-5-5", Task.CHAT, turn())
        assertTrue((r.end as LoopEnd.Failed).error is ApiError.RateLimited)
    }

    @Test fun `the budget is checked before every call`() = runBlocking {
        var allowed = 1
        val t = ScriptedTransport { _, _ -> message("tool_use", toolUse("x", "learner_state")) }
        val r = TutorLoop(t, Runner(), budgetOk = { allowed-- > 0 }).run(prefix, "claude-sonnet-5-5", Task.CHAT, turn())
        assertEquals(LoopEnd.Paused, r.end)
        assertEquals(1, t.bodies.size)
    }

    @Test fun `a resumed turn runs the saved step's tools, then calls`() = runBlocking {
        val saved = mutableListOf(userText("<event/>"), TutorLoop.assistant(listOf(thinking(), toolUse("r1", "learner_state"))))
        val t = ScriptedTransport { _, _ -> message("end_turn", text("ok")) }
        val runner = Runner()
        val r = TutorLoop(t, runner).run(prefix, "claude-sonnet-5-5", Task.CHAT, saved, callsDone = 1)
        assertEquals(LoopEnd.Done, r.end)
        assertEquals(setOf("r1"), runner.ran.keys)
        assertEquals(2, r.calls)
        assertEquals(1, t.bodies.size)
        // a turn already answered isn't called again
        val done = TutorLoop(t, runner).run(prefix, "claude-sonnet-5-5", Task.CHAT, saved)
        assertEquals(LoopEnd.Done, done.end)
        assertEquals(1, t.bodies.size)
    }

    @Test fun `a turn that ends with text and no tool call gives its text, one that ends after a tool call gives its last`() = runBlocking {
        // Haiku 5.5 at low in Jan's shadow: the whole answer as text, end_turn, no reply
        val plain = ScriptedTransport { _, _ -> message("end_turn", thinking(), text("Odlično, 20/20!"), text("Tako naprej.")) }
        val r = TutorLoop(plain, Runner()).run(prefix, "claude-haiku-5-5", Task.ROUTINE, turn(), effort = "low")
        assertEquals(LoopEnd.Done, r.end)
        assertEquals("Odlično, 20/20!\nTako naprej.", r.endText)
        assertEquals("low", plain.bodies[0]["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        // a stop that isn't end_turn, or no text: none
        val cut = ScriptedTransport { _, _ -> message("stop_sequence", text("Odlično")) }
        assertEquals(null, TutorLoop(cut, Runner()).run(prefix, "claude-haiku-5-5", Task.ROUTINE, turn()).endText)
        val silent = ScriptedTransport { _, _ -> message("end_turn", thinking()) }
        assertEquals(null, TutorLoop(silent, Runner()).run(prefix, "claude-haiku-5-5", Task.ROUTINE, turn()).endText)
    }

    @Test fun `the reply fallback - only a done turn without a reply, never a closing`() {
        val done = TutorLoop.Result(LoopEnd.Done, 1, "  Bravo!  ")
        assertEquals(ShadowReply("main", "Bravo!", 7), ReplyFallback.of(done, TurnKind.STATELESS, replied = false, conversation = "main", at = 7))
        assertEquals("ex-3", ReplyFallback.of(done, TurnKind.DAY, false, "ex-3", 7)!!.conversationId)
        assertEquals(null, ReplyFallback.of(done, TurnKind.DAY, replied = true, conversation = "main", at = 7))
        assertEquals(null, ReplyFallback.of(done, TurnKind.CLOSING, false, "main", 7))
        assertEquals(null, ReplyFallback.of(TutorLoop.Result(LoopEnd.Limit, 8, "Bravo"), TurnKind.DAY, false, "main", 7))
        assertEquals(null, ReplyFallback.of(TutorLoop.Result(LoopEnd.Done, 1, " "), TurnKind.DAY, false, "main", 7))
    }

    @Test fun `max_tokens by the level - room for the thinking at xhigh and max`() = runBlocking {
        val t = ScriptedTransport { _, _ -> message("end_turn", text("ok")) }
        TutorLoop(t, Runner()).run(prefix, "claude-haiku-5-5", Task.ROUTINE, turn(), effort = "high")
        TutorLoop(t, Runner()).run(prefix, "claude-haiku-5-5", Task.ROUTINE, turn(), effort = "xhigh")
        TutorLoop(t, Runner()).run(prefix, "claude-opus-5-5", Task.CONTENT, turn(), effort = "max")
        assertEquals(listOf(16_000, 32_000, 64_000), t.bodies.map { it["max_tokens"]!!.jsonPrimitive.int })
    }

    @Test fun `Haiku 4_5 gets no adaptive thinking and no effort`() = runBlocking {
        val t = ScriptedTransport { _, _ -> message("end_turn", text("ok")) }
        TutorLoop(t, Runner()).run(prefix, "claude-haiku-4-5", Task.ROUTINE, turn())
        assertTrue("thinking" !in t.bodies[0] && "output_config" !in t.bodies[0])
        assertTrue(ModelCatalog.adaptive("claude-haiku-5-5"))
    }
}
