package si.lanisce.lani.ui

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import si.lanisce.lani.app.ExerciseSource
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.ReviewPlanner.Variant

/** "Ask" on an exercise's feedback: the tutor gets the exercise, the answer and where the exercise came from. */
class AskAboutTest {
    @Test fun `a question about a review card carries the card`() {
        val ex = Exercise.Translate("yes / no", listOf("ja", "ne"))
        val ctx = chatContext(ex, Outcome(Verdict.WRONG, "ja / ne", "ja", null), null, ExerciseSource.review("vocab_ne", Variant.TYPE))
        assertEquals("about_exercise", ctx.key)
        val d = ctx.data
        assertEquals("yes / no", d["prompt"]!!.jsonPrimitive.content)
        assertEquals("ja / ne", d["learner_answer"]!!.jsonPrimitive.content)
        assertEquals("wrong", d["verdict"]!!.jsonPrimitive.content)
        val source = d["source"]!!.jsonObject
        assertEquals("vocab_ne", source["card_id"]!!.jsonPrimitive.content)
        assertEquals("type", source["variant"]!!.jsonPrimitive.content)
    }

    @Test fun `a module exercise keeps its module id, and gains version and index`() {
        val ex = Exercise.Cloze("Jaz ___ Jan.", listOf("sem"))
        val ctx = chatContext(ex, Outcome(Verdict.CORRECT, "sem", "sem", null), "biti-sedanjik", ExerciseSource.module("biti-sedanjik", 2, 7))
        assertEquals("biti-sedanjik", ctx.data["module_id"]!!.jsonPrimitive.content)
        assertEquals("7", ctx.data["source"]!!.jsonObject["exercise_index"]!!.jsonPrimitive.content)
    }

    @Test fun `without a source nothing is made up`() {
        val ctx = chatContext(Exercise.Flashcard("a", "b"), Outcome(Verdict.CORRECT, "", null, null), null)
        assertFalse("source" in ctx.data)
        assertFalse("module_id" in ctx.data)
    }
}
