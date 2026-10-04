package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.MistakeNote
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine

/**
 * Rules not yet (companion/SCENES.md and GAME.md, "Rules not yet"): a rule above the learner's level and not introduced
 * is not asked for; its turns keep what is about meaning, or are heard and repeated; each such turn passed meets it, and
 * enough meetings make it ripe to introduce, then open its page.
 */
class IntroductionTest {
    private val day = Fixtures.day0
    private val s0 = GameEngine.newGame(7, Fixtures.noon(day))

    /** A learner at A1: the instrumental (A2) and the dative (A2) are not yet, everything else is theirs. */
    private val notYet = { id: String -> id == "orodnik" || id == "dajalnik" }

    private fun wrong(sl: String, grammar: String?, why: String = "…") = DialogChoice(sl, why = why, grammar = grammar)

    /** "With mum": the instrumental after z. */
    private val withMum = DialogLine(choices = listOf(
        DialogChoice("Grem z mamo.", "I'm going with mum.", ok = true),
        wrong("Grem z mama.", "orodnik", "With z: the instrumental, z mamo."),
        wrong("Grem z mami.", "orodnik", "With z: the instrumental, z mamo."),
    ))

    @Test fun `levels in order, a page above the learner's, and the next one`() {
        assertEquals(0, Introduction.rank("A1"))
        assertEquals(1, Introduction.rank("a2"))
        assertEquals(2, Introduction.rank("B1+"))
        assertNull(Introduction.rank("beginner"))
        assertNull(Introduction.rank(null))
        assertTrue(Introduction.above("A2", "A1"))
        assertFalse(Introduction.above("A1", "A1"))
        assertFalse(Introduction.above("A1", "A2"))
        // a level not known gates nothing
        assertFalse(Introduction.above("A2", "?"))
        assertFalse(Introduction.above(null, "A1"))
        assertTrue(Introduction.next("A2", "A1"))
        assertFalse(Introduction.next("B1", "A1"))
        assertFalse(Introduction.next("A1", "A1"))
    }

    @Test fun `a rule above the learner's level is not yet until it is introduced`() {
        val none = emptyList<ReviewCard>()
        val clean = emptyList<MistakeNote>()
        assertEquals(Mastery.NOT_YET, Introduction.mastery("A2", "A1", met = false, record = null, cards = none, mistakes = clean))
        // at or below their level: new, as ever
        assertEquals(Mastery.NEW, Introduction.mastery("A1", "A1", false, null, none, clean))
        assertEquals(Mastery.NEW, Introduction.mastery("A2", "A2", false, null, none, clean))
        // introduced: its page unlocked (a module, the tutor's page, the book), answers, a card or a mistake on it
        assertEquals(Mastery.NEW, Introduction.mastery("A2", "A1", met = true, record = null, cards = none, mistakes = clean))
        assertEquals(Mastery.LEARNING, Introduction.mastery("A2", "A1", false, RuleRecord("2026-09-01", right = 1), none, clean))
        val card = ReviewCard("vocab_z_mamo", "z mamo", "with mum", kind = "grammar", category = "instrumental", repetitions = 1, lastQuality = 4)
        assertEquals(Mastery.LEARNING, Introduction.mastery("A2", "A1", false, null, listOf(card), clean))
        assertEquals(Mastery.LEARNING, Introduction.mastery("A2", "A1", false, null, none, listOf(MistakeNote("instrumental_z", 1, wrongRun = 1))))
        // not yet is below new: a turn of it and another rule goes by it, and is chosen
        assertTrue(Mastery.NOT_YET < Mastery.NEW)
        assertEquals(TurnMode.CHOOSE, Masteries.mode(listOf(Mastery.MASTERED, Mastery.NOT_YET), canSay = true))
    }

    @Test fun `a form turn of a rule not yet with nothing else to choose is an echo`() {
        val g = Introduction.turn(withMum, notYet)!!
        assertEquals(listOf("orodnik"), g.pages)
        assertEquals(listOf(0), g.keep)
        assertTrue(g.echo)
        // a rule introduced (or of their level): as it is
        assertNull(Introduction.turn(withMum) { false })
    }

