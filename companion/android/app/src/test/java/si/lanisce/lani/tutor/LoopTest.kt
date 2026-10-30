package si.lanisce.lani.tutor

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
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

    /**
     * On the test scheduler's virtual time (runTest; docs/plans/05-built/nav-perf.md): the two calls' 200 ms take 200 ms of
     * its clock when they run together and 400 one after the other, whatever the machine's load (the wall clock's "under
     * 390 ms" could fail on a busy machine with the calls together all the same).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `parallel tool calls run together and answer in one message, in order`() = kotlinx.coroutines.test.runTest {
        val t = ScriptedTransport { body, n ->
            if (n == 1) message("tool_use", thinking(), text("Pogledam."), toolUse("a", "learner_state"), toolUse("b", "guide", obj("topic" to "feedback")))
            else message("end_turn", text("done"))
        }
        val runner = Runner(delayMs = 200)
        val m = turn()
        val started = currentTime
        val r = TutorLoop(t, runner).run(prefix, "claude-sonnet-5-5", Task.CHAT, m)
        assertEquals("ran together: 200 ms of the virtual clock, not 400", 200L, currentTime - started)
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
        assertEquals("the chat's recommended level (the matrix: Sonnet at xhigh)", "xhigh", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
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
        val r = TutorLoop(t, Runner()).run(prefix, "claude-sonnet-5-5", Task.CHAT, m, effort = "medium")
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
        // at the limit only the loop's last word has text (the tools-off call): it is the reply
        assertEquals("Bravo", ReplyFallback.of(TutorLoop.Result(LoopEnd.Limit, 9, "Bravo"), TurnKind.DAY, false, "main", 7)!!.text)
        assertEquals(null, ReplyFallback.of(TutorLoop.Result(LoopEnd.Limit, 8), TurnKind.DAY, false, "main", 7))
        assertEquals(null, ReplyFallback.of(TutorLoop.Result(LoopEnd.Truncated, 2, "Bravo"), TurnKind.DAY, false, "main", 7))
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

    // --- never a silent turn (the matrix, 2026-10) ------------------------------------------------------------------

    /** A runner whose `reply` counts as sent (the turn's [Owed]). */
    private class Replies : ToolRunner {
        val sent = mutableListOf<String>()
        override suspend fun run(use: ToolUse): ToolOutcome {
            if (use.name == "reply") sent += use.input["text"]!!.jsonPrimitive.content
            return ToolOutcome("ok ${use.name}", false, ToolCallRecord(use.id, use.name, use.input, "read", "ok"))
        }
    }

    private fun lastResult(body: JsonObject): String = (body["messages"] as JsonArray).last().jsonObject.blocks().last()["content"]!!.jsonPrimitive.content

    @Test fun `a turn looping without a reply is told one call is left, then gets a last word with the tools off`() = runBlocking {
        var n = 0
        val t = ScriptedTransport { body, _ ->
            n++
            if (body["tool_choice"]!!.jsonObject["type"]!!.jsonPrimitive.content == "none") message("end_turn", text("Kartico sem popravil: mrzel šteje."))
            else message("tool_use", toolUse("c$n", "card", obj("action" to "show", "card_id" to "vocab_mrzlo")))
        }
        val runner = Replies()
        val m = turn()
        val r = TutorLoop(t, runner).run(prefix, "claude-haiku-5-5", Task.ROUTINE, m, owed = Owed({ runner.sent.isNotEmpty() }, required = false))
        assertEquals(LoopEnd.Limit, r.end)
        assertEquals("eight calls and the last word", 9, r.calls)
        assertEquals("Kartico sem popravil: mrzel šteje.", r.endText)
        // the seventh call's results say one call is left; the eighth's that the tools are off
        assertTrue(lastResult(t.bodies[7]).endsWith(TutorLoop.LAST_CALL))
        assertTrue(lastResult(t.bodies[8]).endsWith(TutorLoop.TOOLS_OFF))
        assertTrue((0..5).none { lastResult(t.bodies[it + 1]).contains("[Lani]") })
        // only the last word goes with the tools off; the tools stay listed (the prefix's cache)
        assertEquals(listOf("auto"), t.bodies.dropLast(1).map { it["tool_choice"]!!.jsonObject["type"]!!.jsonPrimitive.content }.distinct())
        assertEquals("none", t.bodies.last()["tool_choice"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(t.bodies[0]["tools"], t.bodies.last()["tools"])
        // the thread keeps the answer; the reply fallback sends it
        assertEquals("assistant", m.last().role())
        assertEquals("Kartico sem popravil: mrzel šteje.", ReplyFallback.of(r, TurnKind.DAY, false, "main", 1)!!.text)
    }

    @Test fun `a turn that replied runs to its limit as before, with no note and no last word`() = runBlocking {
        var n = 0
        val t = ScriptedTransport { _, _ ->
            n++
            if (n == 1) message("tool_use", toolUse("r", "reply", obj("conversation_id" to "main", "text" to "Že popravljam.")))
            else message("tool_use", toolUse("c$n", "card", obj("action" to "show")))
        }
        val runner = Replies()
        val r = TutorLoop(t, runner).run(prefix, "claude-haiku-5-5", Task.ROUTINE, turn(), owed = Owed({ runner.sent.isNotEmpty() }, required = true))
        assertEquals(LoopEnd.Limit, r.end)
        assertEquals(8, r.calls)
        assertTrue(t.bodies.none { lastUser(it).contains("[Lani]") })
    }

    @Test fun `the one-call-left note leads to the reply`() = runBlocking {
        val t = ScriptedTransport { body, n ->
            if (lastUser(body).contains("One call is left")) message("tool_use", toolUse("r", "reply", obj("conversation_id" to "main", "text" to "Tako je.")))
            else message("tool_use", toolUse("c$n", "learner_state"))
        }
        val runner = Replies()
        val r = TutorLoop(t, runner).run(prefix, "claude-sonnet-5-5", Task.CHAT, turn(), owed = Owed({ runner.sent.isNotEmpty() }, required = true))
        assertEquals(listOf("Tako je."), runner.sent)
        assertEquals(LoopEnd.Limit, r.end)
        assertEquals(8, r.calls)
    }

    @Test fun `a tool_use stop with no tool call is asked once more, not kept`() = runBlocking {
        val t = ScriptedTransport { _, n ->
            when (n) {
                1 -> message("tool_use", thinking(), text("Pogledam …")) // the stop says tool_use, no block came
                2 -> message("tool_use", toolUse("r", "reply", obj("conversation_id" to "main", "text" to "Kranj je mesto.")))
                else -> message("end_turn", text("ok"))
            }
        }
        val runner = Replies()
        val m = turn()
        val r = TutorLoop(t, runner).run(prefix, "claude-sonnet-5-5", Task.CHAT, m, owed = Owed({ runner.sent.isNotEmpty() }, required = true))
        assertEquals(listOf("Kranj je mesto."), runner.sent)
        assertEquals(3, r.calls)
        // the empty answer wasn't kept: the second call asked from the same messages
        assertEquals(t.bodies[0]["messages"], t.bodies[1]["messages"])
        assertEquals(listOf("user", "assistant", "user", "assistant"), m.map { it.role() })
    }

    @Test fun `twice a tool_use stop with no tool call - its text is the reply, or the tools-off call gives one`() = runBlocking {
        val worded = ScriptedTransport { _, _ -> message("tool_use", text("Trnovo je srednjega spola: v Trnovem.")) }
        val runner = Replies()
        val r = TutorLoop(worded, runner).run(prefix, "claude-sonnet-5-5", Task.CHAT, turn(), owed = Owed({ runner.sent.isNotEmpty() }, required = true))
        assertEquals(LoopEnd.Done, r.end)
        assertEquals(2, r.calls)
        assertEquals("Trnovo je srednjega spola: v Trnovem.", r.endText)

        val bare = ScriptedTransport { body, _ ->
            if (body["tool_choice"]!!.jsonObject["type"]!!.jsonPrimitive.content == "none") message("end_turn", text("Oprosti, tu je odgovor."))
            else message("tool_use", thinking())
        }
        val m = turn()
        val r2 = TutorLoop(bare, runner).run(prefix, "claude-sonnet-5-5", Task.CHAT, m, owed = Owed({ runner.sent.isNotEmpty() }, required = true))
        assertEquals(LoopEnd.Done, r2.end)
        assertEquals(3, r2.calls)
        assertEquals("Oprosti, tu je odgovor.", r2.endText)
        // the call before the last word ends with the model's answer: the reason goes as a user line, never a prefill
        assertEquals("user", (bare.bodies.last()["messages"] as JsonArray).last().jsonObject.role())
        assertTrue(lastUser(bare.bodies.last()).contains("the `reply` tool wasn't called"))
    }

    @Test fun `a required turn that ends silent gets a last word, a digest may end silent`() = runBlocking {
        val t = ScriptedTransport { body, _ ->
            if (body["tool_choice"]!!.jsonObject["type"]!!.jsonPrimitive.content == "none") message("end_turn", text("Lepo, 7/11."))
            else message("end_turn", thinking())
        }
        val runner = Replies()
        val r = TutorLoop(t, runner).run(prefix, "claude-haiku-5-5", Task.GRADING, turn(), owed = Owed({ runner.sent.isNotEmpty() }, required = true))
        assertEquals("Lepo, 7/11.", r.endText)
        assertEquals(2, r.calls)
        val digest = ScriptedTransport { _, _ -> message("end_turn", thinking()) }
        val d = TutorLoop(digest, runner).run(prefix, "claude-haiku-5-5", Task.ROUTINE, turn(), owed = Owed({ false }, required = false))
        assertEquals(1, d.calls)
        assertEquals(null, d.endText)
        // what a turn owes: a chat, an answer, a session's end must reply; a digest needn't; a closing owes nothing
        assertTrue(Owed.of("chat", TurnKind.DAY) { false }!!.required)
        assertTrue(Owed.of("session_end", TurnKind.STATELESS) { false }!!.required)
        assertTrue(!Owed.of("digest", TurnKind.STATELESS) { false }!!.required)
        assertEquals(null, Owed.of("closing", TurnKind.CLOSING) { false })
        // the dictionary's notes owe nothing: no last-call note, no tools-off call, their text no reply
        assertEquals(null, Owed.of("gloss_request", TurnKind.STATELESS) { false })
        assertEquals(null, Owed.of("gloss_gaps", TurnKind.DAY) { false })
    }

    @Test fun `a network error while offline isn't retried - the queue waits for the network`() = runBlocking {
        var calls = 0
        val t = object : TutorTransport {
            override suspend fun models() = emptyList<ModelInfo>()
            override suspend fun stream(body: JsonObject, onDelta: () -> Unit): StreamResult { calls++; throw ApiError.Network("Unable to resolve host \"api.anthropic.com\"") }
        }
        val offline = OnlineState(false)
        val r = TutorLoop(t, Runner(), sleep = { error("no retry while offline") }, online = offline::now).run(prefix, "claude-sonnet-5-5", Task.CHAT, turn())
        assertTrue((r.end as LoopEnd.Failed).error is ApiError.Network)
        assertEquals(1, calls)
        assertTrue(Failures.offline((r.end as LoopEnd.Failed).error, offline))
        // online, the same error is retried as before (a blip)
        calls = 0
        val waits = mutableListOf<Long>()
        TutorLoop(t, Runner(), sleep = { waits += it }, online = { true }).run(prefix, "claude-sonnet-5-5", Task.CHAT, turn())
        assertEquals(4, calls)
        assertEquals(listOf(1_000L, 2_000L, 4_000L), waits)
        assertTrue(!Failures.offline(ApiError.Network("x"), OnlineState(true)))
        assertTrue(!Failures.offline(ApiError.Overloaded(529, "x"), offline))
    }

    @Test fun `the network state - waits until it's back`() = runBlocking {
        val s = OnlineState(false)
        val waiter = async { s.await(); "back" }
        delay(50)
        assertTrue(!waiter.isCompleted)
        s.set(true)
        assertEquals("back", kotlinx.coroutines.withTimeout(2_000) { waiter.await() })
        assertTrue(Online.ALWAYS.now())
    }
}
