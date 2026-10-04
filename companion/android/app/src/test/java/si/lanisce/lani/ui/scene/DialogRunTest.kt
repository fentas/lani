package si.lanisce.lani.ui.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.DialogReply
import si.lanisce.lani.game.scene.Sky
import si.lanisce.lani.game.scene.SkyCue
import si.lanisce.lani.game.scene.hasFx
import si.lanisce.lani.game.scene.hasSky
import si.lanisce.lani.ui.scene.DialogRun.Step

class DialogRunTest {
    private val soup = Dialog(
        id = "juha",
        talk = "nedeljsko-kosilo",
        lines = listOf(
            DialogLine(who = "babica", sl = "Dober večer! Si lačen?", en = "Good evening! Are you hungry?"),
            DialogLine(choices = listOf(
                DialogChoice("Ja, zelo.", "Yes, very.", ok = true, reply = DialogReply("Potem sedi k ognju.", "Then sit by the fire.")),
                DialogChoice("Dobro jutro!", "Good morning!", why = "It's evening: dober večer."),
                DialogChoice("Na zdravje!", "Cheers!", why = "That's for a toast."),
            )),
            DialogLine(who = "babica", sl = "Izvoli, juha je vroča.", en = "Here you are, the soup is hot."),
            DialogLine(choices = listOf(
                DialogChoice("Hvala lepa!", "Thank you!", ok = true),
                DialogChoice("Prosim.", "Please.", why = "When you get something, say hvala."),
            )),
            DialogLine(who = "babica", sl = "Dober tek!", en = "Enjoy your meal!"),
            DialogLine(who = "babica", sl = "Pridi spet jutri.", en = "Come again tomorrow."),
        ),
    )

    @Test fun `the first line is said and the learner's turn follows right away`() {
        val r = DialogRun.start(soup, "babica")
        assertEquals(listOf(Said("babica", "Dober večer! Si lačen?", "Good evening! Are you hungry?")), r.said)
        assertEquals(Step.CHOOSE, r.step)
        assertEquals(3, r.choices.size)
    }

    @Test fun `with a seed the choices come shuffled, the right one not always first, and the pick is the one shown`() {
        val firsts = (1L..40L).map { seed -> DialogRun.start(soup, "babica", seed).choices.first().ok }
        assertTrue("the right choice first every time", firsts.any { !it })
        assertTrue("the right choice never first", firsts.any { it })
        val r = DialogRun.start(soup, "babica", seed = 7)
        assertEquals(soup.lines[1].choices.toSet(), r.choices.toSet()) // the same choices, in another order
        assertEquals(r.choices, r.choose(r.choices.indexOfFirst { !it.ok }).choices) // the order holds for the whole turn
        val right = r.choose(r.choices.indexOfFirst { it.ok })
        assertEquals(Said(null, "Ja, zelo.", "Yes, very."), right.said[1])
        assertEquals(listOf(0), right.picks) // the pick is the file's choice (the cues follow the file)
    }

    @Test fun `a wrong choice counts once, says why, and the learner tries again`() {
        val r0 = DialogRun.start(soup, "babica")
        val r1 = r0.choose(1)
        assertEquals(Step.CHOOSE, r1.step)
        assertEquals("It's evening: dober večer.", r1.why)
        assertEquals(setOf(1), r1.tried)
        assertEquals(1, r1.wrongPick)
        assertEquals(1, r1.mistakes)
        assertEquals(r0.said, r1.said) // nothing said
        assertSame(r1, r1.choose(1)) // the same wrong one again doesn't count twice
        val r2 = r1.choose(2)
        assertEquals(2, r2.mistakes)
        assertEquals(setOf(1, 2), r2.tried)
    }

    @Test fun `a turn missed the first time counts once however many wrong picks it took (a story's reading practice)`() {
        // two wrong picks at the first turn, the second turn right away: 2 turns, 1 missed
        val r = DialogRun.start(soup, "babica").choose(1).choose(2).choose(0).next().choose(0)
        assertEquals(2, r.picks.size)
        assertEquals(2, r.mistakes)
        assertEquals(1, r.missedTurns)
        assertEquals(0, DialogRun.start(soup, "babica").choose(0).next().choose(0).missedTurns)
    }