    @Test fun `a wrong choice about meaning stays to choose from, the form's go`() {
        // other words: no part of the form
        val alone = withMum.copy(choices = withMum.choices + DialogChoice("Grem sam.", "I'm going alone.", why = "Mum asked you to come along."))
        val g = Introduction.turn(alone, notYet)!!
        assertEquals(listOf(0, 3), g.keep)
        assertFalse(g.echo)
        // one word of no rule (grandma, not mum): about meaning, it stays too
        val granny = withMum.copy(choices = listOf(withMum.choices[0], withMum.choices[1], DialogChoice("Grem z babico.", "I'm going with grandma.", why = "Mum asked, not grandma.")))
        assertEquals(listOf(0, 2), Introduction.turn(granny, notYet)!!.keep)
    }

    @Test fun `a form with a rule not yet isn't asked for at all, not even its other rules' forms`() {
        val eggs = DialogLine(choices = listOf(
            DialogChoice("Deset jajc, prosim.", ok = true),
            wrong("Deset jajce, prosim.", "stevila-samostalniki"),
            wrong("Deset jajcem, prosim.", "orodnik"),
        ))
        val g = Introduction.turn(eggs, notYet)!!
        assertEquals(listOf("orodnik"), g.pages)
        assertTrue(g.echo)
        // another rule's choice in another word is no part of that form: it stays, and asks for its own rule
        val coffee = DialogLine(choices = listOf(
            DialogChoice("Pijem kavo z mlekom.", ok = true),
            wrong("Pijem kavo z mleko.", "orodnik"),
            wrong("Pijem kava z mlekom.", "tozilnik"),
        ))
        assertEquals(listOf(0, 2), Introduction.turn(coffee, notYet)!!.keep)
    }

    @Test fun `the turn's own page, two right choices, and a tap turn`() {
        // the turn names the rule for all its choices: each of them tests it
        val help = DialogLine(grammar = "dajalnik", choices = listOf(DialogChoice("Pomagam mami.", ok = true), wrong("Pomagam mamo.", null)))
        val g = Introduction.turn(help, notYet)!!
        assertEquals(listOf("dajalnik"), g.pages)
        assertTrue(g.echo)
        // two right answers: the wrong one of a rule not yet goes, both right ones stay to choose between
        val thanks = DialogLine(choices = listOf(
            DialogChoice("Hvala, gospa!", ok = true), DialogChoice("Hvala lepa!", ok = true), wrong("Hvala gospe!", "dajalnik"),
        ))
        val t = Introduction.turn(thanks, notYet)!!
        assertEquals(listOf(0, 1), t.keep)
        assertFalse(t.echo)
        // a tap turn is tapped as ever: finding a place is no form; its page (on the right choice) only met
        val hide = DialogLine(choices = listOf(
            DialogChoice("Aha, za vrati si!", ok = true, tap = "behind-door", grammar = "orodnik"),
            DialogChoice("Aha, pod mizo si!", tap = "under-table", why = "Look for a clue."),
        ))
        val h = Introduction.turn(hide, notYet)!!
        assertEquals(listOf("orodnik"), h.pages)
        assertEquals(listOf(0, 1), h.keep)
        assertFalse(h.echo)
        // a turn of no rule not yet, or of no rule: nothing
        assertNull(Introduction.turn(DialogLine(choices = listOf(DialogChoice("Nasvidenje!", ok = true), wrong("Dober dan!", null))), notYet))
    }

    @Test fun `a dialog is trimmed turn by turn, and says which turns are echoes and what each meets`() {
        val d = Dialog("sprehod", listOf(
            DialogLine(who = "micka", sl = "Kam greš?", en = "Where are you going?"),
            withMum,
            DialogLine(who = "micka", sl = "Lepo!", en = "Nice!"),
            withMum.copy(choices = withMum.choices + DialogChoice("Grem sam.", "I'm going alone.", why = "…")),
            DialogLine(choices = listOf(DialogChoice("Nasvidenje!", ok = true), wrong("Dober dan!", null))),
        ))
        val g = Introduction.dialog(d, notYet)
        assertEquals(setOf(1), g.echo)
        assertEquals(mapOf(1 to listOf("orodnik"), 3 to listOf("orodnik")), g.notYet)
        assertEquals(listOf("Grem z mamo."), g.dialog.lines[1].choices.map { it.sl })
        assertEquals(listOf("Grem z mamo.", "Grem sam."), g.dialog.lines[3].choices.map { it.sl })
        assertEquals(d.lines[4], g.dialog.lines[4])
        // nothing not yet: the very dialog
        assertSame(d, Introduction.dialog(d) { false }.dialog)
    }

