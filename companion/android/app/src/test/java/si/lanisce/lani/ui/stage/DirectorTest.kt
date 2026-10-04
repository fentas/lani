package si.lanisce.lani.ui.stage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Clips
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.scene.Pose
import si.lanisce.lani.game.villagers.VillagerLine
import si.lanisce.lani.game.villagers.VillagerLines
import java.time.LocalDate

class DirectorTest {
    private val day = LocalDate.of(2026, 9, 24)

    private val luka = StagePerson(
        id = "luka", name = "Pastir Luka", emoji = "🐑", art = "shepherd", voice = Clips.MALE, role = "Pastir · Shepherd", register = "ti",
        lines = VillagerLines(
            greet = listOf(VillagerLine("Dober dan!", "Good day!"), VillagerLine("Ej, Jan! Kako si?", "Hey, Jan! How are you?", 1)),
            cheer = listOf(VillagerLine("Bravo!", "Well done!"), VillagerLine("Točno tako!", "Exactly!"), VillagerLine("Ej, super!", "Hey, great!", 3)),
            comfort = listOf(VillagerLine("Nič hudega. Še enkrat!", "Never mind. Once more!")),
            listen = listOf(VillagerLine("Slišiš zvonce?", "Do you hear the bells?")),
            bye = listOf(VillagerLine("Adijo!", "Bye!")),
        ),
        level = 1,
    )

    private val flash = Exercise.Flashcard("hvala", "thank you")
    private val quietFlash = Exercise.Flashcard("hvala", "thank you", speak = false)
    private val cloze = Exercise.Cloze("Jaz ___ Jan.", listOf("sem"))
    private val listen = Exercise.Choice("", listOf("a", "b"), 0, audio = "Dober dan")
    private val dictation = Exercise.Dictation("Dober večer", listOf("Dober večer"))
    private val hiddenSpeak = Exercise.Speak("Hvala lepa", "Say thank you", show = false)

    @Test fun `listening cups an ear, quietly, until the answer`() {
        val d = Director(luka, 1, day)
        for (ex in listOf(listen, dictation)) {
            val c = d.prompt(0, ex, null)!!
            assertEquals(Pose.LISTEN, c.pose)
            assertFalse("the audio prompt itself is voiced", c.voice)
            assertNull(c.holdMs)
            assertTrue(c.line!!.target in (luka.lines.listen + Lines.listen("ti")).map { it.target })
        }
    }

    @Test fun `a run without an intro opens with their warmest greeting`() {
        val c = Director(luka, 1, day, greet = true).prompt(0, cloze, null)!!
        assertEquals(Pose.TALK, c.pose)
        assertEquals("Ej, Jan! Kako si?", c.line!!.target)
        assertTrue(c.voice)
        assertTrue(c.holdMs!! > 0)
        // Without greet, a lead-in for the kind of exercise.
        val lead = Director(luka, 1, day).prompt(0, cloze, null)!!
        assertTrue(lead.line!!.target in Lines.leadIns(cloze, "ti").map { it.target })
        // A first exercise that is heard: the greeting, then an ear cupped.
        val heard = Director(luka, 1, day, greet = true).prompt(0, listen, null)!!
        assertEquals(Pose.LISTEN, heard.pose)
        assertTrue(heard.line!!.target.startsWith("Ej, Jan! Kako si? "))
    }

    @Test fun `lead-ins are voiced when the kind changes, never over the exercise's own audio`() {
        val d = Director(luka, 1, day)
        assertTrue(d.prompt(0, quietFlash, null)!!.voice)
        assertFalse("same kind again", d.prompt(1, quietFlash, quietFlash)!!.voice)
        assertTrue("kind changed", d.prompt(2, cloze, quietFlash)!!.voice)
        assertFalse("a flashcard reads itself aloud", d.prompt(3, flash, cloze)!!.voice)
    }

    @Test fun `a timed run keeps the cheer up instead of a lead-in for the same kind`() {
        val d = Director(luka, 1, day)
        assertNull(d.prompt(1, cloze, cloze, fast = true))
        assertNotNull(d.prompt(2, quietFlash, cloze, fast = true))
    }

