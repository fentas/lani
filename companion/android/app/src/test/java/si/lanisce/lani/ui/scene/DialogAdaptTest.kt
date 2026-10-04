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
 * Adaptive turns (companion/SCENES.md): a form turn is chosen, typed or said by how well the learner has its rule; "✋ Let
 * me choose" and a wrong answer bring back the choices; the turns' answers count on their rules.
 */
class DialogAdaptTest {
    /** Micka at the market: ten eggs (numbers with nouns), then "we two" (the dual), then a goodbye (no form). */
    private val market = Dialog(
        id = "jajca",
        lines = listOf(
            DialogLine(who = "micka", sl = "Koliko jajc?", en = "How many eggs?"),
            DialogLine(choices = listOf(
                DialogChoice("Deset jajc, prosim.", "Ten eggs, please.", ok = true, reply = DialogReply("Izvoli.", "Here you are.")),
                DialogChoice("Deset jajce, prosim.", "Ten eggs, please.", why = "From five on, the genitive plural: deset jajc.", grammar = "stevila-samostalniki",
                    reply = DialogReply("Samo eno jajce? Deset?", "Only one egg? Ten?")),
                DialogChoice("Deset jajca, prosim.", "Ten eggs, please.", why = "Jajca is for two, three and four.", grammar = "stevila-samostalniki"),
            )),
            DialogLine(who = "micka", sl = "Kdo bo jedel?", en = "Who will eat?"),
            DialogLine(choices = listOf(
                DialogChoice("Midva bova jedla.", "The two of us will eat.", ok = true),
                DialogChoice("Midva bomo jedla.", "The two of us will eat.", why = "Midva takes the dual: bova.", grammar = "dvojina"),
            )),
            DialogLine(who = "micka", sl = "Adijo!", en = "Bye!"),
            DialogLine(choices = listOf(
                DialogChoice("Nasvidenje!", "Goodbye!", ok = true),
                DialogChoice("Dober dan!", "Hello!", why = "Leaving: nasvidenje."),
            )),
        ),
    )

    private fun levels(vararg m: Pair<String, Mastery>): (String) -> Mastery? = m.toMap()::get

    @Test fun `each form turn is asked as its rule allows, the others are chosen`() {
        assertEquals(emptyMap<Int, TurnMode>(), Adaptive.modes(market, levels(), canSay = true))
        val modes = Adaptive.modes(market, levels("stevila-samostalniki" to Mastery.SECURE, "dvojina" to Mastery.MASTERED), canSay = true)
        assertEquals(mapOf(1 to TurnMode.TYPE, 3 to TurnMode.SAY), modes)
        // nothing listens: saying is typed
        assertEquals(mapOf(1 to TurnMode.TYPE, 3 to TurnMode.TYPE), Adaptive.modes(market, levels("stevila-samostalniki" to Mastery.SECURE, "dvojina" to Mastery.MASTERED), canSay = false))
        // a wrong choice whose rule isn't known keeps the turn chosen
        val unknown = market.copy(lines = market.lines.toMutableList().also { l -> l[3] = l[3].copy(choices = l[3].choices.map { it.copy(grammar = null) }) })
        assertEquals(mapOf(1 to TurnMode.TYPE), Adaptive.modes(unknown, levels("stevila-samostalniki" to Mastery.SECURE, "dvojina" to Mastery.MASTERED), canSay = true))
    }