    @Test fun `meetings count per turn passed, and the days they were on`() {
        val s1 = Introduction.met(s0, "sl", listOf("orodnik", "dajalnik", "orodnik"), day)
        assertEquals(RuleMeetings(1, day.toString(), day.toString(), 1), Introduction.meetings(s1, "sl")["orodnik"])
        val s2 = Introduction.met(s1, "sl", listOf("orodnik"), day)
        assertEquals(RuleMeetings(2, day.toString(), day.toString(), 1), Introduction.meetings(s2, "sl")["orodnik"])
        val s3 = Introduction.met(s2, "sl", listOf("orodnik"), day.plusDays(2))
        assertEquals(RuleMeetings(3, day.toString(), day.plusDays(2).toString(), 2), Introduction.meetings(s3, "sl")["orodnik"])
        // another language's rules are another book's
        assertTrue(Introduction.meetings(s3, "it").isEmpty())
        assertSame(s3, Introduction.met(s3, "sl", emptyList(), day))
    }

    @Test fun `the book lists the rules met in the dialogs, most met first, until their pages open`() {
        val pages = si.lanisce.lani.data.Grammar.bundled("sl")
        val s1 = Introduction.met(Introduction.met(s0, "sl", listOf("orodnik", "dajalnik"), day), "sl", listOf("dajalnik"), day)
        val c = GrammarBook.chapter(pages, s1, "sl")
        assertEquals(listOf("dajalnik" to 2, "orodnik" to 1), c.seen.map { it.first.id to it.second.times })
        assertEquals(pages.size - 2, c.more)
        // once its page opens it is a page of the chapter, no longer only met
        val s2 = GrammarBook.meet(s1, "sl", listOf("dajalnik"), day).first
        val c2 = GrammarBook.chapter(pages, s2, "sl")
        assertEquals(listOf("dajalnik"), c2.met.map { it.id })
        assertEquals(listOf("orodnik"), c2.seen.map { it.first.id })
        assertEquals(pages.size - 2, c2.more)
    }

    @Test fun `ripe to introduce after a few meetings, and the page opens after more on more days, one level up only`() {
        val two = RuleMeetings(2, "2026-09-01", "2026-09-01", 1)
        assertFalse(Introduction.ripe(two, "A2", "A1"))
        val three = RuleMeetings(Introduction.RIPE, "2026-09-01", "2026-09-01", 1)
        assertTrue(Introduction.ripe(three, "A2", "A1"))
        // two levels up waits for the learner, whatever they met
        assertFalse(Introduction.ripe(three.copy(times = 20, days = 9), "B1", "A1"))
        assertFalse(Introduction.unlocks(three.copy(times = 20, days = 9), "B1", "A1"))
        // the book opens the page itself after enough meetings on enough days
        val many = RuleMeetings(Introduction.UNLOCK, "2026-09-01", "2026-09-01", 1)
        assertFalse(Introduction.unlocks(many, "A2", "A1")) // all in one day
        assertTrue(Introduction.unlocks(many.copy(days = Introduction.UNLOCK_DAYS), "A2", "A1"))
        assertFalse(Introduction.unlocks(null, "A2", "A1"))
    }

    @Test fun `adapted, a turn of a rule not yet is echoed or chosen, never typed, and gets no trap of the learner's`() {
        val d = Dialog("sprehod", listOf(
            DialogLine(who = "micka", sl = "Kam greš?", en = "Where are you going?"),
            withMum,
            DialogLine(choices = listOf(DialogChoice("Deset jajc, prosim.", ok = true), wrong("Deset jajce, prosim.", "stevila-samostalniki"))),
        ))
        val levels = { id: String -> if (notYet(id)) Mastery.NOT_YET else Mastery.SECURE }
        var skipped: Set<Int>? = null
        val a = Adaptive.dialog(d, levels, canSay = true) { x, skip -> skipped = skip; x }
        assertEquals(mapOf(1 to TurnMode.ECHO, 2 to TurnMode.TYPE), a.modes)
        assertEquals(mapOf(1 to listOf("orodnik")), a.notYet)
        assertEquals(setOf(1), skipped)
        // a learner with the instrumental introduced: as before
        val all = Adaptive.dialog(d, { Mastery.SECURE }, canSay = true)
        assertEquals(mapOf(1 to TurnMode.TYPE, 2 to TurnMode.TYPE), all.modes)
        assertTrue(all.notYet.isEmpty())
    }
}