    @Test fun `answers get a cheer, a gentle almost, or comfort`() {
        val d = Director(luka, 1, day)
        val right = d.answer(Verdict.CORRECT, 0, cloze, fast = false)
        assertTrue(right.pose == Pose.CHEER || right.pose == Pose.HAPPY)
        assertTrue(right.voice)
        assertTrue(right.line!!.target in Lines.pool(luka.lines.cheer, Lines.cheer("ti"), 1).map { it.target })
        val almost = d.answer(Verdict.ALMOST, 0, cloze, fast = false)
        assertEquals(Pose.HAPPY, almost.pose)
        assertTrue(almost.line!!.target in Lines.almost("ti").map { it.target })
        val wrong = d.answer(Verdict.WRONG, 0, cloze, fast = false)
        assertEquals(Pose.SAD, wrong.pose)
        assertTrue(wrong.line!!.target in Lines.pool(luka.lines.comfort, Lines.comfort("ti"), 1).map { it.target })
        // A right answer after a hint is pleased, not jubilant.
        assertEquals(Pose.HAPPY, d.answer(Verdict.CORRECT, 1, cloze, fast = false).pose)
    }

    @Test fun `warm lines wait for the friendship`() {
        val d = Director(luka, 1, day)
        repeat(40) { assertNotEquals("Ej, super!", d.answer(Verdict.CORRECT, 0, cloze, fast = false).line!!.target) }
        val close = Director(luka.copy(level = 3), 1, day)
        assertTrue((0 until 40).any { close.answer(Verdict.CORRECT, 0, cloze, fast = false).line!!.target == "Ej, super!" })
    }

    @Test fun `a timed run's right answers and a hidden speaking model stay quiet`() {
        val d = Director(luka, 1, day)
        assertFalse(d.answer(Verdict.CORRECT, 0, cloze, fast = true).voice)
        assertTrue("a wrong answer in a timed run is still comforted aloud", d.answer(Verdict.WRONG, 0, cloze, fast = true).voice)
        assertFalse(d.answer(Verdict.WRONG, 0, hiddenSpeak, fast = false).voice)
    }

    @Test fun `never the same line twice in a row`() {
        val d = Director(luka, 5, day)
        var last: String? = null
        repeat(30) {
            val l = d.answer(Verdict.CORRECT, 0, cloze, fast = false).line!!.target
            assertNotEquals(last, l)
            last = l
        }
    }

    @Test fun `the same day and village say the same lines`() {
        fun run(seed: Long) = Director(luka, seed, day).let { d -> (0 until 8).map { d.answer(Verdict.CORRECT, 0, cloze, false).line!!.target } }
        assertEquals(run(9), run(9))
    }

    @Test fun `a hint makes them think along, quietly`() {
        val c = Director(luka, 1, day).hint()
        assertEquals(Pose.THINK, c.pose)
        assertFalse(c.voice)
        assertTrue(c.line!!.target in Lines.think("ti").map { it.target })
    }

    @Test fun `the end waves with the tally and goodbye`() {
        val c = Director(luka, 1, day).end(7, 10, 10)
        assertEquals(Pose.WAVE, c.pose)
        assertTrue(c.voice)
        assertTrue(c.line!!.target, c.line!!.target.startsWith("7 od 10 pravilno."))
        assertTrue(c.line!!.target.endsWith("Adijo!"))
        assertTrue(c.line!!.base.startsWith("7 of 10 right."))
        // Nothing answered (left before the first answer): just goodbye.
        val bye = Director(luka, 1, day).end(0, 0, 12).line!!.target
        assertTrue(bye, bye in Lines.pool(luka.lines.bye, Lines.bye("ti"), 1).map { it.target })
    }

    @Test fun `the stage collapses on short screens, with large fonts and while typing`() {
        assertFalse(StageLayout.compact(915, 1f, keyboard = false))
        assertTrue(StageLayout.compact(915, 1f, keyboard = true))
        assertTrue(StageLayout.compact(915, 1.5f, keyboard = false))
        assertTrue(StageLayout.compact(560, 1f, keyboard = false))
    }
}