    @Test fun `a typed turn shows its gap and what it means, and the word typed right goes on like the right choice`() {
        val r = DialogRun.start(market, "micka", seed = 3, modes = mapOf(1 to TurnMode.TYPE))
        assertEquals(TurnMode.TYPE, r.mode)
        assertEquals("Deset ____, prosim.", r.gap?.shown)
        assertEquals("Ten eggs, please.", r.intent)
        val right = r.type("jajc")
        assertEquals(listOf(Said(null, "Deset jajc, prosim.", "Ten eggs, please."), Said("micka", "Izvoli.", "Here you are.")), right.said.drop(1))
        assertEquals(0, right.mistakes)
        assertEquals(listOf(0), right.picks)
        assertEquals(listOf(DialogRun.RuleAnswer("stevila-samostalniki", true, "Deset jajc, prosim.", null)), right.answers)
        // č/š/ž and the whole sentence typed are right too
        assertEquals(0, r.type("Deset jajc, prosim").mistakes)
        assertEquals(Step.LISTEN, r.type("JAJC").step)
        // the next turn is chosen: no mode for it
        assertEquals(TurnMode.CHOOSE, right.next().mode)
    }

    @Test fun `a form typed wrong that is a wrong choice is that choice picked, and the choices come`() {
        val r = DialogRun.start(market, "micka", seed = 3, modes = mapOf(1 to TurnMode.TYPE))
        val wrong = r.type("jajce")
        assertEquals(TurnMode.CHOOSE, wrong.mode)
        assertEquals(Step.CHOOSE, wrong.step)
        assertEquals(1, wrong.mistakes)
        assertEquals(1, wrong.missedTurns)
        assertEquals("Samo eno jajce? Deset?", wrong.reaction?.sl)
        assertEquals("From five on, the genitive plural: deset jajc.", wrong.why)
        assertEquals(setOf(wrong.choices.indexOfFirst { it.sl == "Deset jajce, prosim." }), wrong.tried)
        assertEquals(listOf(DialogRun.RuleAnswer("stevila-samostalniki", false, "Deset jajce, prosim.", "Deset jajc, prosim.")), wrong.answers)
        // then the right choice: no second answer on the rule, and the turn counts missed once
        val right = wrong.choose(wrong.choices.indexOfFirst { it.ok })
        assertEquals(1, right.answers.size)
        assertEquals(1, right.missedTurns)
    }

    @Test fun `another form typed wrong is said, the person is puzzled, and the choices come`() {
        val r = DialogRun.start(market, "micka", modes = mapOf(1 to TurnMode.TYPE))
        val wrong = r.type("jajcev")
        assertEquals(TurnMode.CHOOSE, wrong.mode)
        assertEquals(1, wrong.mistakes)
        assertEquals(Said(null, "Deset jajcev, prosim.", "", wrong = true), wrong.said[1])
        assertEquals("micka", wrong.said[2].who)
        assertEquals("Hm? Kako, prosim?", wrong.said[2].sl)
        assertEquals("Hm? Kako, prosim?", wrong.reaction?.sl)
        assertTrue(wrong.tried.isEmpty())
        assertEquals(listOf(DialogRun.RuleAnswer("stevila-samostalniki", false, "Deset jajcev, prosim.", "Deset jajc, prosim.")), wrong.answers)
        // the turn's own puzzled reaction, when it has one
        val own = market.copy(lines = market.lines.toMutableList().also { l -> l[1] = l[1].copy(puzzled = DialogReply("Kako? Ne razumem.", "What? I don't understand.")) })
        assertEquals("Kako? Ne razumem.", DialogRun.start(own, "micka", modes = mapOf(1 to TurnMode.TYPE)).type("jajcev").said[2].sl)
        // picking the right one then: the puzzled reaction is over
        val right = wrong.choose(wrong.choices.indexOfFirst { it.ok })
        assertNull(right.reaction)
        assertEquals(Step.LISTEN, right.step)
    }

    @Test fun `let me choose steps back for that turn, with no mistake`() {
        val r = DialogRun.start(market, "micka", seed = 5, modes = mapOf(1 to TurnMode.TYPE, 3 to TurnMode.SAY))
        val chose = r.letMeChoose()
        assertEquals(TurnMode.CHOOSE, chose.mode)
        assertEquals(0, chose.mistakes)
        assertEquals(r.choices, chose.choices)
        assertSame(chose, chose.letMeChoose())
        // the next turn keeps its own mode
        val at3 = chose.choose(chose.choices.indexOfFirst { it.ok }).next()
        assertEquals(TurnMode.SAY, at3.mode)
        assertEquals(TurnMode.CHOOSE, at3.letMeChoose().mode)
    }

