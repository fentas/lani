package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Dashboard
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Grammar
import si.lanisce.lani.data.MistakeNote
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.DialogReply

/**
 * The learner's own traps (companion/SCENES.md, "Own traps"): the wrong choice shown is one they mixed up before, from
 * their mistakes (a fixture mistakes-db in its format), else the file's; and nothing changes for a new learner.
 */
class TrapsTest {
    private val state = Dashboard.parse(TrapsTest::class.java.getResource("/adaptive/state.json")!!.readText())
    private val mistakes = state.mistakes
    private val pages = Grammar.bundled("sl")
    private fun pagesOf(pattern: String) = pages.filter { Grammar.mistakes(it, listOf(MistakeNote(pattern, 0))).isNotEmpty() }.map { it.id }.toSet()
    private val known = setOf("voda", "kava", "kruh", "mleko")
    private val puzzled = DialogReply("Hm? Kako, prosim?", "Hm? Sorry, what?")
    private fun why(c: Confusion) = "You mixed this up before: «${c.example.first}» (right: «${c.example.second}»)."

    private fun turn(vararg choices: DialogChoice) = Dialog("x", listOf(DialogLine(who = "micka", sl = "No?", en = "Well?"), DialogLine(choices = choices.toList())))

    @Test fun `the fixture's mistakes read with their kind, the day they last happened, and the reviews since`() {
        val gen = mistakes.first { it.id == "genitive_after_negation" }
        assertEquals("grammar", gen.category)
        assertEquals("2026-09-26", gen.lastSeen)
        assertFalse("reviewed the same day it happened: not after it", gen.reviewedRight)
        assertTrue(gen.open)
        val register = mistakes.first { it.id == "register_formal_informal" }
        assertTrue("reviewed right three days after", register.reviewedRight)
        assertFalse(register.open)
        assertEquals("listening", mistakes.first { it.id == "listening_se_vidimo" }.subcategory)
    }

    @Test fun `an example's swapped words are its confusions, spelling and hearing are none`() {
        assertEquals(listOf("eno" to "ena", "kavo" to "kava"), Traps.pairs("Lahko ena kava?", "Lahko dobim eno kavo?"))
        assertEquals(listOf("Berlina" to "Berlin"), Traps.pairs("iz Berlin", "iz Berlina"))
        // a lone word swapped, whatever it is
        assertEquals(listOf("moja" to "dobra"), Traps.pairs("To je dobra partnerka.", "To je moja partnerka."))
        assertEquals(listOf("vas" to "te"), Traps.pairs("Jaz te vprašam.", "Jaz vas vprašam. Kako ste?"))
        // "a / b" part by part, the notes in brackets gone
        assertEquals(listOf("sta" to "so", "sva" to "je"), Traps.pairs("Jan in Maja so / Midva je", "Jan in Maja sta / Midva sva"))
        assertEquals(emptyList<Pair<String, String>>(), Traps.pairs("zivjo (to a stranger)", "Dober dan"))
        assertEquals(emptyList<Pair<String, String>>(), Traps.pairs("Moja partnerka imenuje se Maja.", "Moja partnerka se imenuje Maja."))

        val c = Traps.confusions(mistakes)
        assertEquals(setOf("genitive_after_negation", "accusative_feminine_a_to_o", "register_formal_informal"), c.map { it.pattern }.toSet())
        // the most frequent mistake first, its latest example first
        assertEquals(Confusion("vžigalic", "vžigalico", "genitive_after_negation", "nimam vžigalico" to "Nimam vžigalic."), c.first())
        // a confusion keeps the part of its example it is in, without the notes
        val dual = Traps.confusions(listOf(MistakeNote("dual_verb_forms", 1, examples = listOf("Jan in Maja so (twice) / Midva je" to "Jan in Maja sta / Midva sva"))))
        assertEquals(listOf(("sta" to "so") to ("Jan in Maja so" to "Jan in Maja sta"), ("sva" to "je") to ("Midva je" to "Midva sva")), dual.map { (it.right to it.wrong) to it.example })
    }

    @Test fun `their very word, the trap is what they had, in place of the wrong choice of the same rule, with the turn's puzzled reaction`() {
        val d = turn(
            DialogChoice("Nimam vžigalic.", "I have no matches.", ok = true, reply = DialogReply("Tu jih imaš.", "Here you are.")),
            DialogChoice("Nimam vžigalice.", "I have no matches.", why = "Several matches: the genitive plural, vžigalic.", grammar = "stevila-samostalniki"),
            DialogChoice("Nimam vžigalicah.", "I have no matches.", why = "After a negation the genitive: nimam vžigalic.", grammar = "rodilnik-nikalnica", reply = DialogReply("Kje?", "Where?")),
        )
        val t = Traps.dialog(d, Traps.confusions(mistakes), ::pagesOf, { it in known }, puzzled, ::why)
        val choices = t.lines[1].choices
        assertEquals("Nimam vžigalice.", choices[1].sl) // the other rule's wrong choice stays
        val own = choices[2]
        assertEquals("Nimam vžigalico.", own.sl)
        assertEquals("I have no matches.", own.en)
        assertFalse(own.ok)
        assertEquals(puzzled, own.reply)
        assertEquals("rodilnik-nikalnica", own.grammar)
        assertEquals("You mixed this up before: «nimam vžigalico» (right: «Nimam vžigalic.»).", own.why)
        assertEquals(d.lines[1].choices[0], choices[0]) // the right one where it was
        // a turn with its own puzzled reaction gets that one
        val own2 = Traps.dialog(d.copy(lines = listOf(d.lines[0], d.lines[1].copy(puzzled = DialogReply("Kaj?", "What?")))), Traps.confusions(mistakes), ::pagesOf, { false }, puzzled, ::why)
        assertEquals("Kaj?", own2.lines[1].choices[2].reply?.sl)
    }

