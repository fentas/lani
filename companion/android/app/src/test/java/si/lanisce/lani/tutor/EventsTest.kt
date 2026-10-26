package si.lanisce.lani.tutor

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.WordExample
import si.lanisce.lani.data.Writes
import java.time.ZoneId

/** The outbox's writes as the channel's events, how each is handled, and what a digest is worth. */
class EventsTest {
    private fun of(e: si.lanisce.lani.data.OutboxEntry, reactAtOnce: Boolean = false) = ShadowEvents.of("ev_" + e.clientId, e.path, e.body, 1_000, reactAtOnce)!!

    @Test fun `a review's numbers card by card - the forms asked within its cards are not cards`() {
        // the matrix, 2026-10: lanes counted form gaps as cards ("17 of 20" where the app said 19 of 20)
        val body = """{"results":[{"item_id":"a","quality":5},{"item_id":"b","quality":1},{"item_id":"c","quality":4}],"duration_minutes":2,""" +
            """"forms":[{"item_id":"a","key":"pl","page":"p","right":false},{"item_id":"c","key":"du","page":"p","right":true}],""" +
            """"counting":[{"item_id":"k","lemma":"kava","steps":[{"n":1,"right":true},{"n":2,"right":false}]}],"client_id":"x"}"""
        val e = ShadowEvents.of("ev", "/reviews", body, 0)!!
        assertEquals(
            "a review: 3 card(s), 1 wrong; within those cards (not cards of their own): 2 form(s) asked of them, 1 wrong; 2 step(s) of counting and forms cards, 1 wrong",
            e.text,
        )
        assertEquals(obj("cards" to 3, "cards_wrong" to 1, "forms_asked" to 2, "forms_wrong" to 1, "steps" to 2, "steps_wrong" to 1), e.data!!["counts"])
        // a plain review: its cards only
        val plain = ShadowEvents.of("ev2", "/reviews", """{"results":[{"item_id":"a","quality":5}],"duration_minutes":1}""", 0)!!
        assertEquals("a review: 1 card(s), 0 wrong", plain.text)
        assertEquals(obj("cards" to 1, "cards_wrong" to 0), plain.data!!["counts"])
    }

    @Test fun `each write the app sends the node is the channel's kind of event`() {
        val chat = of(Writes.message("chat", "main", "Živjo", null, id = "a"))
        assertEquals("chat" to Handling.AT_ONCE, chat.kind to chat.handling)
        assertEquals("main", chat.conversationId)
        assertEquals("Živjo", chat.text)
        val answer = of(Writes.message("answer", "ex123", "Grem domov.", null, id = "b"))
        assertEquals("answer" to Handling.AT_ONCE, answer.kind to answer.handling)
        assertEquals("ex123", answer.conversationId)
        val end = of(Writes.message("session_end", "main", "done", null, id = "c"))
        assertEquals("session_end" to Handling.DIGEST, end.kind to end.handling)
        val review = of(Writes.reviews(listOf("vocab_kurim" to 1, "vocab_peč" to 5), 3, id = "d"))
        assertEquals("review_done" to Handling.DIGEST, review.kind to review.handling)
        assertTrue(review.text, review.text.contains("2 card(s), 1 wrong"))
        assertNull(review.data!!["client_id"])
        assertEquals("words_added", of(Writes.addWord("peč", "stove", example = WordExample("Peč gori.", "The stove burns."), id = "e")).kind)
        val learned = of(Writes.learnPack("kuhinja", listOf("lonec" to 4), 2, id = "f"))
        assertEquals("words_learned", learned.kind)
        assertEquals("kuhinja", learned.data!!["pack_id"]!!.jsonPrimitive.content)
        assertEquals("reading_done", of(Writes.readingDone(obj("reading" to "micka-recept"), id = "g")).kind)
        assertEquals("villager_arrived" to Handling.RECORDED, of(Writes.message("villager_arrived", "main", "x", null, id = "h")).let { it.kind to it.handling })
        // react at once: reviews and session ends get a turn of their own
        assertEquals(Handling.AT_ONCE, of(Writes.reviews(listOf("vocab_kurim" to 5), 1, id = "i"), reactAtOnce = true).handling)
        // a write that isn't a tutor's event
        assertNull(ShadowEvents.of("x", "/game", "{}", 0))
    }

    @Test fun `a digest is worth a call for a wrong answer or a milestone, not otherwise`() {
        val fine = of(Writes.reviews(listOf("vocab_kurim" to 5), 1, id = "a"))
        val word = of(Writes.addWord("peč", "stove", id = "b"))
        assertNull(ShadowEvents.worth(listOf(fine, word)))
        assertEquals("1 answer(s) wrong", ShadowEvents.worth(listOf(fine, of(Writes.reviews(listOf("vocab_x" to 2), 1, id = "c")))))
        assertEquals("a practice run ended", ShadowEvents.worth(listOf(of(Writes.message("session_end", "main", "s", null, id = "d")))))
    }

    @Test fun `the turn's message carries the event as the channel had it`() {
        val e = of(Writes.message("chat", "main", "Kako se reče stove?", obj("about_exercise" to obj("prompt" to "stove")), id = "a"))
        val text = TurnText.event(e, ZoneId.of("Europe/Ljubljana"))
        assertTrue(text, text.startsWith("<event kind=\"chat\" conversation_id=\"main\" msg_id=\"ev_a\""))
        assertTrue(text.contains("```json\n{\"about_exercise\":{\"prompt\":\"stove\"}}"))
        assertTrue(TurnText.now(0, ZoneId.of("Europe/Ljubljana"), "Home").contains("The learner is in the app (Home)"))
        assertTrue(TurnText.closing("The day is over").startsWith("[notebook_write]"))
    }
}
