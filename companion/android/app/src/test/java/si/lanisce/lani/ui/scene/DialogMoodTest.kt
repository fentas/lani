package si.lanisce.lani.ui.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.DialogReply
import si.lanisce.lani.game.scene.Pose

class DialogMoodTest {
    private val soup = Dialog(
        id = "juha",
        lines = listOf(
            DialogLine(who = "babica", sl = "Si lačen?", en = "Hungry?"),
            DialogLine(choices = listOf(
                DialogChoice("Ja, zelo.", ok = true, reply = DialogReply("Sedi k ognju.")),
                DialogChoice("Dobro jutro!", why = "evening"),
            )),
            DialogLine(who = "babica", sl = "Izvoli.", en = "Here you are."),
            DialogLine(choices = listOf(
                DialogChoice("Hvala!", ok = true),
                DialogChoice("Prosim!", why = "thanks"),
            )),
        ),
    )

    /** The pose at [at] ms after the mood began. */
    private fun Mood.at(at: Long, talking: Boolean = false, listening: Boolean = false) = DialogMood.pose(this, since + at, talking, listening)

    @Test fun `the person waves when the dialog opens, then stands still`() {
        val m = DialogMood.react(null, DialogRun.start(soup, "babica"), Mood.NONE, 1_000)
        assertEquals(Pose.WAVE, m.at(0))
        assertEquals(Pose.WAVE, m.at(1_400))
        assertEquals(Pose.IDLE, m.at(1_500))
    }

    @Test fun `a right pick makes them happy, a wrong one sad`() {
        val open = DialogRun.start(soup, "babica")
        val wrong = open.choose(1)
        val sad = DialogMood.react(open, wrong, Mood.NONE, 5_000)
        assertEquals(Pose.SAD, sad.at(0))
        assertEquals(Pose.IDLE, sad.at(DialogMood.SAD_MS))
        // the same wrong choice again counts once: no new reaction
        assertSame(sad, DialogMood.react(wrong, wrong.choose(1), sad, 6_000))
        val happy = DialogMood.react(wrong, wrong.choose(0), sad, 7_000)
        assertEquals(Pose.HAPPY, happy.at(0))
        assertEquals(Pose.HAPPY, happy.at(1_700))
        assertEquals(Pose.IDLE, happy.at(1_800))
    }

    @Test fun `a wrong pick they react to puzzles them, and their reaction is said after the look`() {
        val d = soup.copy(lines = soup.lines.map { l -> l.copy(choices = l.choices.map { if (it.ok) it else it.copy(reply = DialogReply("Jutro? Zdaj?")) }) })
        val open = DialogRun.start(d, "babica")
        val wrong = open.choose(1)
        val puzzled = DialogMood.react(open, wrong, Mood.NONE, 5_000)
        assertEquals(Pose.THINK, puzzled.at(0))
        assertEquals(Pose.IDLE, puzzled.at(DialogMood.PUZZLED_MS))
        assertEquals(Pose.IDLE, puzzled.at(100, talking = true)) // then they say it
        assertEquals(DialogMood.SAY_AFTER_REACTION_MS, DialogMood.sayAfter(wrong))
        assertEquals("Jutro? Zdaj?", wrong.said.last().sl)
    }

    @Test fun `listening on goes on as it was`() {
        val run = DialogRun.start(soup, "babica").choose(0)
        val m = Mood(Pose.HAPPY, 0, 1_800)
        assertSame(m, DialogMood.react(run, run.next(), m, 500))
    }

    @Test fun `the end cheers without a mistake, else a happy hop`() {
        val last = DialogRun.start(soup, "babica").choose(0).next()
        val end = last.choose(0)
        assertEquals(DialogRun.Step.END, end.step)
        assertEquals(Pose.CHEER, DialogMood.react(last, end, Mood.NONE, 0).at(2_000))
        assertEquals(Pose.IDLE, DialogMood.react(last, end, Mood.NONE, 0).at(DialogMood.END_MS))
        val sloppy = last.choose(1)
        assertEquals(Pose.HAPPY, DialogMood.react(sloppy, sloppy.choose(0), Mood.NONE, 0).at(2_000))
    }

    @Test fun `closing the dialog ends the mood`() {
        val run = DialogRun.start(soup, "babica")
        assertEquals(Mood.NONE, DialogMood.react(run, null, Mood(Pose.SAD, 0, 1_800), 100))
    }

    @Test fun `their own lines win, the mic makes them listen`() {
        val m = Mood(Pose.HAPPY, 0, 1_800)
        assertEquals(Pose.IDLE, m.at(100, talking = true)) // drawn talking
        assertEquals(Pose.LISTEN, m.at(100, listening = true))
        assertEquals(Pose.LISTEN, Mood.NONE.at(100, listening = true))
        assertEquals(Pose.IDLE, Mood.NONE.at(100))
    }

    @Test fun `the first line and a reply to a pick are said after the reaction`() {
        val open = DialogRun.start(soup, "babica")
        assertEquals(DialogMood.SAY_AFTER_REACTION_MS, DialogMood.sayAfter(open))
        val reply = open.choose(0)
        assertEquals(DialogMood.SAY_AFTER_REACTION_MS, DialogMood.sayAfter(reply))
        assertEquals(DialogMood.SAY_MS, DialogMood.sayAfter(reply.next()))
    }
}
