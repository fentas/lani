package si.lanisce.lani.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import si.lanisce.lani.data.Prefs
import java.io.File
import java.nio.file.Files

/**
 * 💬 in a screen's top bar (README "Tutor chat", "The exercise on screen"): the chat opens with what is on screen
 * attached, nothing sent; ✕ takes it away; closed unsent, the chat lets it go, so the chat opened elsewhere later has none.
 * "Ask" on an answer's feedback keeps its attachment as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatAttachTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val chat: ChatController by lazy {
        val dir = Files.createTempDirectory("chat").toFile()
        val notices = Notices()
        ChatController(
            scope, Prefs(context), SyncController(context, scope, { null }, notices) {}, notices,
            ChatStore(File(dir, "chat"), File(dir, "chat.json")), clock = { 1_000L },
        )
    }

    private val exercise = ChatContext("Kje je ___?", buildJsonObject { put("type", "choice"); put("answered", false) })

    @After fun done() = scope.cancel()

    @Test fun `the top bar's 💬 opens the chat with the exercise attached, and sends nothing`() {
        chat.openFrom(exercise)
        assertTrue(chat.isOpen)
        assertSame(exercise, chat.attached)
        assertTrue("nothing sent", chat.messages.isEmpty())
        assertFalse(chat.typing)
    }

    @Test fun `the learner's message carries it, then it's gone`() {
        chat.openFrom(exercise)
        chat.send("Kaj pomeni postaja?")
        val sent = chat.messages.single()
        assertEquals("Kje je ___?", sent.about)
        assertEquals("choice", (sent.data!!["about_exercise"] as JsonObject)["type"]!!.jsonPrimitive.content)
        assertNull(chat.attached)
    }

    @Test fun `✕ takes it away, a general question goes without it`() {
        chat.openFrom(exercise)
        chat.attached = null // the chip's ✕
        chat.send("Kako si?")
        assertNull(chat.messages.single().about)
        assertNull(chat.messages.single().data)
    }

    @Test fun `closed unsent, the chat lets it go, and opened elsewhere later nothing is attached`() {
        chat.openFrom(exercise)
        chat.close()
        assertNull(chat.attached)
        chat.open()
        assertNull(chat.attached)
        // nothing on screen to attach: the chat opens as it is
        chat.openFrom(null)
        assertNull(chat.attached)
    }

    @Test fun `"Ask" on the feedback keeps its attachment as before, also when the chat closes`() {
        val answered = ChatContext("Kje je ___? → postajo", buildJsonObject { put("verdict", "wrong") })
        chat.openFrom(exercise)
        chat.askAbout(answered)
        chat.close()
        assertSame(answered, chat.attached)
        // the top bar's again: what's on screen now
        chat.openFrom(exercise)
        assertSame(exercise, chat.attached)
    }
}
