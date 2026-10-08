package si.lanisce.lani.road

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.json
import si.lanisce.lani.game.PlayReviewDay
import java.time.LocalDate

/**
 * How the car's quiz answers count (RoadQuizCount; companion/GAME.md, "Words met in play"): as a dialog's do, a right
 * answer on one of the learner's words due or nearly due is a review (quality 4), once a day; a wrong one about its meaning
 * lowers it gently (a bridge that takes a dialog's words), a wrong form leaves it; the rules and forms go to the app.
 */
class RoadQuizCountTest {
    private val today = LocalDate.parse("2026-10-08")
    private val due = QuizCard("c1", "kruh", "2026-10-08", 6)
    private val later = QuizCard("c2", "mleko", "2026-10-30", 20)
    private var n = 0
    private val id = { "id${n++}" }

    private fun answer(card: QuizCard, right: Boolean, form: Boolean = false, page: String? = null, at: Long = 1_000L) = QuizAnswer(
        "meaning:${card.id}", if (form) QuizKind.FORM else QuizKind.MEANING,
        QuizOption(if (right) card.word else "kaj drugega", Sound.Prompt("x"), page, listOf(QuizWord(card.id, card.word, form))),
        right, card.word, listOf(card), if (form) "pres.1sg" else null, at,
    )

    private fun body(e: si.lanisce.lani.data.OutboxEntry) = json.parseToJsonElement(e.body).jsonObject

    @Test fun `a right answer on a card due is a review of quality 4, sent as a dialog's words, one not due is only met`() {
        val s = RoadQuizCount.settle(listOf(answer(due, true), answer(later, true, at = 70_000)), today, QuizDay(), null, takesWords = true, id = id)
        val w = s.writes.single()
        assertEquals("/reviews", w.path)
        val b = body(w)
        val results = b["results"]!!.jsonArray
        assertEquals(1, results.size)
        assertEquals("c1", results[0].jsonObject["item_id"]!!.jsonPrimitive.content)
        assertEquals(4, results[0].jsonObject["quality"]!!.jsonPrimitive.int)
        assertEquals(2, b["duration_minutes"]!!.jsonPrimitive.int)
        val d = b["dialog"] as JsonObject
        assertEquals("road", d["scene"]!!.jsonPrimitive.content)
        assertEquals("quiz", d["happening"]!!.jsonPrimitive.content)
        val words = d["words"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("c1", "c2"), words.map { it["item_id"]!!.jsonPrimitive.content })
        assertEquals(listOf(true, false), words.map { it["review"]!!.jsonPrimitive.boolean })
        // the day's record: both answered, the reviewed one changed today
        assertEquals(QuizDay("2026-10-08", listOf("c1", "c2"), listOf("c1")), s.day)
        assertEquals(listOf("c1"), s.handOff!!.changed)
        assertEquals(listOf("c1"), s.handOff!!.reviewed)
    }

    @Test fun `a wrong answer about the meaning lowers the card gently, only to a bridge that takes a dialog's words`() {
        val s = RoadQuizCount.settle(listOf(answer(later, false)), today, QuizDay(), null, takesWords = true, id = id)
        val b = body(s.writes.single())
        assertTrue(b["results"]!!.jsonArray.isEmpty())
        val w = (b["dialog"] as JsonObject)["words"]!!.jsonArray.single().jsonObject
        assertEquals(false, w["right"]!!.jsonPrimitive.boolean)
        assertEquals("meaning", w["how"]!!.jsonPrimitive.content)
        assertEquals("kaj drugega", w["said"]!!.jsonPrimitive.content)
        assertEquals(listOf("c2"), s.handOff!!.changed)
        // an older bridge: nothing to lower, nothing sent
        assertTrue(RoadQuizCount.settle(listOf(answer(later, false)), today, QuizDay(), null, takesWords = false, id = id).writes.isEmpty())
    }

    @Test fun `a wrong form leaves the card, counts on its rule and goes to the card's record of forms`() {
        val s = RoadQuizCount.settle(listOf(answer(due, false, form = true, page = "glagoli-sedanjik")), today, QuizDay(), null, takesWords = true, id = id)
        val b = body(s.writes.single())
        assertTrue(b["results"]!!.jsonArray.isEmpty())
        assertEquals("form", (b["dialog"] as JsonObject)["words"]!!.jsonArray.single().jsonObject["how"]!!.jsonPrimitive.content)
        val f = b["forms"]!!.jsonArray.single().jsonObject
        assertEquals("pres.1sg", f["key"]!!.jsonPrimitive.content)
        assertEquals("glagoli-sedanjik", f["page"]!!.jsonPrimitive.content)
        val h = s.handOff!!
        assertTrue(h.changed.isEmpty())
        assertEquals(listOf(QuizRule("glagoli-sedanjik", false, "kaj drugega", "kruh")), h.rules)
        assertEquals(listOf(QuizFormAsked("c1", "pres.1sg", false, 0)), h.forms)
    }

    @Test fun `once a day, the day's first answer on a card decides, and what play counted today counts nothing more`() {
        val first = RoadQuizCount.settle(listOf(answer(due, true)), today, QuizDay(), null, takesWords = true, id = id)
        val again = RoadQuizCount.settle(listOf(answer(due, false)), today, first.day, null, takesWords = true, id = id)
        assertTrue(again.writes.isEmpty())
        assertNull(again.handOff)
        // the next day afresh
        val tomorrow = RoadQuizCount.settle(listOf(answer(due.copy(due = "2026-10-09"), true)), today.plusDays(1), first.day, null, takesWords = true, id = id)
        assertEquals(1, body(tomorrow.writes.single())["results"]!!.jsonArray.size)
        // a card «Vidim, vidim» or a dialog reviewed today (the village state's when getting ready): nothing
        val played = RoadQuizCount.settle(listOf(answer(due, true)), today, QuizDay(), PlayReviewDay("2026-10-08", listOf("c1")), takesWords = true, id = id)
        assertTrue(played.writes.isEmpty())
        // a card the deck reviewed today: met, no review
        val reviewed = RoadQuizCount.settle(listOf(answer(due.copy(last = "2026-10-08"), true)), today, QuizDay(), null, takesWords = true, id = id)
        assertTrue(body(reviewed.writes.single())["results"]!!.jsonArray.isEmpty())
    }

    @Test fun `a rule's answer without a word of the learner's goes to the app only, many words in several writes`() {
        val rule = QuizAnswer("grammar:x/y", QuizKind.GRAMMAR, QuizOption("Nimam časa.", Sound.Prompt("x"), "rodilnik-nikalnica"), true, "Nimam časa.", at = 5L)
        val s = RoadQuizCount.settle(listOf(rule), today, QuizDay(), null, takesWords = true, id = id)
        assertTrue(s.writes.isEmpty())
        assertEquals(listOf(QuizRule("rodilnik-nikalnica", true, "Nimam časa.", "Nimam časa.")), s.handOff!!.rules)
        val many = (1..25).map { answer(QuizCard("w$it", "beseda$it", "2026-10-08"), true) }
        val w = RoadQuizCount.settle(many, today, QuizDay(), null, takesWords = true, id = id).writes
        assertEquals(2, w.size)
        assertEquals(listOf(20, 5), w.map { (body(it)["dialog"] as JsonObject)["words"]!!.jsonArray.size })
        assertEquals(25, w.sumOf { body(it)["results"]!!.jsonArray.size })
    }
}
