package si.lanisce.lani.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OutboxTest {
    private class Memory : OutboxStorage {
        var raw: String? = null
        override fun read() = raw
        override fun write(raw: String) { this.raw = raw }
    }

    private var now = 1_000_000L
    private val storage = Memory()
    private val box = Outbox(storage) { now }

    private fun review(id: String) = Writes.reviews(listOf("vocab_hvala" to 5), 3, id = id, now = now)
    private fun chat(id: String, text: String) = Writes.message("chat", "main", text, null, id = id, now = now)

    @Test fun `entries keep their order`() {
        box.enqueue(review("a"))
        box.enqueue(chat("b", "Zdravo"))
        box.enqueue(Writes.learnPack("kitchen", listOf("kruh" to 4), 2, id = "c", now = now))
        assertEquals(listOf("a", "b", "c"), box.entries().map { it.clientId })
        assertEquals(listOf("/reviews", "/message", "/packs/kitchen/learn"), box.entries().map { it.path })
    }

    @Test fun `flush sends in order and stops at the first write that can't go`() = runBlocking {
        listOf("a", "b", "c").forEach { box.enqueue(review(it)) }
        val tried = mutableListOf<String>()
        val r = box.flush { e -> tried += e.clientId; if (e.clientId == "b") Delivery.Retry("offline") else Delivery.Done }
        assertEquals(listOf("a", "b"), tried) // c must not overtake b
        assertEquals(listOf("a"), r.sent.map { it.clientId })
        assertEquals(2, r.remaining)
        assertEquals(listOf("b", "c"), box.entries().map { it.clientId })
        assertEquals(1, box.entries().first().attempts)
        assertEquals(0, box.entries().first().serverErrors)
    }

    @Test fun `survives a restart through the file`() {
        val dir = Files.createTempDirectory("outbox").toFile()
        try {
            val file = File(dir, "outbox.json")
            Outbox(FileStorage(file)).apply {
                enqueue(review("a"))
                enqueue(chat("b", "Kako se reče »fork«?"))
            }
            val reopened = Outbox(FileStorage(file))
            assertEquals(listOf("a", "b"), reopened.entries().map { it.clientId })
            assertEquals(chat("b", "Kako se reče »fork«?"), reopened.entries()[1])
            assertFalse(File(dir, "outbox.json.tmp").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun `a corrupt file reads as empty instead of crashing`() {
        storage.raw = "{not json"
        assertTrue(box.entries().isEmpty())
        box.enqueue(review("a"))
        assertEquals(1, box.size)
    }

    @Test fun `client id is in the body and enqueuing it twice keeps one`() {
        val e = review("abc123")
        assertEquals("abc123", json.parseToJsonElement(e.body).jsonObject["client_id"]!!.jsonPrimitive.content)
        box.enqueue(e)
        box.enqueue(e)
        assertEquals(1, box.size)
    }

    @Test fun `new ids are unique`() {
        val ids = List(500) { Outbox.newId() }.toSet()
        assertEquals(500, ids.size)
        assertTrue(ids.all { Regex("^[0-9a-f]{32}$").matches(it) })
        assertTrue(Writes.reviews(listOf("x" to 3), 1).clientId != Writes.reviews(listOf("x" to 3), 1).clientId)
    }

    @Test fun `message bodies carry kind, conversation and data`() {
        val e = Writes.message("session_end", "main", "done", buildJsonObject { put("module_id", "m1") }, id = "z1")
        val o = json.parseToJsonElement(e.body).jsonObject
        assertEquals("session_end", o["kind"]!!.jsonPrimitive.content)
        assertEquals("main", o["conversation_id"]!!.jsonPrimitive.content)
        assertEquals("m1", o["data"]!!.jsonObject["module_id"]!!.jsonPrimitive.content)
        assertEquals("POST", e.method)
    }

    @Test fun `a permanent 4xx drops the write, reports it and lets the rest through`() = runBlocking {
        box.enqueue(review("a"))
        box.enqueue(Writes.learnPack("gone", listOf("kruh" to 4), 2, id = "b", now = now))
        box.enqueue(chat("c", "hi"))
        val r = box.flush { e -> if (e.clientId == "b") Outbox.classify(404, """{"error":"not found"}""") else Delivery.Done }
        assertEquals(listOf("a", "c"), r.sent.map { it.clientId })
        assertEquals(1, r.dropped.size)
        assertEquals("b", r.dropped[0].first.clientId)
        assertEquals("HTTP 404: not found", r.dropped[0].second)
        assertEquals(0, r.remaining)
        assertTrue(box.entries().isEmpty())
    }

    @Test fun `status codes`() {
        assertEquals(Delivery.Done, Outbox.classify(200, "{}"))
        assertEquals(Delivery.Rejected(400, "bad quality"), Outbox.classify(400, """{"error":"bad quality"}"""))
        assertEquals(Delivery.Rejected(413, "too big"), Outbox.classify(413, "too big"))
        assertTrue(Outbox.classify(401, "") is Delivery.Retry) // token trouble: keep until fixed
        assertTrue(Outbox.classify(429, "") is Delivery.Retry)
        val s = Outbox.classify(500, """{"error":"update-db failed"}""")
        assertTrue(s is Delivery.Retry && s.server)
    }

    @Test fun `server errors give up after a few tries, offline never does`() = runBlocking {
        box.enqueue(review("a"))
        repeat(50) { box.flush { Delivery.Retry("offline") } }
        assertEquals(1, box.size)
        assertEquals(50, box.entries()[0].attempts)
        var last: FlushReport? = null
        repeat(Outbox.MAX_SERVER_ERRORS) { last = box.flush { Delivery.Retry("HTTP 500", server = true) } }
        assertEquals(0, box.size)
        assertEquals("a", last!!.dropped.single().first.clientId)
    }

    @Test fun `backoff grows and respects force`() = runBlocking {
        assertEquals(5_000L, Outbox.backoff(1))
        assertEquals(10_000L, Outbox.backoff(2))
        assertEquals(40_000L, Outbox.backoff(4))
        assertEquals(15 * 60_000L, Outbox.backoff(30))

        box.enqueue(review("a"))
        box.flush { Delivery.Retry("offline") }
        assertEquals(5_000L, box.nextRetryIn())
        var calls = 0
        box.flush(force = false) { calls++; Delivery.Done }
        assertEquals(0, calls) // not due yet
        now += 5_000
        assertEquals(0L, box.nextRetryIn())
        box.flush(force = false) { calls++; Delivery.Done }
        assertEquals(1, calls)
        assertEquals(null, box.nextRetryIn())
    }

    @Test fun `a new write behind one in backoff waits for the head, not zero`() = runBlocking {
        box.enqueue(review("a"))
        box.flush { Delivery.Retry("offline") } // a: backoff 5 s
        box.enqueue(review("b")) // never tried: nextAttemptAt = 0
        // The retry loop sleeps nextRetryIn() and then flushes without force. With 0 it would spin,
        // because the flush stops at a (not due) and b stays queued.
        assertEquals(5_000L, box.nextRetryIn())
        now += 5_000
        assertEquals(0L, box.nextRetryIn())
        val r = box.flush(force = false) { Delivery.Done }
        assertEquals(listOf("a", "b"), r.sent.map { it.clientId })
    }

    @Test fun `a crash inside send counts as retry`() = runBlocking {
        box.enqueue(review("a"))
        val r = box.flush { throw IllegalStateException("boom") }
        assertEquals(1, r.remaining)
        assertTrue(r.dropped.isEmpty())
    }
}