    @Test fun `a said turn, the right sentence goes on, a wrong choice said is that choice, anything else is said again`() {
        val r = DialogRun.start(market, "micka", seed = 1, modes = mapOf(3 to TurnMode.SAY)).let { it.choose(it.choices.indexOfFirst { c -> c.ok }) }.next()
        assertEquals(TurnMode.SAY, r.mode)
        assertEquals("Midva ____ jedla.", r.gap?.shown)
        val right = r.say(listOf("midva bova jedla"))!!
        assertEquals("Midva bova jedla.", right.said.last { it.who == null }.sl)
        assertEquals(DialogRun.RuleAnswer("dvojina", true, "Midva bova jedla.", null), right.answers.last())
        val wrong = r.say(listOf("midva bomo jedla"))!!
        assertEquals(TurnMode.CHOOSE, wrong.mode)
        assertEquals(1, wrong.mistakes)
        assertEquals(DialogRun.RuleAnswer("dvojina", false, "Midva bomo jedla.", "Midva bova jedla."), wrong.answers.last())
        assertNull(r.say(listOf("dober dan")))
        assertNull(r.say(emptyList()))
        // not a said turn
        assertNull(r.letMeChoose().say(listOf("midva bova jedla")))
    }

    @Test fun `a number the recognizer writes as digits counts as the word`() {
        val sheep = Dialog("ovce", listOf(
            DialogLine(who = "luka", sl = "Koliko ovc imaš?", en = "How many sheep do you have?"),
            DialogLine(choices = listOf(
                DialogChoice("Imam dve ovci.", "I have two sheep.", ok = true),
                DialogChoice("Imam dve ovce.", "I have two sheep.", why = "Two takes the dual: dve ovci.", grammar = "stevila-samostalniki"),
            )),
        ))
        val r = DialogRun.start(sheep, "luka", modes = mapOf(1 to TurnMode.SAY))
        assertEquals(Step.END, r.say(listOf("imam 2 ovci"))?.step)
    }

    @Test fun `a typed answer counts on the page its gap tests, and a page only guessed counts nothing`() {
        val cellar = Dialog("klet", listOf(
            DialogLine(who = "janez", sl = "Dežuje!", en = "It's raining!"),
            DialogLine(choices = listOf(
                DialogChoice("Pojdiva v klet!", "Let's go into the cellar!", ok = true),
                DialogChoice("Pojdiva v kleti!", "Let's go in the cellar!", why = "Where to: v klet, the accusative.", grammar = "kam-tozilnik"),
                DialogChoice("Pojdimo v klet!", "Let's (all) go into the cellar!", why = "Just you two: the dual, pojdiva.", grammar = "dvojina"),
            )),
        ))
        // picked, a right answer counts on neither of two pages; typed, on the one its gap tests
        assertNull(DialogRun.start(cellar, "janez").ruleAnswer(0))
        val typed = DialogRun.start(cellar, "janez", modes = mapOf(1 to TurnMode.TYPE))
        assertEquals("Pojdiva v ____!", typed.gap?.shown)
        assertEquals(listOf(DialogRun.RuleAnswer("kam-tozilnik", true, "Pojdiva v klet!", null)), typed.answersOf(typed.type("klet")))
        // a form typed wrong: a slip on the gap's page, and the why leads there
        val slip = typed.type("kletjo")
        assertEquals("kam-tozilnik", slip.rule)
        assertEquals(listOf(DialogRun.RuleAnswer("kam-tozilnik", false, "Pojdiva v kletjo!", "Pojdiva v klet!")), slip.answers)
        // pages only guessed: the turn is typed as their mastery says, but nothing counts there and no why leads there
        val guessed = cellar.copy(lines = cellar.lines.map { l -> l.copy(choices = l.choices.map { it.copy(guess = it.grammar, grammar = null) }) })
        assertEquals(mapOf(1 to TurnMode.TYPE), Adaptive.modes(guessed, { Mastery.SECURE }, canSay = true))
        val g = DialogRun.start(guessed, "janez", modes = mapOf(1 to TurnMode.TYPE))
        assertTrue(g.type("klet").answers.isEmpty())
        assertTrue(g.type("kletjo").answers.isEmpty())
        assertNull(g.type("kletjo").rule)
        assertNull(g.choose(g.choices.indexOfFirst { it.sl == "Pojdiva v kleti!" }).rule)
    }

