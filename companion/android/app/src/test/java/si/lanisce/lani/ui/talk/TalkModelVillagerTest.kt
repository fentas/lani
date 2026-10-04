package si.lanisce.lani.ui.talk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.BridgeEvent
import si.lanisce.lani.data.Scenario
import si.lanisce.lani.data.json
import si.lanisce.lani.game.villagers.Bonds
import si.lanisce.lani.game.villagers.Memory
import si.lanisce.lani.ui.villagers.FriendGain

/** A talk with a villager: the friendship grows when it ends, and the debrief's memory is kept. */
class TalkModelVillagerTest {
    private val calls = ArrayList<Triple<String, Int, Memory?>>()
    private val sent = ArrayList<String>()
    private val talk = TalkModel(
        CoroutineScope(Dispatchers.Unconfined),
        send = { kind, _, _, _ -> sent += kind },
        fetch = { emptyList() },
        reward = { null },
        befriend = { id, points, memory ->
            calls += Triple(id, points, memory)
            FriendGain(id, "Pastir Luka", "🐑", points, 0, points, memory = memory)
        },
    )

    private fun scenario(id: String) = Scenario(
        id = id, title = "Pogovor", setting = "Na travniku.", role = "Pastir Luka, a shepherd", voice = "male",
        goals = listOf("Pozdravi"), openerSl = "Ej, Jan!", openerEn = "Hey, Jan!",
    )

    private fun data(raw: String): JsonObject = json.parseToJsonElement(raw).jsonObject

    private fun reply(conv: String, raw: String) = BridgeEvent.Reply(conv, "(debrief) Bravo!", null, data(raw))

    @Test fun `ending a talk with a villager befriends them, the debrief's memory stays`() {
        talk.pick(scenario("villager:luka"))
        talk.start()
        talk.say("Živjo, Luka!", emptyList(), typed = true)
        talk.finish()
        assertEquals(listOf(Triple("luka", Bonds.TALK, null as Memory?)), calls)
        assertEquals(Bonds.TALK, talk.friend?.points)
        assertEquals(listOf("roleplay", "roleplay_end"), sent)

        val conv = talk.state!!.conversationId
        talk.onReply(reply(conv, """{"debrief": true, "memory": {"sl": "najin pogovor o burji", "en": "our talk about the burja"}}"""))
        assertEquals(2, calls.size)
        val (id, points, memory) = calls[1]
        assertEquals("luka" to 0, id to points)
        assertEquals("najin pogovor o burji", memory?.sl)
        assertEquals("talk", memory?.kind)
        assertEquals("najin pogovor o burji", talk.friend?.memory?.sl) // shown on the end card
        assertEquals(Bonds.TALK, talk.friend?.points) // still the talk's points

        // Only once per conversation.
        talk.onReply(reply(conv, """{"memory": {"sl": "še enkrat"}}"""))
        assertEquals(2, calls.size)
    }

    @Test fun `a memory arriving after the next talk began still reaches the villager`() {
        talk.pick(scenario("villager:luka"))
        talk.start()
        talk.say("Dober dan!", emptyList(), typed = false)
        talk.finish()
        val first = talk.state!!.conversationId
        talk.again() // a new conversation before the debrief came
        assertNull(talk.friend)
        talk.onReply(reply(first, """{"debrief": true, "memory": {"sl": "tvoj prvi pozdrav", "en": "your first hello"}}"""))
        assertEquals("tvoj prvi pozdrav", calls.last().third?.sl)
        assertNull(talk.friend) // the new conversation's card isn't about it
    }

    @Test fun `other role-plays leave the villagers alone`() {
        talk.pick(scenario("v-pekarni"))
        talk.start()
        talk.say("En kruh, prosim.", emptyList(), typed = true)
        talk.finish()
        talk.onReply(reply(talk.state!!.conversationId, """{"debrief": true, "memory": {"sl": "kruh"}}"""))
        assertTrue(calls.isEmpty())
        assertNull(talk.friend)
    }

    @Test fun `leaving before a word was said is no talk`() {
        talk.pick(scenario("villager:luka"))
        talk.start()
        talk.finish()
        assertTrue(calls.isEmpty())
    }
}