    @Test fun `their confusion on another word, in a turn of the same rule, when the form it makes is a word`() {
        fun water(grammar: String) = turn(
            DialogChoice("Pijem vodo.", "I drink water.", ok = true),
            DialogChoice("Pijem vode.", "I drink water.", why = "The object: the accusative, vodo.", grammar = grammar),
        )
        val c = Traps.confusions(mistakes)
        val t = Traps.dialog(water("tozilnik"), c, ::pagesOf, { it in known }, puzzled, ::why)
        assertEquals("Pijem voda.", t.lines[1].choices[1].sl)
        // not a word known to be Slovene: no trap made up
        val unknown = water("tozilnik")
        assertSame(unknown, Traps.dialog(unknown, c, ::pagesOf, { false }, puzzled, ::why))
        // another rule's turn: the confusion isn't what it tests
        val other = water("pridevniki-ujemanje")
        assertSame(other, Traps.dialog(other, c, ::pagesOf, { it in known }, puzzled, ::why))
        assertEquals("voda", Traps.ending("vodo", c.first { it.right == "kavo" }))
        assertNull(Traps.ending("mizo", Confusion("vžigalic", "vžigalico", "p", "" to ""))) // nothing swapped at the end: a letter added
        assertNull(Traps.ending("čas", Confusion("kavo", "kava", "p", "" to ""))) // not its ending
    }

    @Test fun `when the file's choices have the learner's confusion already, nothing changes`() {
        val d = turn(
            DialogChoice("Lahko dobim eno kavo?", "Can I get a coffee?", ok = true),
            DialogChoice("Lahko dobim eno kava?", "Can I get a coffee?", why = "The object: the accusative, kavo.", grammar = "tozilnik", reply = DialogReply("Ena kava? Kje?", "One coffee? Where?")),
        )
        assertSame(d, Traps.dialog(d, Traps.confusions(mistakes), ::pagesOf, { it in known }, puzzled, ::why))
    }

    @Test fun `a turn that tests no form, or has two right answers, gets no trap`() {
        val c = Traps.confusions(mistakes)
        val meaning = turn(DialogChoice("Nimam vžigalic.", ok = true), DialogChoice("Dober dan!", why = "A greeting.", grammar = "rodilnik-nikalnica"))
        assertSame(meaning, Traps.dialog(meaning, c, ::pagesOf, { true }, puzzled, ::why))
        val two = turn(DialogChoice("Nimam vžigalic.", ok = true), DialogChoice("Nimam vžigalic!", ok = true), DialogChoice("Nimam vžigalice.", why = "…", grammar = "rodilnik-nikalnica"))
        assertSame(two, Traps.dialog(two, c, ::pagesOf, { true }, puzzled, ::why))
    }

    @Test fun `an exercise's options get the learner's trap in place of a wrong one, the right one where it was`() {
        val c = Traps.confusions(mistakes)
        val options = listOf("kruha", "kruhom", "kruhu")
        assertEquals(listOf("kruha", "kruh", "kruhu"), Traps.options(options, 0, "rodilnik-nikalnica", c, ::pagesOf) { it in known })
        // already there
        val has = listOf("kruh", "kruha", "kruhu")
        assertEquals(has, Traps.options(has, 1, "rodilnik-nikalnica", c, ::pagesOf) { it in known })
        // through the exercise: its options, its mode
        val ex = Exercise.Choice("Danes ni ___. (There is no bread today.)", options, 0)
        val a = Adaptive.choice(ex, "rodilnik-nikalnica", Mastery.LEARNING, canSay = true) { o, k -> Traps.options(o, k, "rodilnik-nikalnica", c, ::pagesOf) { it in known } }
        assertEquals(listOf("kruha", "kruh", "kruhu"), a.exercise.options)
        assertEquals(0, a.exercise.answer)
        assertEquals(TurnMode.CHOOSE, a.mode)
        assertEquals("Danes ni kruha.", a.gap?.sentence)
    }

    @Test fun `nothing changes for a new learner`() {
        val d = turn(
            DialogChoice("Nimam vžigalic.", ok = true),
            DialogChoice("Nimam vžigalice.", why = "…", grammar = "rodilnik-nikalnica"),
        )
        val none = Traps.confusions(emptyList())
        assertTrue(none.isEmpty())
        assertSame(d, Traps.dialog(d, none, ::pagesOf, { true }, puzzled, ::why))
        // every rule new: every turn chosen
        assertEquals(emptyMap<Int, TurnMode>(), Adaptive.modes(d, { Mastery.NEW }, canSay = true))
        val ex = Exercise.Choice("Nimam ___.", listOf("čas", "časa"), 1)
        val a = Adaptive.choice(ex, "rodilnik-nikalnica", Mastery.NEW, canSay = true) { o, k -> Traps.options(o, k, "rodilnik-nikalnica", none, ::pagesOf) { true } }
        assertSame(ex, a.exercise)
        assertEquals(TurnMode.CHOOSE, a.mode)
    }
}