    @Test fun `a right answer after the hint says so, a slip counts as ever, and the next turn starts without it`() {
        val r = DialogRun.start(market, "micka")
        assertTrue(!r.hinted)
        val helped = r.hint()
        assertTrue(helped.hinted)
        assertSame(helped, helped.hint()) // opened again: nothing new
        // picked right: counts right, hinted
        val right = helped.choose(0)
        assertEquals(listOf(DialogRun.RuleAnswer("stevila-samostalniki", true, "Deset jajc, prosim.", null, hinted = true)), right.answers)
        // the next turn: no hint yet
        assertTrue(!right.next().hinted)
        assertEquals(DialogRun.RuleAnswer("dvojina", true, "Midva bova jedla.", null), right.next().choose(0).answers.last())
        // typed right after the hint: hinted too
        val typed = DialogRun.start(market, "micka", modes = mapOf(1 to TurnMode.TYPE)).hint().type("jajc")
        assertEquals(listOf(DialogRun.RuleAnswer("stevila-samostalniki", true, "Deset jajc, prosim.", null, hinted = true)), typed.answers)
        // a wrong pick after the hint is a slip, as ever
        val slip = helped.choose(1)
        assertEquals(listOf(DialogRun.RuleAnswer("stevila-samostalniki", false, "Deset jajce, prosim.", "Deset jajc, prosim.")), slip.answers)
        // listening, not at a turn: no hint to open
        val listening = right.hint()
        assertEquals(Step.LISTEN, listening.step)
        assertTrue(!listening.hinted)
    }

    @Test fun `picked answers count on their pages, the first right pick on the turn's page, each wrong pick on its own`() {
        val r = DialogRun.start(market, "micka")
        val first = r.choose(0)
        assertEquals(listOf(DialogRun.RuleAnswer("stevila-samostalniki", true, "Deset jajc, prosim.", null)), first.answers)
        val slip = r.choose(2).choose(1).choose(0)
        // each wrong choice counts once, on its page; the right one after them counts nothing
        assertEquals(
            listOf(
                DialogRun.RuleAnswer("stevila-samostalniki", false, "Deset jajca, prosim.", "Deset jajc, prosim."),
                DialogRun.RuleAnswer("stevila-samostalniki", false, "Deset jajce, prosim.", "Deset jajc, prosim."),
            ),
            slip.answers,
        )
        // a turn of no rule: nothing
        val end = first.next().choose(0).next().choose(0)
        assertEquals(Step.END, end.step)
        assertEquals(listOf("stevila-samostalniki", "dvojina"), end.answers.map { it.page })
    }

    @Test fun `a turn typed where nothing can listen after all, and a dialog without modes, as before`() {
        val r = DialogRun.start(market, "micka", modes = mapOf(1 to TurnMode.SAY))
        assertEquals(Step.LISTEN, r.type("jajc").step)
        val plain = DialogRun.start(market, "micka")
        assertEquals(TurnMode.CHOOSE, plain.mode)
        assertSame(plain, plain.type("jajc"))
        assertSame(plain, plain.letMeChoose())
        assertNull(plain.say(listOf("deset jajc prosim")))
        assertEquals(emptyMap<Int, TurnMode>(), DialogRun.start(market, "micka", modes = mapOf(1 to TurnMode.CHOOSE)).modes)
    }
}