    @Test fun `a right choice is said, the reply follows, then the dialog goes on`() {
        val r = DialogRun.start(soup, "babica").choose(1).choose(0)
        assertEquals(Said(null, "Ja, zelo.", "Yes, very."), r.said[1])
        assertEquals(Said("babica", "Potem sedi k ognju.", "Then sit by the fire."), r.said[2])
        assertEquals(Step.LISTEN, r.step) // the reply is followed by her next line: tap on
        assertTrue(r.tried.isEmpty())
        assertNull(r.why)
        assertNull(r.wrongPick)
        assertEquals(1, r.mistakes) // kept for the reward
        val next = r.next()
        assertEquals("Izvoli, juha je vroča.", next.said.last().sl)
        assertEquals(Step.CHOOSE, next.step)
    }

    @Test fun `without a reply the next line comes at once, and two lines in a row need a tap each`() {
        var r = DialogRun.start(soup, "babica").choose(0).next().choose(0)
        assertEquals(listOf(null, "babica"), r.said.takeLast(2).map { it.who })
        assertEquals("Dober tek!", r.said.last().sl)
        assertEquals(Step.LISTEN, r.step)
        assertSame(r, r.choose(0)) // no choosing while listening
        r = r.next()
        assertEquals("Pridi spet jutri.", r.said.last().sl)
        assertEquals(Step.LISTEN, r.step)
        r = r.next()
        assertEquals(Step.END, r.step)
        assertEquals(0, r.mistakes)
        assertEquals(6 + 1, r.said.size) // 4 lines, 2 answers, 1 reply
        assertSame(r, r.next())
    }

    /** Micka asks, Jan answers: a wrong choice she reacts to (and brings water), one she doesn't; then two right answers. */
    private val hungry = Dialog(
        id = "lacen",
        lines = listOf(
            DialogLine(who = "babica", sl = "Si lačen, fant?", en = "Are you hungry, lad?"),
            DialogLine(choices = listOf(
                DialogChoice("Ja, zelo sem lačen.", "Yes, I am very hungry.", ok = true, reply = DialogReply("Jej, jej!", "Eat, eat!", fx = mapOf("steam" to 1f))),
                DialogChoice("Ja, zelo sem žejen.", "Yes, I am very thirsty.", why = "Žejen is thirsty. Hungry: lačen.",
                    reply = DialogReply("Žejen? Tu imaš vodo.", "Thirsty? Here is some water.", fx = mapOf("oven" to 1f))),
                DialogChoice("Ja, zelo sem lačna.", "Yes, I am very hungry.", why = "You are a man: lačen."),
            )),
            DialogLine(who = "babica", sl = "Adijo, fant!", en = "Bye, lad!"),
            DialogLine(choices = listOf(
                DialogChoice("Nasvidenje, babica!", "Goodbye, grandma!", ok = true, reply = DialogReply("Pridi spet.", "Come again.")),
                DialogChoice("Čav, babica!", "Bye, grandma!", ok = true, reply = DialogReply("Čav, čav! Ti si pa en frajer.", "Bye, bye! Aren't you a cool one.", sky = SkyCue(fog = 0.5f))),
                DialogChoice("Dober dan, babica!", "Hello, grandma!", why = "Dober dan says hello. Leaving: nasvidenje.",
                    reply = DialogReply("Dober dan? Saj že greš!", "Hello? But you are leaving!")),
            )),
        ),
    )

