package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.ReviewPlanner.Variant
import kotlin.random.Random

class ReviewPlannerTest {
    private val pool = listOf(
        ReviewCard("a", "hvala (lepa)", "thank you"),
        ReviewCard("b", "Kako ste? / Kako si?", "How are you?"),
        ReviewCard("c", "dober dan", "good day"),
        ReviewCard("d", "Nemčija → iz Nemčije", "Germany → from Germany"),
        ReviewCard("e", "prosim", "please"),
    )

    @Test fun `placeholders are not typeable and notes stay out of prompts`() {
        assertTrue(ReviewPlanner.accepted("Imenujem se ...").isEmpty())
        assertEquals("good day", ReviewPlanner.meaning("good day (polite hello)"))
        assertEquals("(polite)", ReviewPlanner.meaning("(polite)"))
        val card = ReviewCard("x", "dober dan", "good day (polite hello)", repetitions = 5, lastQuality = 5)
        repeat(30) {
            val ex = ReviewPlanner.plan(listOf(card), pool, canSpeak = true).single().exercise
            assertTrue(ex.toString(), "polite hello" !in ex.toString().substringBefore("explain"))
        }
    }

    @Test fun `rule and placeholder cards are only flashcards`() {
        val rule = ReviewCard("r", "dober/dobra/dobro (dan ♂ / kava ♀)", "adjective endings mirror noun gender", kind = "grammar_rule")
        val placeholder = ReviewCard("p", "Imenujem se ...", "My name is ...", repetitions = 4, lastQuality = 5)
        repeat(30) {
            assertEquals(Variant.FLIP, ReviewPlanner.plan(listOf(rule), pool, canSpeak = true).single().variant)
            assertEquals(Variant.FLIP, ReviewPlanner.plan(listOf(placeholder), pool, canSpeak = true).single().variant)
        }
    }

    @Test fun `rules and placeholders never become distractors`() {
        val rule = ReviewCard("r", "dober/dobra/dobro (dan ♂ / kava ♀)", "adjective endings mirror noun gender", kind = "grammar_rule")
        val arrow = ReviewCard("n", "Nemčija → iz Nemčije", "Germany → from Germany")
        val card = ReviewCard("c", "dober dan", "good day")
        repeat(50) {
            val ex = ReviewPlanner.plan(listOf(card), pool + rule + arrow, canSpeak = true).single().exercise
            val text = ex.toString()
            assertTrue(text, "adjective endings" !in text && "Germany →" !in text && "Nemčija →" !in text)
        }
    }

    @Test fun `accepted expands alternatives and optional parts`() {
        assertEquals(listOf("hvala", "hvala lepa"), ReviewPlanner.accepted("hvala (lepa)"))
        assertEquals(listOf("Kako ste?", "Kako si?"), ReviewPlanner.accepted("Kako ste? / Kako si?"))
        assertTrue(ReviewPlanner.accepted("Nemčija → iz Nemčije").isEmpty())
    }

    @Test fun `new cards get easy variants, known cards mostly hard ones`() {
        val r = Random(7)
        val fresh = ReviewCard("c", "dober dan", "good day", repetitions = 0)
        val known = fresh.copy(repetitions = 5, lastQuality = 5)
        val easy = (1..200).map { ReviewPlanner.plan(listOf(fresh), pool, canSpeak = true, random = r).single().variant }
        val hard = (1..200).map { ReviewPlanner.plan(listOf(known), pool, canSpeak = true, random = r).single().variant }
        assertTrue(easy.none { it.difficulty == 3 })          // never type or dictation on a new card
        assertTrue(easy.count { it == Variant.RECOGNIZE } > 60) // expected ~45 %
        assertTrue(hard.count { it.difficulty == 3 } > 100)     // type + dictation, expected ~60 %
    }

