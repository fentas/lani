package si.lanisce.lani.ui.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.Adaptive
import si.lanisce.lani.game.Mastery
import si.lanisce.lani.game.TurnMode
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.DialogReply
import si.lanisce.lani.ui.scene.DialogRun.Step

/**
 * A dialog with rules not introduced to the learner yet (companion/SCENES.md, "Rules not yet"): an echo turn is heard and
 * repeated, never graded; a trimmed turn is chosen about meaning; neither counts on the rule, both meet it.
 */
class DialogGateTest {
    /** Micka on a walk: going with mum (the instrumental, A2), then the same with a choice about meaning, then eggs (A1). */
    private val walk = Dialog(
        id = "sprehod",
        lines = listOf(
            DialogLine(who = "micka", sl = "Kam greš?", en = "Where are you going?"),
            DialogLine(choices = listOf(
                DialogChoice("Grem z mamo.", "I'm going with mum.", ok = true, reply = DialogReply("Lepo!", "Nice!")),
                DialogChoice("Grem z mama.", "I'm going with mum.", why = "With z: the instrumental, z mamo.", grammar = "orodnik"),
            )),
            DialogLine(who = "micka", sl = "Pa jutri?", en = "And tomorrow?"),
            DialogLine(grammar = "orodnik", choices = listOf(
                DialogChoice("Jutri grem z bratom.", "Tomorrow I'm going with my brother.", ok = true),
                DialogChoice("Jutri grem z brat.", "Tomorrow I'm going with my brother.", why = "With z: the instrumental."),
                DialogChoice("Jutri spim.", "Tomorrow I'm sleeping.", why = "She asked where you are going.", grammar = "glagoli-sedanjik",
                    reply = DialogReply("Spiš? Ves dan?", "Sleeping? All day?")),
            )),
            DialogLine(who = "micka", sl = "Koliko jajc?", en = "How many eggs?"),
            DialogLine(choices = listOf(
                DialogChoice("Deset jajc, prosim.", "Ten eggs, please.", ok = true),
                DialogChoice("Deset jajce, prosim.", "Ten eggs, please.", why = "From five on, the genitive plural.", grammar = "stevila-samostalniki"),
            )),
        ),
    )

    /** An A1 learner: the instrumental is not yet, the rest new. */
    private val adapted = Adaptive.dialog(walk, { if (it == "orodnik") Mastery.NOT_YET else Mastery.NEW }, canSay = true)

    private fun start() = DialogRun.start(adapted.dialog, "micka", seed = 4, modes = adapted.modes, notYet = adapted.notYet)

    @Test fun `an echo shows the right line, and goes on with its reply, graded nothing, meeting its rule`() {
        val r = start()
        assertEquals(TurnMode.ECHO, r.mode)
        assertEquals(listOf("Grem z mamo."), r.choices.map { it.sl })
        assertEquals(listOf("orodnik"), r.later)
        val on = r.echo()
        assertEquals(listOf(Said(null, "Grem z mamo.", "I'm going with mum."), Said("micka", "Lepo!", "Nice!")), on.said.drop(1).take(2))
        assertEquals(0, on.mistakes)
        assertTrue(on.answers.isEmpty())
        assertEquals(listOf("orodnik"), r.meetingsOf(on))
        // no choosing, typing or saying instead: never a wall, and no test either
        assertSame(r, r.letMeChoose())
        assertSame(r, r.type("mamo"))
        assertNull(r.say(listOf("grem z mamo")))
    }

    @Test fun `an echo said close enough goes on, anything else is said again, never a mistake`() {
        val r = start()
        assertEquals(Step.LISTEN, r.echo(listOf("grem z mamo"))?.step)
        // a word off, most of it there: said
        assertEquals(Step.LISTEN, r.echo(listOf("grem z mama"))?.step)
        assertNull(r.echo(listOf("dober dan")))
        assertNull(r.echo(emptyList()))
        // not an echo: nothing
        val chosen = r.echo().next()
        assertNull(chosen.echo(listOf("jutri grem z bratom")))
        assertSame(chosen, chosen.echo())
    }

    @Test fun `a trimmed turn is chosen about meaning, counts nothing on the rule not yet, and meets it`() {
        val r = start().echo().next()
        assertEquals(TurnMode.CHOOSE, r.mode)
        assertEquals(setOf("Jutri grem z bratom.", "Jutri spim."), r.choices.map { it.sl }.toSet())
        // the wrong one about meaning: a mistake, its reaction and its why, on its own page
        val slip = r.choose(r.choices.indexOfFirst { it.sl == "Jutri spim." })
        assertEquals(1, slip.mistakes)
        assertEquals("Spiš? Ves dan?", slip.reaction?.sl)
        assertEquals("glagoli-sedanjik", slip.rule)
        // the right one: nothing on the instrumental (the turn's page), the rule met once
        val right = slip.choose(slip.choices.indexOfFirst { it.ok })
        assertEquals(listOf("glagoli-sedanjik"), right.answers.map { it.page })
        assertEquals(listOf("orodnik", "orodnik"), right.meetings)
        val first = r.choose(r.choices.indexOfFirst { it.ok })
        assertTrue(r.answersOf(first).isEmpty())
        assertEquals(listOf("orodnik"), r.meetingsOf(first))
    }

    @Test fun `the other turns go on as ever`() {
        val end = start().echo().next().let { it.choose(it.choices.indexOfFirst { c -> c.ok }) }.next()
        assertTrue(end.later.isEmpty())
        val done = end.choose(end.choices.indexOfFirst { it.ok })
        assertEquals(Step.END, done.step)
        assertEquals(DialogRun.RuleAnswer("stevila-samostalniki", true, "Deset jajc, prosim.", null), done.answers.last())
        assertEquals(listOf("orodnik", "orodnik"), done.meetings)
    }

    @Test fun `a wrong pick's why doesn't lead to a page not yet`() {
        // a learner shown the whole turn (an older dialog kept), its page not yet: the slip counts nowhere
        val whole = DialogRun.start(walk, "micka", notYet = mapOf(1 to listOf("orodnik")))
        val slip = whole.choose(whole.choices.indexOfFirst { !it.ok })
        assertNull(slip.rule)
        assertTrue(slip.answers.isEmpty())
    }
}
