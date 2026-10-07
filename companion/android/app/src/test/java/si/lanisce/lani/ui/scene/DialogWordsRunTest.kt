package si.lanisce.lani.ui.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.DialogWords
import si.lanisce.lani.game.MyWord
import si.lanisce.lani.game.MyWords
import si.lanisce.lani.game.TurnMode
import si.lanisce.lani.game.WordTest
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.DialogReply

/**
 * A dialog's turns on the learner's own words (companion/SCENES.md, "Your words in the dialogs"): the first answer of a
 * turn on the words it tests, picked, typed, said or tapped; the card of a word got wrong after the turn; the end's list.
 */
class DialogWordsRunTest {
    private val zlica = MyWord("vocab_v-kuhinji_zlica", "žlica", "spoon", setOf("žlica", "žlice", "žlici", "žlico"))
    private val vilice = MyWord("vocab_v-kuhinji_vilice", "vilice", "fork", setOf("vilice", "vilic"))
    private val jajce = MyWord("vocab_trznica_jajce", "jajce", "egg", setOf("jajce", "jajca", "jajc"))
    private val miza = MyWord("vocab_v-kuhinji_miza", "miza", "table", setOf("miza", "mizo"))
    private val words = MyWords(listOf(zlica, vilice, jajce, miza))

    /** Micka: a spoon (meaning), then ten eggs (a form), then a goodbye (none of the learner's words). */
    private val kitchen = Dialog(
        id = "zlica",
        lines = listOf(
            DialogLine(who = "micka", sl = "Kaj ti dam?", en = "What shall I give you?"),
            DialogLine(choices = listOf(
                DialogChoice("Daj mi žlico, prosim.", "A spoon, please.", ok = true, reply = DialogReply("Izvoli žlico.", "Here's a spoon.")),
                DialogChoice("Daj mi vilice, prosim.", "A fork, please.", why = "Soup is eaten with a spoon: žlica.", reply = DialogReply("Vilice? Za juho?", "A fork? For soup?")),
            )),
            DialogLine(who = "micka", sl = "Koliko jajc?", en = "How many eggs?"),
            DialogLine(choices = listOf(
                DialogChoice("Deset jajc, prosim.", "Ten eggs, please.", ok = true),
                DialogChoice("Deset jajce, prosim.", "Ten eggs, please.", why = "From five on, the genitive plural.", grammar = "stevila-samostalniki"),
            )),
            DialogLine(who = "micka", sl = "Adijo!", en = "Bye!"),
            DialogLine(choices = listOf(
                DialogChoice("Nasvidenje!", "Goodbye!", ok = true),
                DialogChoice("Dober dan!", "Hello!", why = "Leaving: nasvidenje."),
            )),
        ),
    )

    /** In the file's order (no seed): choice k is the file's k. */
    private fun start(modes: Map<Int, TurnMode> = emptyMap()) = DialogRun.start(kitchen, "micka", modes = modes, words = DialogWords.of(kitchen, words))

    @Test fun `a right first pick answers the words its turn tests, and only those`() {
        val r = start()
        val right = r.choose(0)
        val a = r.wordsOf(right)
        assertEquals(1, a.size)
        assertEquals(zlica, a.single().card)
        assertTrue(a.single().right)
        assertEquals(WordTest.MEANING, a.single().how)
        assertEquals(false, a.single().produced)
        // the goodbye tests none of the learner's words
        val end = right.next().choose(0).next().choose(0)
        assertEquals(listOf(zlica, jajce), end.wordAnswers.map { it.card })
        assertEquals(DialogRun.Step.END, end.step)
        assertEquals(listOf(true, true), end.wordSummary.map { it.right })
        assertTrue(end.wordCards.isEmpty())
    }

    @Test fun `a wrong first pick gets the word wrong once, and its card shows after the turn`() {
        val r = start()
        val wrong = r.choose(1)
        assertEquals(listOf(Triple(zlica, false, WordTest.MEANING)), r.wordsOf(wrong).map { Triple(it.card, it.right, it.how) })
        assertEquals("Daj mi vilice, prosim.", wrong.wordAnswers.single().said)
        assertEquals("Daj mi žlico, prosim.", wrong.wordAnswers.single().expected)
        // the same wrong one again, and then the right one: nothing more on the word
        assertEquals(wrong, wrong.choose(1))
        val right = wrong.choose(0)
        assertEquals(1, right.wordAnswers.size)
        // its card after the turn: after Micka's reply to the right choice, the last line said then
        val shown = right.wordCards.single()
        assertEquals(zlica, shown.answer.card)
        assertEquals("Izvoli žlico.", right.said[shown.after].sl)
        assertEquals(listOf(false), right.next().choose(0).next().choose(0).wordSummary.take(1).map { it.right })
    }

    @Test fun `a wrong form is a form slip on the word, its rule counts it too`() {
        val r = start().choose(0).next()
        val wrong = r.choose(1)
        val a = r.wordsOf(wrong).single()
        assertEquals(jajce, a.card)
        assertEquals(WordTest.FORM, a.how)
        assertEquals(false, a.right)
        assertEquals(listOf("stevila-samostalniki"), r.answersOf(wrong).map { it.page })
    }

    @Test fun `typed into the gap the word in it is produced, typed wrong its form or its meaning`() {
        val r = start(modes = mapOf(3 to TurnMode.TYPE)).choose(0).next()
        assertEquals(TurnMode.TYPE, r.mode)
        val typed = r.type("jajc")
        val a = r.wordsOf(typed).single()
        assertEquals(jajce, a.card)
        assertTrue(a.right)
        assertTrue(a.produced)
        // another form of it, none of the choices: a form slip
        val slip = r.type("jajca")
        assertEquals(listOf(WordTest.FORM to false), r.wordsOf(slip).map { it.how to it.right })
        // another word: its meaning
        val other = r.type("mleka")
        assertEquals(listOf(WordTest.MEANING), r.wordsOf(other).map { it.how })
        // a wrong choice typed: that choice picked
        assertEquals(listOf(WordTest.FORM), r.wordsOf(r.type("jajce")).map { it.how })
    }

    @Test fun `an echo turn counts nothing on the words`() {
        val r = start(modes = mapOf(1 to TurnMode.ECHO))
        assertEquals(TurnMode.ECHO, r.mode)
        val heard = r.echo()
        assertTrue(r.wordsOf(heard).isEmpty())
    }

    @Test fun `a tap turn's thing is right when its place is tapped, wrong at another`() {
        val hide = Dialog(
            id = "skrij",
            lines = listOf(
                DialogLine(who = "zala", sl = "Kje sem?", en = "Where am I?"),
                DialogLine(choices = listOf(
                    DialogChoice("Pod mizo si!", "You're under the table!", ok = true, tap = "table"),
                    DialogChoice("Za vrati si!", "You're behind the door!", why = "Look again.", tap = "door"),
                )),
                DialogLine(who = "zala", sl = "Bravo!", en = "Well done!"),
            ),
        )
        val r = DialogRun.start(hide, "zala", modes = DialogRun.tapModes(hide), words = DialogWords.of(hide, words, things = mapOf("table" to miza)))
        val right = r.tap("table")
        assertEquals(listOf(Triple(miza, true, WordTest.THING)), r.wordsOf(right).map { Triple(it.card, it.right, it.how) })
        val wrong = r.tap("door")
        assertEquals(listOf(miza to false), r.wordsOf(wrong).map { it.card to it.right })
    }

    @Test fun `without the learner's words a dialog counts none`() {
        val r = DialogRun.start(kitchen, "micka")
        assertTrue(r.choose(0).wordAnswers.isEmpty())
    }
}