    @Test fun `untypeable cards never get typing variants`() {
        val card = pool[3].copy(repetitions = 5, lastQuality = 5)
        val variants = (1..100).map { ReviewPlanner.plan(listOf(card), pool, canSpeak = false).single().variant }.toSet()
        assertTrue(Variant.TYPE !in variants && Variant.TILES !in variants && Variant.LISTEN !in variants)
    }

    @Test fun `choice variants contain the right answer once`() {
        repeat(50) {
            val t = ReviewPlanner.plan(listOf(pool[2].copy(repetitions = 1, lastQuality = 4)), pool, canSpeak = true).single()
            val ex = t.exercise
            if (ex is Exercise.Choice) {
                val right = if (t.variant == Variant.PICK_SLOVENE) "dober dan" else "good day"
                assertEquals(right, ex.options[ex.answer])
                assertEquals(1, ex.options.count { it == right })
            }
        }
    }

    @Test fun `dictation accepts only what is spoken and hides the text`() {
        val card = pool[1].copy(repetitions = 5, lastQuality = 5) // "Kako ste? / Kako si?"
        val d = (1..300).asSequence()
            .map { ReviewPlanner.plan(listOf(card), pool, canSpeak = true).single().exercise }
            .filterIsInstance<Exercise.Dictation>().first()
        assertEquals(listOf(d.audio), d.accept)
    }

    @Test fun `recognize shows the Slovene large with read-aloud`() {
        val card = pool[2]
        val c = (1..300).asSequence()
            .map { ReviewPlanner.plan(listOf(card), pool, canSpeak = false).single() }
            .first { it.variant == Variant.RECOGNIZE }.exercise as Exercise.Choice
        assertEquals("dober dan", c.prompt)
        assertEquals("dober dan", c.say)
        assertTrue(c.instruction!!.startsWith("Kaj pomeni"))
    }

    @Test fun `speak only with a Slovene recognizer and only for known cards`() {
        val r = Random(3)
        val fresh = ReviewCard("c", "dober dan", "good day")
        val learning = fresh.copy(repetitions = 1, lastQuality = 4)
        val known = fresh.copy(repetitions = 5, lastQuality = 5)
        fun variants(c: ReviewCard, rec: Boolean) = (1..300).map { ReviewPlanner.plan(listOf(c), pool, canSpeak = true, random = r, canRecognize = rec).single().variant }
        assertTrue(Variant.SPEAK !in variants(known, rec = false))
        assertTrue(Variant.SPEAK !in variants(fresh, rec = true))
        assertTrue(variants(learning, rec = true).count { it == Variant.SPEAK } > 20) // expected ~14 %
        assertTrue(variants(known, rec = true).count { it == Variant.SPEAK } > 40)    // expected ~23 %
        assertTrue(Variant.SPEAK !in variants(pool[3].copy(repetitions = 5, lastQuality = 5), rec = true)) // not sayable
    }

    @Test fun `speak shows the meaning, hides the Slovene and accepts every variant`() {
        val card = pool[0].copy(repetitions = 5, lastQuality = 5) // "hvala (lepa)"
        val t = (1..300).asSequence()
            .map { ReviewPlanner.plan(listOf(card), pool, canSpeak = false, canRecognize = true).single() }
            .first { it.variant == Variant.SPEAK }
        val s = t.exercise as Exercise.Speak
        assertEquals("thank you", s.prompt)
        assertFalse(s.show)
        assertEquals(listOf("hvala", "hvala lepa"), s.answers())
        assertEquals(3, Variant.SPEAK.difficulty)
        assertEquals(5, ReviewPlanner.quality(Variant.SPEAK, Grading.Verdict.CORRECT))
    }

    @Test fun `harder variants earn higher quality`() {
        assertEquals(3, ReviewPlanner.quality(Variant.RECOGNIZE, Grading.Verdict.CORRECT))
        assertEquals(5, ReviewPlanner.quality(Variant.TYPE, Grading.Verdict.CORRECT))
        assertEquals(1, ReviewPlanner.quality(Variant.TYPE, Grading.Verdict.WRONG))
    }
}
