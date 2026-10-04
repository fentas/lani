package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ParsingTest {
    // The canonical example from the lani-studio spec reference, so app and docs can't drift.
    private val specExample: String = File("../../../.claude/skills/lani-studio/reference/module-spec.md")
        .readText().let { Regex("```json\\n([\\s\\S]*?)```").findAll(it).last().groupValues[1] }

    @Test fun `decodes the reference module into typed exercises`() {
        val m = parseModule(specExample.replaceFirst("{", "{\"version\": 3,"))
        assertEquals("clitic-se-basics", m.id)
        assertEquals(3, m.version)
        assertTrue(m.exercises[0] is Exercise.Choice)
        assertTrue(m.exercises[1] is Exercise.Reorder)
        val t = m.exercises[2] as Exercise.Translate
        assertEquals("match", t.grade)
    }

    @Test fun `unknown exercise types degrade instead of failing the module`() {
        val m = parseModule(
            """{"id":"x","version":1,"title":"t","level":"A1","exercises":[
              {"type":"hologram","prompt":"?"},
              {"type":"multi","prompt":"p","options":["a","b","c"],"answers":[0,2]},
              {"type":"scenario","scene":"🥖","prompt":"p","line":"Kaj bo?","accept":["Kruh, prosim."]}]}""",
        )
        assertEquals(Exercise.Unsupported("hologram"), m.exercises[0])
        assertEquals(listOf(0, 2), (m.exercises[1] as Exercise.Multi).answers)
        assertEquals("Kaj bo?", (m.exercises[2] as Exercise.Scenario).line)
    }

    @Test fun `parses dashboard from read-db output`() {
        val raw = """
        {"databases": {
          "learner_profile": {"learner": {"name": "Jan", "current_level": "A1", "target_level": "B2"},
                              "current_streak_days": 2, "achievements": [{}, {}]},
          "progress_db": {"overall_stats": {"total_correct": 15, "total_sessions": 2}},
          "mistakes_db": {"error_patterns": {"clitic_placement_se": {"frequency": 2, "consecutive_incorrect": 2}}},
          "spaced_repetition": {"items": {
            "vocab_hvala": {"type": "vocabulary", "category": "politeness", "content": "hvala", "answer": "thank you"},
            "vocab_ampak": {"type": "vocabulary", "category": " ", "content": "ampak", "answer": "but"},
            "clitic_placement_se": {"type": "error_pattern"},
            "spelling_c_vs_z": {"type": "error_pattern", "content": "iz Gorize", "answer": "iz Gorice"}}}},
         "computed": {"due_review_items": ["vocab_hvala", "clitic_placement_se", "spelling_c_vs_z"]}}
        """
        val d = Dashboard.parse(raw)
        assertEquals("Jan", d.name)
        assertEquals(2, d.streak)
        assertEquals(15 * 10 + 2 * 25, d.xp)
        // Error patterns store the learner's wrong answer as content: they must never become cards.
        assertEquals(listOf(ReviewCard("vocab_hvala", "hvala", "thank you", category = "politeness")), d.dueCards)
        assertEquals(listOf("vocab_hvala", "vocab_ampak"), d.pool.map { it.id })
        assertEquals(listOf("politeness", null), d.pool.map { it.category }) // a blank category is no category
        assertEquals(listOf("clitic_placement_se"), d.weakPatterns)
    }

    @Test fun `a reply marked as plan is recognised`() {
        val r = BridgeEvent.parse("""{"type":"reply","conversation_id":"main","text":"Dobro jutro!","data":{"plan":true},"at":1}""")
        assertEquals(true, (r as BridgeEvent.Reply).plan)
        val plain = BridgeEvent.parse("""{"type":"reply","conversation_id":"main","text":"hi","at":1}""") as BridgeEvent.Reply
        assertEquals(false, plain.plan)
    }

    @Test fun `parses bridge events`() {
        val r = BridgeEvent.parse("""{"type":"reply","conversation_id":"ex1","text":"ok","data":{"score":8},"at":1}""")
        assertEquals(8, (r as BridgeEvent.Reply).score)
        assertEquals("ex1", r.conversationId)
        assertEquals("8", r.data?.get("score").toString())
        val p = BridgeEvent.parse("""{"type":"permission_request","request_id":"abcde","tool_name":"Bash","description":"d","input_preview":"ls","at":1727000000000}""")
        assertEquals(1727000000000L, (p as BridgeEvent.PermissionRequest).at)
    }
}
