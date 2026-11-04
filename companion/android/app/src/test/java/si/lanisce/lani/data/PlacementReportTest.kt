package si.lanisce.lani.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.LevelPlacement
import kotlin.random.Random

/**
 * The placement test's result for the node (data/PlacementReport; the bridge's src/placement.ts reads it): the body of
 * `POST /placement` as the bridge's schema wants it, and an older node's `session_end`, whose report sets the level only
 * when it rises.
 */
class PlacementReportTest {
    private val course = checkNotNull(Courses.bundled("sl"))

    /** A test by a learner who knows up to part [truth], to its end. */
    private fun played(truth: Int, version: LevelPlacement.Version = LevelPlacement.Version.ADULT): LevelPlacement.Test {
        var t = LevelPlacement.start(course, null, "A1", version)
        val r = Random(3)
        while (true) {
            val part = LevelPlacement.next(t) ?: return t
            val p = LevelPlacement.probe(course, t, part, r)
            t = LevelPlacement.record(t, p, p.items.flatMap { List(it.items) { if (part <= truth) Verdict.CORRECT else Verdict.WRONG } }, 120)
        }
    }

    @Test fun `the body says where it began, what it found, each probe, and whether it was taken`() {
        val t = played(5)
        val r = LevelPlacement.result(t, null)
        val b = PlacementReport.body(t, r, "a1", applied = true, units = r.units.size)
        assertEquals("A1", b["from"]!!.jsonPrimitive.content)
        assertEquals("B1", b["level"]!!.jsonPrimitive.content)
        assertTrue(b["applied"]!!.jsonPrimitive.boolean)
        assertEquals(r.units.size, b["units"]!!.jsonPrimitive.int)
        val start = b["start"]!!.jsonObject
        assertEquals("B1", start["level"]!!.jsonPrimitive.content)
        assertEquals(1, start["part"]!!.jsonPrimitive.int)
        val probes = PlacementReport.probes(b)
        assertEquals(t.probes.size, probes.size)
        for (p in probes) {
            assertTrue(p["right"]!!.jsonPrimitive.int + p["almost"]!!.jsonPrimitive.int <= p["of"]!!.jsonPrimitive.int)
            assertTrue(p["part"]!!.jsonPrimitive.int <= p["parts"]!!.jsonPrimitive.int)
        }
        assertNull(b["child"])
        assertTrue(b["duration_minutes"]!!.jsonPrimitive.int >= 1)
        // kept: no units
        val kept = PlacementReport.body(t, r, "A1", applied = false, units = 12)
        assertEquals(0, kept["units"]!!.jsonPrimitive.int)
        assertFalse(kept["applied"]!!.jsonPrimitive.boolean)
    }

    @Test fun `a child's says so, a test with nothing answered isn't sent`() {
        val t = played(2, LevelPlacement.Version.CHILD)
        val b = PlacementReport.body(t, LevelPlacement.result(t, null), "A1", applied = false, units = 0)
        assertTrue(b["child"]!!.jsonPrimitive.boolean)
        val none = LevelPlacement.start(course, null, "A1", LevelPlacement.Version.ADULT)
        assertFalse(PlacementReport.worth(none))
        assertTrue(PlacementReport.worth(t))
    }

    @Test fun `an older node's session_end sets the level only when it rises`() {
        val t = played(5)
        val r = LevelPlacement.result(t, null)
        val b = PlacementReport.body(t, r, "A1", applied = true, units = 32)
        val (text, data) = PlacementReport.sessionEnd(t, r, "A1", applied = true, units = 32, raise = true, language = "Slovene", body = b)
        val report = data["report"] as JsonObject
        assertEquals("B1", report["level"]!!.jsonPrimitive.content)
        assertEquals("/lani-app-placement", report["command_used"]!!.jsonPrimitive.content)
        assertTrue(text.contains("sets current_level to B1"))
        assertTrue(text.contains("A2 3/3 passed"))
        val (kept, keptData) = PlacementReport.sessionEnd(t, r, "B2", applied = false, units = 0, raise = false, language = "Slovene", body = b)
        assertNull((keptData["report"] as JsonObject)["level"])
        assertTrue(kept.contains("their level stays B2") && kept.contains("kept their place"))
    }

    @Test fun `a level sent again says what was taken, with one probe, and raises it for an older node`() {
        val p = si.lanisce.lani.game.Placed(day = "2026-11-01", from = "A1", level = "A2", start = "A2", part = 2, parts = 3, units = 14, right = 9, almost = 1, of = 12, minutes = 7, child = true)
        val body = PlacementReport.again(p)
        assertEquals("A1", body["from"]?.jsonPrimitive?.content)
        assertEquals("A2", body["level"]?.jsonPrimitive?.content)
        assertEquals(true, body["applied"]?.jsonPrimitive?.boolean)
        assertEquals(14, body["units"]?.jsonPrimitive?.int)
        assertEquals(true, body["child"]?.jsonPrimitive?.boolean)
        val probe = PlacementReport.probes(body).single()
        assertEquals(listOf("A2", "2", "3", "9", "1", "12", "true"), listOf("level", "part", "parts", "right", "almost", "of", "passed").map { probe[it]?.jsonPrimitive?.content })
        val (text, data) = PlacementReport.againSessionEnd(p, "Italian", body)
        assertTrue(text, "current_level to A2" in text)
        assertEquals("A2", data["report"]?.jsonObject?.get("level")?.jsonPrimitive?.content)
        // past the course's last part: no start, and still a probe the node takes
        val past = PlacementReport.again(p.copy(start = null, part = 0, parts = 0, of = 0, minutes = 0))
        assertEquals(null, past["start"])
        assertEquals(listOf("A2", "1", "1", "1"), PlacementReport.probes(past).single().let { q -> listOf("level", "part", "parts", "of").map { q[it]?.jsonPrimitive?.content } })
        assertEquals(1, past["duration_minutes"]?.jsonPrimitive?.int)
    }
}
