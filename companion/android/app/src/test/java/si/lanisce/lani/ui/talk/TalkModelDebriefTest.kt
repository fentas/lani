package si.lanisce.lani.ui.talk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.BridgeEvent
import si.lanisce.lani.data.Scenario
import si.lanisce.lani.data.Talk
import si.lanisce.lani.data.TalkPhase
import si.lanisce.lani.data.json

/** "Ask your tutor" under the debrief: a chat about the role-play on its own conversation, answered right there. */
class TalkModelDebriefTest {
    private data class Sent(val kind: String, val conversation: String, val text: String, val data: JsonObject)

    private val sent = ArrayList<Sent>()
    private val strays = ArrayList<BridgeEvent.Reply>()
    private var offline = false
    private val talk = TalkModel(
        CoroutineScope(Dispatchers.Unconfined),
        send = { kind, conv, text, data ->
            if (offline) throw java.io.IOException("not connected")
            sent += Sent(kind, conv, text, data)
        },
        fetch = { emptyList() },
        reward = { null },
        stray = { strays += it },
    )

    private val lunch = Scenario(
        id = "nedeljsko-kosilo", title = "Nedeljsko kosilo", setting = "Pri tašči.", role = "Tašča Marija, the mother-in-law",
        goals = listOf("Pozdravi", "Pohvali juho", "Zahvali se"), openerSl = "Dober dan, Jan!", openerEn = "Hello, Jan!",
    )

    private fun reply(conv: String, text: String, raw: String? = null) =
        BridgeEvent.Reply(conv, text, null, raw?.let { json.parseToJsonElement(it).jsonObject })

    /** A role-play said, ended and debriefed; its conversation id. */
    private fun debriefed(): String {
        talk.pick(lunch)
        talk.start()
        talk.say("Dober dan, Marija!", emptyList(), typed = false)
        val conv = talk.state!!.conversationId
        talk.onReply(reply(conv, "Dober dan!", """{"sl": "Dober dan, Jan! Juha je na mizi.", "goals_done": [0]}"""))
        talk.finish()
        talk.onReply(reply(conv, "Bravo! Vadi rodilnik: *brez juhe*.", """{"debrief": true}"""))
        assertEquals(TalkPhase.DONE, talk.state!!.phase)
        return conv
    }

    @Test fun `a question goes as a chat about the role-play, on the debrief's own conversation`() {
        val conv = debriefed()
        sent.clear()
        talk.ask("  Zakaj brez juhe?  ")
        val s = sent.single()
        assertEquals("chat", s.kind)
        assertEquals("$conv-debrief", s.conversation)
        assertEquals("Zakaj brez juhe?", s.text)
        val about = s.data["about_roleplay"]!!.jsonObject
        assertEquals("nedeljsko-kosilo", about["scenario_id"]!!.jsonPrimitive.content)
        assertEquals("Nedeljsko kosilo", about["title"]!!.jsonPrimitive.content)
        assertEquals(conv, about["conversation_id"]!!.jsonPrimitive.content)
        assertTrue(about["debrief"]!!.jsonPrimitive.content.startsWith("Bravo!"))
        assertEquals(listOf(true), talk.thread.map { it.fromMe })
        assertTrue(talk.threadWaiting)
        assertTrue(s.conversation.length <= 64) // the bridge's conversation ids
    }

    @Test fun `the answer comes into the thread by its conversation id - the debrief stays`() {
        val conv = debriefed()
        talk.ask("Zakaj brez juhe?")
        talk.onReply(reply(Talk.debriefThread(conv), "Ker *brez* zahteva **rodilnik**."))
        assertEquals(listOf("Zakaj brez juhe?", "Ker *brez* zahteva **rodilnik**."), talk.thread.map { it.text })
        assertEquals(listOf(true, false), talk.thread.map { it.fromMe })
        assertFalse(talk.threadWaiting)
        assertEquals("Bravo! Vadi rodilnik: *brez juhe*.", talk.state!!.debrief)
        assertTrue(strays.isEmpty())

        talk.ask("Še en primer?")
        talk.onReply(reply(Talk.debriefThread(conv), "Brez kruha."))
        assertEquals(4, talk.thread.size)
    }

    @Test fun `no questions while the scene runs`() {
        talk.pick(lunch)
        talk.start()
        talk.ask("Zakaj?")
        assertTrue(sent.isEmpty())
        assertTrue(talk.thread.isEmpty())
    }

    @Test fun `an answer for a debrief left behind goes to the chat`() {
        val conv = debriefed()
        talk.ask("Zakaj brez juhe?")
        talk.abandon()
        assertTrue(talk.thread.isEmpty())
        talk.onReply(reply(Talk.debriefThread(conv), "Ker brez zahteva rodilnik."))
        assertEquals(listOf("Ker brez zahteva rodilnik."), strays.map { it.text })
        assertNull(talk.state)
    }

    @Test fun `another role-play's thread doesn't mix in`() {
        val first = debriefed()
        talk.ask("Zakaj brez juhe?")
        val second = debriefed() // the same scene again
        assertTrue(talk.thread.isEmpty())
        talk.ask("In zdaj?")
        talk.onReply(reply(Talk.debriefThread(first), "Staro."))
        talk.onReply(reply(Talk.debriefThread(second), "Novo."))
        assertEquals(listOf("In zdaj?", "Novo."), talk.thread.map { it.text })
        assertEquals(listOf("Staro."), strays.map { it.text })
    }

    @Test fun `a question that didn't go out can be sent again`() {
        debriefed()
        offline = true
        talk.ask("Zakaj?")
        assertEquals("not connected", talk.threadError)
        assertFalse(talk.threadWaiting)
        offline = false
        sent.clear()
        talk.retryThread()
        assertEquals("Zakaj?", sent.single().text)
        assertNull(talk.threadError)
        assertTrue(talk.threadWaiting)
        assertEquals(1, talk.thread.size) // not twice
    }

    @Test fun `debrief threads are told apart from role-play conversations`() {
        assertTrue(Talk.isDebriefThread(Talk.debriefThread("rp-1234")))
        assertFalse(Talk.isDebriefThread("rp-1234"))
        assertFalse(Talk.isDebriefThread("main"))
    }
}
