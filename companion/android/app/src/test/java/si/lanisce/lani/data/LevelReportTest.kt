package si.lanisce.lani.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The new level the treasure brings, for the node (companion/GAME.md, "The treasure map"): `POST /level` for a bridge that
 * persists it; for a node from before it (404), the same as a `session_end` whose report the tutor persists, level
 * included, in the same place in the outbox.
 */
class LevelReportTest {
    private class Memory : OutboxStorage {
        var raw: String? = null
        override fun read() = raw
        override fun write(raw: String) { this.raw = raw }
    }

    private val stations = listOf(
        StationResult("letter", "reading", 4, 5, 3),
        StationResult("dialog", "speaking", 5, 6, 4),
        StationResult("speaking", "speaking", 2, 3, 2),
    )

    @Test fun `the body the bridge persists`() {
        val b = LevelReport.body("A1", "A2", stations)
        assertEquals("A1", b["from"]!!.jsonPrimitive.content)
        assertEquals("A2", b["to"]!!.jsonPrimitive.content)
        assertNull(b["language"])
        val list = b["stations"] as JsonArray
        assertEquals(3, list.size)
        assertEquals("reading", list[0].jsonObject["skill"]!!.jsonPrimitive.content)
        assertEquals(9, b["duration_minutes"]!!.jsonPrimitive.int)
        assertEquals("it", LevelReport.body("A1", "A2", stations, "it")["language"]!!.jsonPrimitive.content)
    }

    @Test fun `the older node's session_end carries the report with the level`() {
        val body = LevelReport.body("A1", "A2", stations)
        val (text, data) = LevelReport.sessionEnd("A1", "A2", stations, "Slovene", body)
        assertTrue(text.contains("now A2"))
        assertTrue(text.contains("update-db.py"))
        val report = data["report"]!!.jsonObject
        assertEquals("A2", report["level"]!!.jsonPrimitive.content)
        assertEquals("/lani-app-treasure", report["command_used"]!!.jsonPrimitive.content)
        // the stations' answers by skill: two of speaking together
        val speaking = report["skill_scores"]!!.jsonObject["speaking"]!!.jsonObject
        assertEquals(9, speaking["exercises"]!!.jsonPrimitive.int)
        assertEquals(7, speaking["correct"]!!.jsonPrimitive.int)
        assertEquals(body, data["level_up"])
    }

    @Test fun `a node from before the route gets the session_end instead, in the same place`() = runBlocking {
        val box = Outbox(Memory()) { 1_000L }
        box.enqueue(Writes.reviews(listOf("vocab_hvala" to 5), 3, id = "a", now = 1_000L))
        box.enqueue(Writes.levelUp("A1", "A2", stations, "Slovene", id = "b", now = 1_000L))
        box.enqueue(Writes.message("chat", "main", "Zdravo", null, id = "c", now = 1_000L))
        val tried = mutableListOf<String>()
        val r = box.flush { e ->
            tried += e.path
            if (e.path == "/level") Delivery.Rejected(404, "not found") else Delivery.Done
        }
        assertEquals(listOf("/reviews", "/level", "/message", "/message"), tried)
        assertEquals(listOf("a", "b", "c"), r.sent.map { it.clientId })
        assertEquals(listOf("b"), r.fellBack.map { it.clientId })
        assertTrue(r.dropped.isEmpty())
        val sent = kotlinx.serialization.json.Json.parseToJsonElement(r.sent[1].body) as JsonObject
        assertEquals("session_end", sent["kind"]!!.jsonPrimitive.content)
        assertEquals("b", sent["client_id"]!!.jsonPrimitive.content)
        assertEquals("A2", sent["data"]!!.jsonObject["report"]!!.jsonObject["level"]!!.jsonPrimitive.content)
    }

    @Test fun `a newer node takes it as it is, and another 4xx is dropped as ever`() = runBlocking {
        val box = Outbox(Memory()) { 1_000L }
        box.enqueue(Writes.levelUp("A1", "A2", stations, "Slovene", id = "b", now = 1_000L))
        val ok = box.flush { Delivery.Done }
        assertEquals("/level", ok.sent.single().path)
        assertTrue(ok.fellBack.isEmpty())
        box.enqueue(Writes.levelUp("A1", "A2", stations, "Slovene", id = "d", now = 1_000L))
        val bad = box.flush { Delivery.Rejected(400, "to: not the next level") }
        assertEquals("d", bad.dropped.single().first.clientId)
        assertTrue(bad.fellBack.isEmpty())
    }

    @Test fun `offline, it waits with its fallback`() = runBlocking {
        val storage = Memory()
        val box = Outbox(storage) { 1_000L }
        box.enqueue(Writes.levelUp("A1", "A2", stations, "Slovene", id = "b", now = 1_000L))
        box.flush { Delivery.Retry("offline") }
        // read back from its storage, as after a restart
        val again = Outbox(storage) { 1_000L }.entries().single()
        assertEquals("/level", again.path)
        assertEquals("/message", again.fallbackPath)
    }
}
