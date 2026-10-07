package si.lanisce.lani.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.app.DialogWordsController
import si.lanisce.lani.app.SceneTalk
import si.lanisce.lani.game.DialogReviews
import si.lanisce.lani.game.MyWord
import si.lanisce.lani.game.TurnWord
import si.lanisce.lani.game.WordAnswer
import si.lanisce.lani.game.WordTest
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.Happening
import si.lanisce.lani.game.scene.ScenePerson
import si.lanisce.lani.ui.scene.DialogRun

/**
 * The learner's words in a dialog on the wire (companion/README.md, POST /reviews' `dialog`): what a card says of its
 * schedule, what the bridge takes ("dialog-words"), the review a dialog sends, and the dashboard after it.
 */
class DialogWordsWireTest {
    private val state = """
        {"databases": {
          "learner_profile": {"learner": {"name": "Jan", "current_level": "A1"}},
          "spaced_repetition": {"items": {
            "vocab_v-kuhinji_zlica": {"type": "vocabulary", "content": "žlica", "answer": "spoon", "due_date": "2026-10-07",
              "interval_days": 6, "repetitions": 2, "last_quality": 4, "last_reviewed": "2026-10-01"},
            "vocab_word_miza": {"type": "vocabulary", "content": "miza", "answer": "table"}
          }}},
         "computed": {"today": "2026-10-07", "due_review_items": ["vocab_v-kuhinji_zlica"]},
         "features": ["dialog-words"]}
    """

    @Test fun `a card says when it is due, its interval and its last review, and the bridge what it takes`() {
        val d = Dashboard.parse(state)
        val zlica = d.pool.first { it.id == "vocab_v-kuhinji_zlica" }
        assertEquals(LocalDate.of(2026, 10, 7), zlica.due)
        assertEquals(6, zlica.interval)
        assertEquals(LocalDate.of(2026, 10, 1), zlica.lastReviewed)
        val miza = d.pool.first { it.id == "vocab_word_miza" }
        assertNull(miza.due)
        assertEquals(1, miza.interval)
        assertEquals(setOf("dialog-words"), d.features)
        // an older bridge says nothing
        assertTrue(Dashboard.parse(state.replace(""""features": ["dialog-words"]""", """"other": 1""")).features.isEmpty())
    }

    @Test fun `a word a dialog reviewed is done for today on the phone`() {
        val d = Dashboard.parse(state)
        val after = d.reviewedInDialog(listOf("vocab_v-kuhinji_zlica"), LocalDate.of(2026, 10, 7))
        assertTrue(after.dueCards.none { it.id == "vocab_v-kuhinji_zlica" })
        assertEquals(LocalDate.of(2026, 10, 7), after.pool.first { it.id == "vocab_v-kuhinji_zlica" }.lastReviewed)
        assertTrue("vocab_v-kuhinji_zlica" in after.learned)
    }

    @Test fun `the review a dialog sends has its reviews as results and every word in the dialog's part`() {
        val talk = SceneTalk(
            "kuhinja", "kuhinja/juha", ScenePerson("micka", "Babica Micka", "👵", "grandma", "stove"),
            Happening("juha", "Babica kuha juho", "micka"), Dialog("juha", listOf(DialogLine(who = "micka", sl = "Si lačen?"))),
            DialogRun(Dialog("juha", emptyList()), "micka"),
        )
        fun answer(id: String, word: String, right: Boolean, how: WordTest) =
            WordAnswer(TurnWord(MyWord(id, word, "m", setOf(word)), word + "o", 2), right, how, produced = false, turn = 1, said = "Daj mi vilice.", expected = "Daj mi žlico.")
        val outcomes = listOf(
            DialogReviews.Outcome(answer("a", "žlica", true, WordTest.MEANING), DialogReviews.Kind.REVIEW, 4),
            DialogReviews.Outcome(answer("b", "miza", false, WordTest.MEANING), DialogReviews.Kind.LOWERED),
            DialogReviews.Outcome(answer("c", "jajce", false, WordTest.FORM), DialogReviews.Kind.FORM),
        )
        val dialog = DialogWordsController.body(talk, outcomes)
        val e = Writes.reviews(listOf("a" to 4), 2, id = "d1", now = 1, dialog = dialog)
        assertEquals("/reviews", e.path)
        val body = Json.parseToJsonElement(e.body).jsonObject
        assertEquals("a", body.getValue("results").jsonArray.single().jsonObject.getValue("item_id").jsonPrimitive.content)
        val part = body.getValue("dialog").jsonObject
        assertEquals("kuhinja", part.getValue("scene").jsonPrimitive.content)
        assertEquals("juha", part.getValue("dialog").jsonPrimitive.content)
        val words = part.getValue("words").jsonArray.map { it.jsonObject }
        assertEquals(listOf("a", "b", "c"), words.map { it.getValue("item_id").jsonPrimitive.content })
        assertEquals(listOf("meaning", "meaning", "form"), words.map { it.getValue("how").jsonPrimitive.content })
        assertEquals(listOf(true, false, false), words.map { it.getValue("review").jsonPrimitive.boolean })
        assertFalse("said" in words[0])
        assertEquals("Daj mi vilice.", words[1].getValue("said").jsonPrimitive.content)
        // a plain review has no dialog part
        assertFalse("dialog" in Json.parseToJsonElement(Writes.reviews(listOf("a" to 4), 2, id = "r", now = 1).body).jsonObject)
    }
}