    @Test fun `a wrong choice with a reaction is said, the person answers what they heard, and the learner picks again`() {
        val r0 = DialogRun.start(hungry, "babica")
        assertNull(r0.reaction)
        val r1 = r0.choose(1)
        assertEquals(Step.CHOOSE, r1.step) // still the learner's turn
        assertEquals(
            listOf(Said(null, "Ja, zelo sem žejen.", "Yes, I am very thirsty.", wrong = true), Said("babica", "Žejen? Tu imaš vodo.", "Thirsty? Here is some water.")),
            r1.said.drop(1),
        )
        assertEquals("Žejen? Tu imaš vodo.", r1.reaction?.sl)
        assertEquals("Žejen is thirsty. Hungry: lačen.", r1.why) // the why is still there
        assertEquals(1, r1.mistakes)
        assertEquals(r0.progress, r1.progress) // no line passed
        assertSame(r1, r1.choose(1)) // the same wrong one again: no second reaction, no second mistake
        // a wrong choice without a reaction: nothing said, as before
        val r2 = r1.choose(2)
        assertEquals(r1.said, r2.said)
        assertNull(r2.reaction)
        assertEquals(2, r2.mistakes)
        // then the right one: said, its reply follows, the reaction is over
        val r3 = r2.choose(0)
        assertEquals(listOf(Said(null, "Ja, zelo sem lačen.", "Yes, I am very hungry."), Said("babica", "Jej, jej!", "Eat, eat!")), r3.said.takeLast(2))
        assertNull(r3.reaction)
        assertEquals(2, r3.mistakes)
    }

    @Test fun `a reaction's effects pass with it, the right reply's stay`() {
        val r = DialogRun.start(hungry, "babica")
        assertEquals(emptyMap<String, Float>(), r.fx(emptyMap()))
        val wrong = r.choose(1)
        assertEquals(mapOf("oven" to 1f), wrong.fx(emptyMap())) // Micka brings the water
        assertEquals(emptyMap<String, Float>(), wrong.choose(2).fx(emptyMap())) // another wrong one without a reaction: gone
        assertEquals(mapOf("steam" to 1f), wrong.choose(0).fx(emptyMap())) // the right one: its reply's effect, the water gone
        // a dialog whose only effect is a reaction's has none to keep for the day, and no weather either
        val onlyReaction = hungry.copy(lines = hungry.lines.take(3).map { l -> l.copy(choices = l.choices.map { c -> if (c.ok) c.copy(reply = null) else c }) })
        assertFalse(onlyReaction.hasFx)
        assertFalse(onlyReaction.hasSky)
        assertTrue(hungry.hasFx && hungry.hasSky)
    }

    @Test fun `a turn with two right choices takes either, each with its own reply, and the cues follow the one taken`() {
        val at = DialogRun.start(hungry, "babica").choose(0).next()
        assertEquals(Step.CHOOSE, at.step)
        val polite = at.choose(0)
        assertEquals(Step.END, polite.next().step)
        assertEquals(listOf(Said(null, "Nasvidenje, babica!", "Goodbye, grandma!"), Said("babica", "Pridi spet.", "Come again.")), polite.said.takeLast(2))
        assertEquals(listOf(0, 0), polite.picks)
        assertEquals(0f, polite.sky(Sky.CLEAR).fog)
        val cheeky = at.choose(1)
        assertEquals(Step.END, cheeky.next().step)
        assertEquals("Čav, čav! Ti si pa en frajer.", cheeky.said.last().sl)
        assertEquals(listOf(0, 1), cheeky.picks) // the file's index: the second right choice's cue
        assertEquals(0.5f, cheeky.sky(Sky.CLEAR).fog)
        assertEquals(0, cheeky.mistakes)
        // shuffled, the picks are still the file's
        for (seed in 1L..20L) {
            val r = DialogRun.start(hungry, "babica", seed).let { it.choose(it.choices.indexOfFirst { c -> c.ok }) }.next()
            val k = r.choices.indexOfFirst { it.sl.startsWith("Čav") }
            assertEquals(1, r.choose(k).picks.last())
            assertEquals("Čav, čav! Ti si pa en frajer.", r.choose(k).said.last().sl)
        }
    }

    @Test fun `a dialog ending with the learner's answer ends right after it`() {
        val d = Dialog("x", listOf(
            DialogLine(who = "a", sl = "Kako si?", en = "How are you?"),
            DialogLine(choices = listOf(DialogChoice("Dobro, hvala.", ok = true), DialogChoice("Dober dan.", why = "A greeting, not an answer."))),
        ))
        val r = DialogRun.start(d, "a").choose(0)
        assertEquals(Step.END, r.step)
        assertEquals(2, r.progress.first)
    }

