package si.lanisce.lani.ui

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import si.lanisce.lani.app.ExerciseSource
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading
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

    @Test fun `a review card's answer for the node's log carries the card, how it was asked, what was typed against what was expected`() {
        val ex = Exercise.Translate("to make a fire", listOf("kuriti ogenj"))
        val a = reviewAnswer(ex, Outcome(Verdict.WRONG, "kurim", "kuriti ogenj", null, hint = Grading.Hint.ENDING, hints = 1), "vocab_kurim", Variant.TYPE)
        assertEquals("vocab_kurim", a["item_id"]!!.jsonPrimitive.content)
        assertEquals("type", a["variant"]!!.jsonPrimitive.content)
        assertEquals("translate", a["type"]!!.jsonPrimitive.content)
        assertEquals("to make a fire", a["prompt"]!!.jsonPrimitive.content)
        assertEquals("kurim", a["answer"]!!.jsonPrimitive.content)
        assertEquals("kuriti ogenj", a["expected"]!!.jsonPrimitive.content)
        assertEquals("wrong", a["verdict"]!!.jsonPrimitive.content)
        assertEquals("ending", a["hint"]!!.jsonPrimitive.content)
        assertEquals("1", a["hints_used"]!!.jsonPrimitive.content)
        assertFalse("spoken" in a)
    }

    @Test fun `a self-rated flashcard keeps no answer, a spoken one says so, long text is cut`() {
        val flip = reviewAnswer(Exercise.Flashcard("hvala", "thanks"), Outcome(Verdict.CORRECT, "", null, null), "vocab_hvala", Variant.FLIP)
        assertFalse("answer" in flip)
        assertFalse("expected" in flip)
        assertEquals("correct", flip["verdict"]!!.jsonPrimitive.content)
        val said = reviewAnswer(Exercise.Speak("hvala", "say thanks"), Outcome(Verdict.ALMOST, "hvla".repeat(80), "hvala", null, spoken = true), "vocab_hvala", Variant.SPEAK)
        assertEquals("true", said["spoken"]!!.jsonPrimitive.content)
        assertEquals(200, said["answer"]!!.jsonPrimitive.content.length)
    }

    @Test fun `without a source nothing is made up`() {
        val ctx = chatContext(Exercise.Flashcard("a", "b"), Outcome(Verdict.CORRECT, "", null, null), null)
        assertFalse("source" in ctx.data)
        assertFalse("module_id" in ctx.data)
    }
}
