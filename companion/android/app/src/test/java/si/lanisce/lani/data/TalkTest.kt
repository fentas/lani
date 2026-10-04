package si.lanisce.lani.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import java.io.File

class TalkTest {
    private val raw = """
        {
          "schema": "lani.scenario/v0", "id": "v-pekarni", "title": "V pekarni · At the bakery", "emoji": "🥖",
          "level": "A1", "setting": "Early morning in a bakery.", "role": "Prodajalka Nina, the baker. Says vi.",
          "goals": ["Greet the baker", "Ask for bread", "Say goodbye"],
          "opener_sl": "Dobro jutro! Izvolite, kaj bo?", "opener_en": "Good morning! What can I get you?",
          "vocabulary_hints": [{ "sl": "En kruh, prosim.", "en": "A loaf of bread, please." }],
          "source": "curated", "future_field": true
        }
    """.trimIndent()
    private val scenario = parseScenario(raw)

    private fun reply(data: String?, text: String = "x") =
        RoleplayReply.parse(text, data?.let { json.parseToJsonElement(it).jsonObject })

    @Test fun `parses a scenario and names the character`() {
        assertEquals("v-pekarni", scenario.id)
        assertEquals(3, scenario.goals.size)
        assertEquals("Dobro jutro! Izvolite, kaj bo?", scenario.openerSl)
        assertEquals("En kruh, prosim.", scenario.vocabularyHints.single().sl)
        assertEquals("Prodajalka Nina", scenario.character)
        assertEquals("Dr. Kovač", scenario.copy(role = "Dr. Kovač, a calm family doctor.").character)
    }

    @Test fun `parses a list with defaults`() {
        val list = parseScenarios("""[{"id":"x","title":"T","setting":"s","role":"R","goals":["a","b","c"],"opener_sl":"Živjo!","opener_en":"Hi!"}]""")
        assertEquals("🗣️", list.single().emoji)
        assertEquals("A1", list.single().level)
        assertTrue(list.single().vocabularyHints.isEmpty())
    }

    @Test fun `every curated scenario parses`() {
        val dir = listOf(File("../../scenarios"), File("../scenarios"), File("companion/scenarios")).first { it.isDirectory }
        val files = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        assertEquals(6, files.size)
        for (f in files) {
            val s = parseScenario(f.readText())
            assertEquals(f.nameWithoutExtension, s.id)
            assertTrue(s.goals.size in 3..5)
            assertFalse(s.character.isBlank())
        }
    }

    @Test fun `parses an in-character reply`() {
        val r = reply("""{"sl":"Izvolite.","en":"Here you are.","correction":"**En kruh**","goals_done":[0,"1"],"end":true}""")
        assertEquals("Izvolite.", r.sl)
        assertEquals("Here you are.", r.en)
        assertEquals("**En kruh**", r.correction)
        assertEquals(listOf(0, 1), r.goalsDone)
        assertTrue(r.end)
        assertFalse(r.hint)
        assertTrue(r.inCharacter)
    }

    @Test fun `a reply without data falls back to its text`() {
        val r = reply(null, text = " Dober dan! ")
        assertEquals("Dober dan!", r.sl)
        assertNull(r.goalsDone)
        assertNull(r.correction)
        assertFalse(r.inCharacter)
        assertNull(reply("""{"sl":"x","correction":"  "}""").correction)
    }

    @Test fun `a conversation runs from opener to debrief`() {
        var st = Talk.start(scenario, now = 0, conversationId = "rp-1")
        assertEquals(1, st.lines.size)
        assertFalse(st.lines.single().fromMe)
        assertEquals(0, st.turn)

        st = Talk.said(st, " Dober dan. ", typed = false, now = 1_000)
        assertTrue(st.waiting)
        assertEquals("Dober dan.", st.lines.last().sl)
        val data = Talk.turnData(st, "Dober dan.", listOf("dober dan", "Dober dan.", " "))
        assertEquals("1", data["turn"]!!.jsonPrimitive.content)
        assertEquals(listOf("dober dan"), (data["alternatives"] as JsonArray).map { it.jsonPrimitive.content })

        st = Talk.replied(st, reply("""{"sl":"Dobro jutro! Kaj bo?","en":"What will it be?","goals_done":[0]}"""))
        assertFalse(st.waiting)
        assertEquals(setOf(0), st.goalsDone)
        assertEquals(3, st.lines.size)

        st = Talk.said(st, "Kruh prosim", typed = true, now = 2_000)
        st = Talk.replied(st, reply("""{"sl":"Izvolite.","correction":"Kruh, **prosim**.","goals_done":[1, 7],"end":true}"""))
        assertEquals("Kruh, **prosim**.", st.lines[3].correction) // on Jan's line, not the character's
        assertNull(st.lines[4].correction)
        assertEquals(setOf(0, 1), st.goalsDone) // out-of-range goal ignored
        assertTrue(st.sceneOver)
        assertEquals(listOf(Verdict.CORRECT, Verdict.ALMOST), Talk.verdicts(st))

        // Goals never untick, even if the tutor forgets one later.
        assertEquals(setOf(0, 1), Talk.replied(Talk.said(st, "Hvala", false, 3_000), reply("""{"sl":"Prosim.","goals_done":[]}""")).goalsDone)

        st = Talk.ending(st)
        val t = Talk.transcript(st, now = 5 * 60_000)
        assertEquals(5, t["duration_minutes"]!!.jsonPrimitive.content.toInt())
        assertEquals(2, t["turns"]!!.jsonPrimitive.content.toInt())
        val lines = (t["transcript"] as JsonArray).map { it as JsonObject }
        assertEquals(listOf("character", "jan", "character", "jan", "character"), lines.map { it["who"]!!.jsonPrimitive.content })
        assertEquals("true", lines[3]["typed"]!!.jsonPrimitive.content)

        // A late in-character line after the end is dropped; the debrief finishes the scene.
        assertSame(st, Talk.replied(st, reply("""{"sl":"Še kaj?"}""")))
        assertFalse(reply("""{"sl":"Bravo!","debrief":true}""").inCharacter)
        st = Talk.replied(st, reply(null, text = "**Bravo!** Practise *prosim*."))
        assertEquals(TalkPhase.DONE, st.phase)
        assertEquals("**Bravo!** Practise *prosim*.", st.debrief)
        assertSame(st, Talk.replied(st, reply(null, text = "again")))
    }

    @Test fun `hints don't move the scene on`() {
        var st = Talk.start(scenario, now = 0)
        assertTrue(st.conversationId.startsWith("rp-"))
        val data = Talk.turnData(st, heard = null)
        assertEquals("true", data["hint"]!!.jsonPrimitive.content)
        assertEquals("1", data["turn"]!!.jsonPrimitive.content)
        assertNull(data["heard"])

        st = Talk.askedHint(st)
        assertTrue(st.hintLoading)
        st = Talk.replied(st, reply("""{"hint":true,"sl":"En kruh, prosim.","en":"A loaf, please."}"""))
        assertEquals(TalkHint("En kruh, prosim.", "A loaf, please."), st.hint)
        assertFalse(st.hintLoading)
        assertEquals(1, st.lines.size)
        assertEquals(1, st.hintsUsed)

        // A hint reply that forgot hint:true still counts as the hint.
        st = Talk.replied(Talk.askedHint(st), reply("""{"sl":"Dober dan."}"""))
        assertEquals("Dober dan.", st.hint?.sl)
        assertEquals(1, st.lines.size)

        st = Talk.said(st, "En kruh, prosim.", typed = false, now = 1)
        assertNull(st.hint)
    }
}