    @Test fun `a turn without a right answer doesn't trap the learner`() {
        val d = Dialog("x", listOf(DialogLine(who = "a", sl = "Živjo!"), DialogLine(choices = listOf(DialogChoice("Živjo!"), DialogChoice("Zdravo!")))))
        assertEquals(Step.END, DialogRun.start(d, "a").choose(1).step)
    }

    /** Janez by the cellar: a turn on the accusative of direction, one wrong choice a case slip, one a slip of the dual. */
    private val cellar = Dialog("klet", listOf(
        DialogLine(who = "janez", sl = "Dežuje!", en = "It's raining!"),
        DialogLine(choices = listOf(
            DialogChoice("Pojdiva v klet!", "Let's go into the cellar!", ok = true),
            DialogChoice("Pojdiva v kleti!", "Let's go in the cellar!", why = "Where to: v klet, the accusative.", grammar = "kam-tozilnik"),
            DialogChoice("Pojdimo v klet!", "Let's (all) go into the cellar!", why = "Just you two: the dual, pojdiva.", grammar = "dvojina"),
        )),
        DialogLine(who = "janez", sl = "Hitro!", en = "Quick!"),
        DialogLine(grammar = "tozilnik", choices = listOf(
            DialogChoice("Vidim sod.", "I see a barrel.", ok = true),
            DialogChoice("Vidim soda.", "I see a barrel.", why = "A barrel is a thing: sod."),
        )),
    ))

    @Test fun `a wrong choice's why leads to the page of its rule, its own or its turn's, and a right pick leaves it`() {
        val r = DialogRun.start(cellar, "janez")
        val slip = r.choose(1)
        assertEquals("kam-tozilnik", slip.rule)
        assertEquals("dvojina", slip.choose(2).rule)
        val on = slip.choose(0)
        assertNull(on.rule)
        // the turn names the page for a choice that doesn't
        assertEquals("tozilnik", on.next().choose(1).rule)
        assertNull(DialogRun.start(soup, "babica").choose(1).rule) // a why that is no rule of the book
    }

    @Test fun `a pick says what it answers on a rule, a slip on the choice's page, the first pick right on the turn's`() {
        val r = DialogRun.start(cellar, "janez")
        assertEquals(DialogRun.RuleAnswer("kam-tozilnik", false, "Pojdiva v kleti!", "Pojdiva v klet!"), r.ruleAnswer(1))
        assertEquals(DialogRun.RuleAnswer("dvojina", false, "Pojdimo v klet!", "Pojdiva v klet!"), r.ruleAnswer(2))
        // the choices name two pages and the turn none: a right pick counts on neither
        assertNull(r.ruleAnswer(0))
        assertNull(r.choose(1).ruleAnswer(1)) // the same wrong one again counts nothing
        val turn = r.choose(0).next()
        assertEquals(DialogRun.RuleAnswer("tozilnik", true, "Vidim sod.", null), turn.ruleAnswer(0))
        assertEquals(DialogRun.RuleAnswer("tozilnik", false, "Vidim soda.", "Vidim sod."), turn.ruleAnswer(1))
        assertNull(turn.choose(1).ruleAnswer(0)) // right only after a slip: not right the first time
        assertNull(DialogRun.start(soup, "babica").ruleAnswer(1))
        // shown shuffled, the answer is the choice shown
        for (seed in 1L..10L) {
            val s = DialogRun.start(cellar, "janez", seed)
            val k = s.choices.indexOfFirst { it.sl == "Pojdimo v klet!" }
            assertEquals("dvojina", s.ruleAnswer(k)?.page)
        }
    }

    @Test fun `a line without a speaker is the partner's, and an empty one is skipped`() {
        val d = Dialog("x", listOf(DialogLine(sl = "Pozdravljen!"), DialogLine(who = "a"), DialogLine(who = "a", sl = "Adijo.")))
        val r = DialogRun.start(d, "babica")
        assertEquals("babica", r.said.single().who)
        assertEquals(listOf("Pozdravljen!", "Adijo."), r.next().said.map { it.sl })
    }
}
