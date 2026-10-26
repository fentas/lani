package si.lanisce.lani.tutor

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a reply may carry and when it may go (the matrix, 2026-10: scores on chats and digests; record_session skipped on
 * two session ends).
 */
class ReplyDataTest {
    private val specs = PhaseA.load().associateBy { it.name }
    private val notebook = object : ShadowTools.NotebookHost {
        override fun note(line: String) = Unit
        override fun write(text: String) = Unit
        override val size = 0
    }

    private fun event(kind: String, conv: String? = "main") = ShadowEvent("ev_$kind", kind, 0, "/message", conv)

    @Test fun `a score goes only on the reply to an answer, a town's check, a debrief`() {
        val data = obj("score" to 8, "severity" to "🟢", "plan" to true)
        val (kept, dropped) = ReplyData.clean(data, "chat")
        assertEquals(obj("plan" to true), kept)
        assertTrue(dropped!!.contains("data.score dropped"))
        assertEquals(null, ReplyData.clean(obj("score" to 7), "review_done").first)
        assertEquals(null, ReplyData.clean(obj("score" to 7), null).first) // a digest's "main": none of the turn's events
        for (k in listOf("answer", "town_check", "town_asked", "town_answer", "roleplay_end", "partner_challenge", "partner_answer")) {
            assertEquals(k, data to null, ReplyData.clean(data, k))
        }
        // nothing to drop, or the turn's events unknown (the studio's): as it came
        assertEquals(obj("plan" to true) to null, ReplyData.clean(obj("plan" to true), "chat"))
        assertEquals(data to null, ReplyData.clean(data, null, known = false))
        assertEquals(null to null, ReplyData.clean(null, "chat"))
    }

    @Test fun `a reply's event - its conversation's, else the turn's one event`() {
        val events = listOf(event("review_done", null), event("session_end", "main"))
        assertEquals("session_end", ReplyData.kindOf(events, "main"))
        assertEquals("chat", ReplyData.kindOf(listOf(event("chat", "c-1")), "main"))
        assertNull(ReplyData.kindOf(events, "other"))
        assertEquals(1, ReplyData.persistFirst(events))
        assertEquals(0, ReplyData.persistFirst(listOf(event("chat"))))
    }

    @Test fun `the shadow's reply - score dropped on a chat, kept on an answer, the model told`() = runBlocking {
        val replies = mutableListOf<ShadowReply>()
        val chat = ShadowTools(specs, { _, _ -> ReadResult("{}") }, Guide(), notebook, onReply = { replies += it }, replyKind = { "chat" })
        val o = chat.run(ToolUse("r1", "reply", obj("conversation_id" to "main", "text" to "Bravo!", "data" to obj("score" to 9))))
        assertFalse(o.isError)
        assertTrue(o.content.startsWith("delivered (data.score dropped"))
        assertNull(replies.single().data)
        val answer = ShadowTools(specs, { _, _ -> ReadResult("{}") }, Guide(), notebook, onReply = { replies += it }, replyKind = { "answer" })
        assertEquals("delivered", answer.run(ToolUse("r2", "reply", obj("conversation_id" to "ex-1", "text" to "7/10", "data" to obj("score" to 7)))).content)
        assertEquals("7", replies.last().data!!["score"]!!.jsonPrimitive.content)
    }

    @Test fun `a turn with a session's end replies only once record_session ran`() = runBlocking {
        val replies = mutableListOf<ShadowReply>()
        val tools = ShadowTools(specs, { _, _ -> ReadResult("{}") }, Guide(), notebook, onReply = { replies += it }, replyKind = { "session_end" }, persist = 1)
        val early = tools.run(ToolUse("r1", "reply", obj("conversation_id" to "main", "text" to "Shranjeno.")))
        assertTrue(early.isError)
        assertTrue(early.content.contains("record_session"))
        assertTrue(replies.isEmpty())
        val rec = tools.run(ToolUse("s1", "record_session", obj("session_id" to "session-042", "date" to "2026-10-19", "duration_minutes" to 5)))
        assertFalse(rec.content, rec.isError)
        assertEquals("delivered", tools.run(ToolUse("r2", "reply", obj("conversation_id" to "main", "text" to "Shranjeno."))).content)
        assertEquals(1, replies.size)
    }

    @Test fun `record_session and reply in one message - the reply goes after it`() = runBlocking {
        val replies = mutableListOf<ShadowReply>()
        val tools = ShadowTools(specs, { _, _ -> ReadResult("{}") }, Guide(), notebook, onReply = { replies += it }, replyKind = { "session_end" }, persist = 1)
        val t = ScriptedTransport { _, n ->
            // the reply first in the message: the loop runs it after the rest
            if (n == 1) message(
                "tool_use",
                toolUse("r", "reply", obj("conversation_id" to "main", "text" to "Shranjeno.")),
                toolUse("s", "record_session", obj("session_id" to "session-042", "date" to "2026-10-19", "duration_minutes" to 5)),
            ) else message("end_turn", text("ok"))
        }
        val m = mutableListOf(userText("<event kind=\"session_end\">…</event>"))
        val prefix = Prefix(JsonArray(listOf(ToolSpec("reply", "", obj("type" to "object")).definition())), "core")
        TutorLoop(t, tools).run(prefix, "claude-haiku-5-5", Task.GRADING, m)
        assertEquals(listOf("Shranjeno."), replies.map { it.text })
        // the results in the calls' order
        assertEquals(listOf("r", "s"), m[2].blocks().map { it["tool_use_id"]!!.jsonPrimitive.content })
        assertFalse(m[2].blocks().first().toString().contains("is_error"))
    }
}
