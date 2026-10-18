package si.lanisce.lani.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * A chat message with a photo in the outbox (README "Tutor chat", "Photos"): one entry, the photo PUT first under the
 * message's client id, then the message naming it; offline it waits with its photo and goes when the node is back, a
 * retry sends both again harmlessly; an older node (404) and a photo deleted before it went get the text alone.
 */
class OutboxPhotoTest {
    private val dir: File = Files.createTempDirectory("outbox-photo").toFile()
    private val photoFile = File(dir, "p.jpg").apply { writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)) }
    private val photo = ChatPhoto(id = "0123456789abcdef0123456789abcdef", width = 1568, height = 1176, bytes = 6)
    private val about = buildJsonObject { put("about_exercise", buildJsonObject { put("type", "choice") }) }
    private val entry = Writes.chatPhoto("Kaj piše tukaj?", about, photo, photoFile, now = 1_000L)

    /** What a node got: each request's method, path, and body (text) or bytes. */
    private data class Got(val method: String, val path: String, val body: String? = null, val bytes: ByteArray? = null)

    private class Node(var putCode: Int = 200, var sendCode: Int = 200, var offline: Boolean = false) {
        val got = mutableListOf<Got>()
        suspend fun send(method: String, path: String, body: String): Pair<Int, String> {
            if (offline) throw java.io.IOException("offline")
            got += Got(method, path, body = body)
            return sendCode to """{"msg_id":"m1"}"""
        }
        suspend fun put(path: String, bytes: ByteArray, type: String): Pair<Int, String> {
            if (offline) throw java.io.IOException("offline")
            got += Got("PUT", path, body = type, bytes = bytes)
            return putCode to if (putCode == 200) """{"id":"x"}""" else """{"error":"no"}"""
        }
        suspend fun deliver(e: OutboxEntry): Delivery = try {
            Outbox.deliverWith(e, ::send, ::put)
        } catch (x: java.io.IOException) {
            Delivery.Retry(x.message ?: "offline")
        }
    }

    private fun bodyOf(g: Got): JsonObject = json.parseToJsonElement(g.body!!).jsonObject

    @Test fun `one entry - the message's client id is the photo's, its body names the photo, the upload its route`() {
        assertEquals(photo.id, entry.clientId)
        assertEquals("/message", entry.path)
        val body = json.parseToJsonElement(entry.body).jsonObject
        assertEquals(photo.id, body["image"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals(photo.id, body["client_id"]!!.jsonPrimitive.content)
        assertEquals("Kaj piše tukaj?", body["text"]!!.jsonPrimitive.content)
        assertEquals("choice", body["data"]!!.jsonObject["about_exercise"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val up = entry.upload!!
        assertEquals("/chat/images/${photo.id}", up.path)
        assertEquals(photoFile.absolutePath, up.file)
        assertEquals("image/jpeg", up.type)
        // the body for a node without photos: no image, a line for the tutor, the same client id
        val without = json.parseToJsonElement(up.withoutBody!!).jsonObject
        assertNull(without["image"])
        assertTrue(without["text"]!!.jsonPrimitive.content.endsWith(Writes.PHOTO_NOT_SENT))
        assertEquals(photo.id, without["client_id"]!!.jsonPrimitive.content)
    }

    @Test fun `delivered - the photo first, its bytes as they are on the phone, then the message`() = runBlocking {
        val node = Node()
        assertEquals(Delivery.Done, node.deliver(entry))
        assertEquals(listOf("PUT" to "/chat/images/${photo.id}", "POST" to "/message"), node.got.map { it.method to it.path })
        assertArrayEquals(photoFile.readBytes(), node.got[0].bytes)
        assertEquals("image/jpeg", node.got[0].body)
        assertEquals(photo.id, bodyOf(node.got[1])["image"]!!.jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test fun `offline it waits with its photo, survives a restart, and goes once the node is back`() = runBlocking {
        val file = File(dir, "outbox.json")
        val node = Node(offline = true)
        Outbox(FileStorage(file)).apply {
            enqueue(entry)
            val r = flush { node.deliver(it) }
            assertEquals(1, r.remaining)
        }
        val reopened = Outbox(FileStorage(file))
        assertEquals(entry.upload, reopened.entries().single().upload)
        node.offline = false
        val r = reopened.flush { node.deliver(it) }
        assertEquals(listOf(photo.id), r.sent.map { it.clientId })
        assertEquals(listOf("PUT", "POST"), node.got.map { it.method })
        assertEquals(0, reopened.size)
    }

    @Test fun `a retry after the message failed sends the photo again, under the same id (the node keeps it once)`() = runBlocking {
        val node = Node(sendCode = 503)
        assertTrue(node.deliver(entry) is Delivery.Retry)
        node.sendCode = 200
        assertEquals(Delivery.Done, node.deliver(entry))
        val puts = node.got.filter { it.method == "PUT" }
        assertEquals(2, puts.size)
        assertEquals(setOf("/chat/images/${photo.id}"), puts.map { it.path }.toSet())
        assertEquals(setOf(photo.id), node.got.filter { it.method == "POST" }.map { bodyOf(it)["client_id"]!!.jsonPrimitive.content }.toSet())
    }

    @Test fun `a node from before photos (404) gets the text alone, with the line for the tutor`() = runBlocking {
        val node = Node(putCode = 404)
        assertEquals(Delivery.Done, node.deliver(entry))
        val msg = bodyOf(node.got.last())
        assertNull(msg["image"])
        assertTrue(msg["text"]!!.jsonPrimitive.content.startsWith("Kaj piše tukaj?"))
        assertTrue(msg["text"]!!.jsonPrimitive.content.contains("📷"))
    }

    @Test fun `a photo deleted before it went - the text alone, nothing uploaded`() = runBlocking {
        photoFile.delete()
        val node = Node()
        assertEquals(Delivery.Done, node.deliver(entry))
        assertEquals(listOf("POST"), node.got.map { it.method })
        assertNull(bodyOf(node.got.single())["image"])
    }

    @Test fun `a photo the node refuses (413, 415) is the write's answer, and the message doesn't go without it`() = runBlocking {
        for (code in listOf(413, 415)) {
            val node = Node(putCode = code)
            val r = node.deliver(entry)
            assertTrue("$code: $r", r is Delivery.Rejected && r.code == code)
            assertFalse(node.got.any { it.method == "POST" })
        }
        val busy = Node(putCode = 500)
        assertTrue(busy.deliver(entry) is Delivery.Retry)
        assertFalse(busy.got.any { it.method == "POST" })
    }

    @Test fun `a photo deleted on the phone goes on the node too - DELETE through the outbox`() {
        val e = Writes.deletePhoto(photo.id, clientId = "del-0001", now = 5L)
        assertEquals("DELETE", e.method)
        assertEquals("/chat/images/${photo.id}", e.path)
        assertNull(e.upload)
        assertEquals("del-0001", json.parseToJsonElement(e.body).jsonObject["client_id"]!!.jsonPrimitive.content)
    }

    @Test fun `a message without a photo is as it was`() {
        val m = Writes.message("chat", "main", "Zdravo", null, id = "abcdefgh", now = 1L)
        assertNull(m.upload)
        assertNull(json.parseToJsonElement(m.body).jsonObject["image"])
    }
}
