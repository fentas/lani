package si.lanisce.lani.app

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryTest {
    @Test fun `a saved conversation comes back as it was`() {
        val all = listOf(
            ChatMessage(fromMe = false, text = "Dober dan!", id = "m1", at = 1_790_000_000_000),
            ChatMessage(
                fromMe = true, text = "Zakaj „mi“?", about = "cloze → mi", id = "q", at = 1_790_000_060_000,
                data = buildJsonObject { put("about_exercise", buildJsonObject { put("prompt", "Daj ___ knjigo.") }) },
                bookmark = ChatBookmark(1_790_000_120_000, note = "dajalnik"),
                replyTo = ChatQuote("m1", fromMe = false, text = "Dober dan!", at = 1_790_000_000_000),
            ),
        )
        assertEquals(all, ChatHistory.decode(ChatHistory.encode(all)))
    }

    @Test fun `the conversation kept before the archive reads as it was, without times`() {
        val raw = """[{"fromMe":true,"text":"Zakaj „mi“?","about":"cloze → mi","id":"q"},{"fromMe":false,"text":"Ker …","id":"a"}]"""
        val all = ChatHistory.decode(raw)
        assertEquals(listOf("q", "a"), all.map { it.id })
        assertEquals("cloze → mi", all[0].about)
        assertTrue(all.all { it.at == null && it.bookmark == null && it.replyTo == null && it.data == null })
    }

    @Test fun `what a message doesn't have isn't written`() {
        val raw = ChatHistory.encode(listOf(ChatMessage(fromMe = true, text = "Živjo", id = "x", at = 1L)))
        assertEquals("""[{"fromMe":true,"text":"Živjo","id":"x","at":1}]""", raw)
    }

    @Test fun `a damaged file reads as an empty chat`() {
        assertTrue(ChatHistory.decode("[{\"fromMe\":tr").isEmpty())
        assertTrue(ChatHistory.decode("").isEmpty())
        assertNull(ChatHistory.decode("[]").firstOrNull())
    }
}
