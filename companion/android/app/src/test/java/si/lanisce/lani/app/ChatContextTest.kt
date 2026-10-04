package si.lanisce.lani.app

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import si.lanisce.lani.ChallengeOrigin
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.ReviewPlanner.Variant
import si.lanisce.lani.game.Challenge
import si.lanisce.lani.game.Res

/** What the app tells the tutor about where an exercise came from, so a wrong or confusing one can be found. */
class ChatContextTest {
    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.content

    @Test fun `a review card names its id, variant and deck`() {
        val home = ExerciseSource.review("vocab_da", Variant.PICK_SLOVENE)
        assertEquals(listOf("review", "vocab_da", "pick_slovene"), listOf(home.str("kind"), home.str("card_id"), home.str("variant")))
        assertFalse("language" in home) // the home language's deck
        assertEquals("it", ExerciseSource.review("vocab_si", Variant.TYPE, "it").str("language"))
    }

    @Test fun `a module exercise names the module, its version and the exercise`() {
        val m = ExerciseSource.module("clitic-se", 3, 4)
        assertEquals(listOf("module", "clitic-se"), listOf(m.str("kind"), m.str("module_id")))
        assertEquals(3, m["module_version"]!!.jsonPrimitive.int)
        assertEquals(4, m["exercise_index"]!!.jsonPrimitive.int)
    }

    @Test fun `a pack word names the pack, the word and its card`() {
        val p = ExerciseSource.pack("hrana", "vocab_hrana_kruh", Variant.TYPE)
        assertEquals(listOf("pack", "hrana", "kruh", "vocab_hrana_kruh"), listOf(p.str("kind"), p.str("pack_id"), p.str("word_id"), p.str("card_id")))
        assertFalse("word_id" in ExerciseSource.pack("hrana", "vocab_da", Variant.TYPE)) // not this pack's card: no word
    }

    @Test fun `a challenge exercise names what it was made of`() {
        val ex = listOf<Exercise>(Exercise.Translate("yes", listOf("da")), Exercise.Cloze("Jaz ___ Jan.", listOf("sem")), Exercise.Flashcard("a", "b"))
        val c = Challenge(
            "🌾 Nabiranje", "", "🌾", ex, ex.map { Res.FOOD },
            cardIds = listOf("vocab_da", null, null), moduleIds = listOf(null, "biti-sedanjik", null),
        )
        val card = ExerciseSource.challenge(ChallengeOrigin.Gather(Res.FOOD), c, 0)
        assertEquals(listOf("challenge", "gather:food", "🌾 Nabiranje", "vocab_da"), listOf(card.str("kind"), card.str("origin"), card.str("title"), card.str("card_id")))
        assertFalse("module_id" in card)
        assertEquals("biti-sedanjik", ExerciseSource.challenge(ChallengeOrigin.Event, c, 1).str("module_id"))
        val festival = c.copy(pack = "praznik-kostanj", packWords = listOf("kostanj", "kostanj", "kostanj"))
        val word = ExerciseSource.challenge(ChallengeOrigin.Festival("martinovo"), festival, 2)
        assertEquals(listOf("festival:martinovo", "praznik-kostanj", "kostanj"), listOf(word.str("origin"), word.str("pack_id"), word.str("word_id")))
        assertEquals(2, word["exercise_index"]!!.jsonPrimitive.int)
        // a letter's question: the letter it asks about goes with it, as its prompt no longer holds it
        assertFalse("text" in card)
        val letter = c.copy(text = si.lanisce.lani.game.ReadFirst("✉️ Preberi pismo · Read the letter", listOf(listOf("Dragi Marko!"), listOf("V petek pridem.", "Lep pozdrav."))))
        assertEquals("Dragi Marko!\nV petek pridem. Lep pozdrav.", ExerciseSource.challenge(ChallengeOrigin.Surprise, letter, 0).str("text"))
    }

    @Test fun `every challenge origin has a name`() {
        assertEquals(
            listOf("event", "gather:wood", "quest:q1", "project:most", "festival:kres", "surprise"),
            listOf(
                ChallengeOrigin.Event, ChallengeOrigin.Gather(Res.WOOD), ChallengeOrigin.Quest("q1"), ChallengeOrigin.Project("most"),
                ChallengeOrigin.Festival("kres"), ChallengeOrigin.Surprise,
            ).map(ExerciseSource::originName),
        )
    }

    @Test fun `a visit request names the town and the request`() {
        val v = ExerciseSource.visitRequest("friuli-town", "r1", 5)
        assertEquals(listOf("visit_request", "friuli-town", "r1"), listOf(v.str("kind"), v.str("town"), v.str("request_id")))
    }
}
