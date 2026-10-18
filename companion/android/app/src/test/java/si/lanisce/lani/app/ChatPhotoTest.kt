package si.lanisce.lani.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import si.lanisce.lani.data.ChatPhoto
import si.lanisce.lani.data.ChatPhotoStore
import si.lanisce.lani.data.Jpeg
import si.lanisce.lani.data.Outbox
import si.lanisce.lani.data.Prefs
import si.lanisce.lani.data.TestJpegs
import si.lanisce.lani.data.json
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.time.ZoneOffset

/**
 * The chat's photos on the phone (README "Tutor chat", "Photos"): one taken or picked is made ready (no EXIF) and shown
 * above the input, ✕ lets it go; sent (alone or with text) it is the message's and waits in the outbox with it, offline
 * too; deleted, it leaves the phone and a DELETE goes to the node; a photo no message keeps goes after a day.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatPhotoTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val dir = Files.createTempDirectory("chat-photo").toFile()
    private var now = 1_760_000_000_000L
    private val photos = ChatPhotoStore(File(dir, "chat-photos"))
    private val chat: ChatController by lazy {
        val notices = Notices()
        // no node: every write waits in the outbox (Outbox(context): the app's files/outbox.json)
        ChatController(
            scope, Prefs(context), SyncController(context, scope, { null }, notices) {}, notices,
            ChatStore(File(dir, "chat"), File(dir, "chat.json")), clock = { now }, photos = photos,
        )
    }

    @After fun done() {
        scope.cancel()
        File(context.filesDir, "outbox.json").delete()
        dir.deleteRecursively()
    }

    private fun cameraPhoto(): ByteArray {
        val b = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val jpeg = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        return TestJpegs.withSegments(jpeg, TestJpegs.exif(orientation = 6))
    }

    private fun waitFor(what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!cond()) {
            check(System.currentTimeMillis() < end) { "timed out: $what" }
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private fun attached(): ChatPhoto {
        chat.attachPhoto { cameraPhoto() }
        waitFor("the photo made ready") { chat.draftPhoto != null && !chat.preparingPhoto }
        return chat.draftPhoto!!
    }

    @Test fun `a photo taken is made ready - upright, without EXIF - and waits above the input`() {
        val p = attached()
        assertEquals(900, p.width) // orientation 6: turned upright
        assertEquals(1200, p.height)
        val f = photos.file(p.id)
        assertTrue(f.isFile)
        assertEquals(f.length(), p.bytes)
        assertEquals(emptyList<String>(), Jpeg.metadata(f.readBytes()))
        assertFalse(TestJpegs.contains(f.readBytes(), TestJpegs.MODEL))
        assertTrue("nothing sent yet", chat.messages.isEmpty())
    }

    @Test fun `its ✕ lets it go, off the phone, nothing sent`() {
        val p = attached()
        chat.dropDraftPhoto()
        assertNull(chat.draftPhoto)
        waitFor("the file deleted") { !photos.file(p.id).exists() }
        assertTrue(chat.messages.isEmpty())
    }

    @Test fun `a photo alone is a message, and waits in the outbox with it (offline)`() {
        val p = attached()
        chat.send("")
        val m = chat.messages.single()
        assertEquals(p, m.photo)
        assertEquals("", m.text)
        assertNull(chat.draftPhoto)
        waitFor("the outbox has it") { Outbox(context).entries().isNotEmpty() }
        val e = Outbox(context).entries().single()
        assertEquals(p.id, e.clientId)
        assertEquals("/chat/images/${p.id}", e.upload!!.path)
        assertEquals(photos.file(p.id).absolutePath, e.upload!!.file)
        assertEquals(p.id, json.parseToJsonElement(e.body).jsonObject["image"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        // the message keeps it in the chat's month file
        val store = ChatStore(File(dir, "chat"), File(dir, "chat.json"))
        waitFor("the chat saved") { store.load(now).isNotEmpty() }
        assertEquals(p, store.load(now).single().photo)
    }

    @Test fun `with text it goes with the text, and a reply to it quotes the photo`() {
        val p = attached()
        chat.send("Kaj piše na tej tabli?")
        val m = chat.messages.single()
        assertEquals("Kaj piše na tej tabli?", m.text)
        assertEquals(p, m.photo)
        assertEquals("📷 Kaj piše na tej tabli?", ChatArchive.quote(m).text)
        val reply = ChatArchive.replyTo(m, ZoneOffset.UTC)
        assertEquals(p.id, reply["photo"]!!.jsonPrimitive.content)
    }

    @Test fun `a photo still being made ready holds the message back, and the send button too`() {
        chat.attachPhoto { Thread.sleep(300); cameraPhoto() }
        assertTrue(chat.preparingPhoto)
        chat.send("Že?")
        assertTrue("nothing sent while it's made ready", chat.messages.isEmpty())
        waitFor("ready") { !chat.preparingPhoto }
    }

    @Test fun `what isn't a photo says so and attaches nothing`() {
        chat.attachPhoto { "not a photo".toByteArray() }
        waitFor("done") { !chat.preparingPhoto }
        assertNull(chat.draftPhoto)
    }

    @Test fun `deleted - off the phone, marked, a DELETE to the node through the outbox, the text stays`() {
        val p = attached()
        chat.send("Poglej")
        val m = chat.messages.single()
        chat.deletePhoto(m.id)
        val after = chat.find(m.id)!!
        assertTrue(after.photo!!.deleted)
        assertEquals("Poglej", after.text)
        waitFor("the file deleted") { !photos.file(p.id).exists() }
        waitFor("the DELETE queued") { Outbox(context).entries().any { it.method == "DELETE" } }
        val del = Outbox(context).entries().first { it.method == "DELETE" }
        assertEquals("/chat/images/${p.id}", del.path)
        assertNull("a reply no longer points to it", ChatArchive.replyTo(after, ZoneOffset.UTC)["photo"])
    }

    @Test fun `a photo no message keeps leaves the phone after a day, one a message keeps stays`() {
        photos.save("orphan-0000000001", byteArrayOf(1, 2, 3))
        photos.save("kept-00000000001", byteArrayOf(1, 2, 3))
        val old = now - 2 * 24 * 3_600_000L
        photos.file("orphan-0000000001").setLastModified(old)
        photos.file("kept-00000000001").setLastModified(old)
        assertEquals(listOf("orphan-0000000001"), photos.sweep(setOf("kept-00000000001"), now))
        assertFalse(photos.has("orphan-0000000001"))
        assertTrue(photos.has("kept-00000000001"))
        // a newer one (a draft being made) stays
        photos.save("fresh-0000000001", byteArrayOf(1))
        assertEquals(emptyList<String>(), photos.sweep(setOf("kept-00000000001"), System.currentTimeMillis()))
        assertTrue(photos.has("fresh-0000000001"))
    }

    @Test fun `the bubble's thumbnail is the phone's copy, scaled`() = runBlocking {
        val p = attached()
        val b = chat.photoImage(p, 300)
        assertNotNull(b)
        assertTrue(maxOf(b!!.width, b.height) in 300..1200)
        assertNull("a deleted photo shows none", chat.photoImage(p.copy(deleted = true), 300))
    }
}
