package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine

/**
 * Which of the learner's words a dialog's turns test (companion/SCENES.md, "Your words in the dialogs"): only where a wrong
 * choice differs from the right one in that word, another word or another form of it, or a tap turn's thing; a word that
 * is just there counts for nothing.
 */
class DialogWordsTest {
    private val zlica = MyWord("vocab_v-kuhinji_zlica", "žlica", "spoon", setOf("žlica", "žlice", "žlici", "žlico", "žlic", "žlicama"))
    private val vilice = MyWord("vocab_v-kuhinji_vilice", "vilice", "fork", setOf("vilice", "vilic", "vilicam"))
    private val jajce = MyWord("vocab_trznica_jajce", "jajce", "egg", setOf("jajce", "jajca", "jajcu", "jajc", "jajcem"))
    private val lacen = MyWord("vocab_word_lacen", "lačen", "hungry", setOf("lačen", "lačna", "lačno", "lačni"))
    private val miza = MyWord("vocab_v-kuhinji_miza", "miza", "table", setOf("miza", "mize", "mizi", "mizo"))
    private val words = MyWords(listOf(zlica, vilice, jajce, lacen, miza))

    private fun turn(vararg choices: DialogChoice, grammar: String? = null) = DialogLine(choices = choices.toList(), grammar = grammar)

    @Test fun `a wrong choice tests the words of the right one it differs in, when the rest is shared`() {
        assertEquals(mapOf(1 to 1), DialogWords.tested("Deset jajc, prosim.", "Deset jajce, prosim."))
        assertEquals(mapOf(2 to 2, 3 to 3), DialogWords.tested("Daj mi eno žlico.", "Daj mi dve vilici."))
        // a one-word answer: always
        assertEquals(mapOf(0 to 0), DialogWords.tested("Žlica.", "Vilica."))
        // nothing shared: the choices differ in everything, so no word of it is what the turn is about
        assertEquals(emptyMap<Int, Int?>(), DialogWords.tested("Ja, zelo.", "Dobro jutro!"))
        // three words of four differ: too many
        assertEquals(emptyMap<Int, Int?>(), DialogWords.tested("Daj mi eno žlico.", "Prinesi ji dve vilici."))
        // more words in the wrong one's place: none said instead of it, one by one
        assertEquals(mapOf(0 to null), DialogWords.tested("Žlico, prosim.", "Eno veliko vilico, prosim."))
        // the same sentence: nothing
        assertEquals(emptyMap<Int, Int?>(), DialogWords.tested("Daj mi žlico.", "daj mi žlico!"))
    }

    @Test fun `another word in its place tests the word's meaning, another form of it its form`() {
        val meaning = DialogWords.turn(turn(DialogChoice("Daj mi žlico.", ok = true), DialogChoice("Daj mi vilice.", why = "fork")), words)
        assertNotNull(meaning)
        val t = meaning!!.byRight.getValue(0).single()
        assertEquals(zlica, t.word.card)
        assertEquals("žlico", t.word.said)
        assertEquals(2, t.word.slot)
        assertEquals(mapOf(1 to WordTest.MEANING), t.by)
        // a form the forms list
        val form = DialogWords.turn(turn(DialogChoice("Deset jajc, prosim.", ok = true), DialogChoice("Deset jajce, prosim.", why = "gen. pl.")), words)!!
        assertEquals(mapOf(1 to WordTest.FORM), form.byRight.getValue(0).single().by)
        // a wrong ending the dictionary doesn't list, on the same stem
        val stem = DialogWords.turn(turn(DialogChoice("Deset jajc, prosim.", ok = true), DialogChoice("Deset jajcev, prosim.", why = "?")), words)!!
        assertEquals(mapOf(1 to WordTest.FORM), stem.byRight.getValue(0).single().by)
        // another of the learner's words in its place is its meaning, even on a similar stem
        val other = DialogWords.turn(turn(DialogChoice("Daj mi mizo.", ok = true), DialogChoice("Daj mi žlico.", why = "spoon")), words)!!
        assertEquals(mapOf(1 to WordTest.MEANING), other.byRight.getValue(0).single().by)
    }

    @Test fun `a word that is only there counts for nothing`() {
        // lačen is in the right answer, but the wrong one differs in the greeting: nothing about lačen is asked
        val greeting = turn(DialogChoice("Dober večer, lačen sem.", ok = true), DialogChoice("Dobro jutro, lačen sem.", why = "evening"))
        assertNull(DialogWords.turn(greeting, words))
        // the same word in every choice
        assertNull(DialogWords.turn(turn(DialogChoice("Žlico, prosim.", ok = true), DialogChoice("Žlico, hvala.", why = "?")), words))
        // in someone's line, never: lines aren't turns
        val d = Dialog("d", listOf(DialogLine(who = "micka", sl = "Tu je žlica.", en = "Here is a spoon."), greeting, DialogLine(who = "micka", sl = "Dobro.")))
        assertEquals(emptyMap<Int, TurnWords>(), DialogWords.of(d, words))
        // not one of the learner's words
        assertNull(DialogWords.turn(turn(DialogChoice("Daj mi nož.", ok = true), DialogChoice("Daj mi kruh.", why = "?")), words))
    }

    @Test fun `a turn tests every word its wrong choices differ in, and a wrong choice gets those it tests wrong`() {
        val line = turn(
            DialogChoice("Daj mi eno žlico.", ok = true),
            DialogChoice("Daj mi eno vilice.", why = "fork"),
            DialogChoice("Daj mi eni žlici.", why = "the dual", grammar = "dvojina"),
        )
        val t = DialogWords.turn(line, words)!!
        // žlico, tested by both: by the first for its meaning, by the second for its form
        assertEquals(listOf(zlica), t.answered(0).map { it.card })
        assertEquals(mapOf(1 to WordTest.MEANING, 2 to WordTest.FORM), t.byRight.getValue(0).single().by)
        assertEquals(listOf(zlica to WordTest.MEANING), t.missed(1).map { it.first.card to it.second })
        assertEquals(listOf(zlica to WordTest.FORM), t.missed(2).map { it.first.card to it.second })
    }

    @Test fun `with two right choices a wrong one gets wrong what it tests of the one it nearly copies`() {
        val line = turn(
            DialogChoice("Rad bi žlico.", ok = true),
            DialogChoice("Daj mi žlico.", ok = true),
            DialogChoice("Daj mi vilice.", why = "fork"),
        )
        val t = DialogWords.turn(line, words)!!
        // against "Daj mi žlico." it tests žlico; against "Rad bi žlico." it differs in all three words: tests nothing
        assertEquals(listOf(zlica), t.answered(1).map { it.card })
        assertEquals(emptyList<MyWord>(), t.answered(0).map { it.card })
        assertEquals(listOf(zlica to WordTest.MEANING), t.missed(2).map { it.first.card to it.second })
        // Stari Janez's swarm: "Kdo je to?" was an attempt at "Kaj je to?", not at "Ojoj, čebele!"
        val kaj = MyWord("vocab_word_kaj", "kaj", "what", setOf("kaj", "česa", "čem"))
        val roj = turn(
            DialogChoice("Kaj je to? Toliko čebel!", ok = true),
            DialogChoice("Ojoj, čebele! Ali pičijo?", ok = true),
            DialogChoice("Kdo je to? Toliko čebel!", why = "Kdo asks who", grammar = "vprasalnice"),
        )
        val swarm = DialogWords.turn(roj, MyWords(listOf(kaj)))!!
        assertEquals(listOf(kaj), swarm.missed(2).map { it.first.card })
        assertEquals(listOf(kaj), swarm.answered(0).map { it.card })
        assertEquals(emptyList<MyWord>(), swarm.answered(1).map { it.card })
    }

    @Test fun `a tap turn tests the thing its right place is`() {
        val tap = turn(
            DialogChoice("Pod mizo si!", ok = true, tap = "table"),
            DialogChoice("Za vrati si!", why = "no", tap = "behind-door"),
        )
        val t = DialogWords.turn(tap, words, things = mapOf("table" to miza))!!
        assertEquals(miza, t.thing?.card)
        // a place that isn't one of the learner's words: nothing
        assertNull(DialogWords.turn(tap, words, things = mapOf("door" to miza)))
    }

    @Test fun `a word typed wrong into a gap is its form when it is one of it or looks like one`() {
        assertEquals(WordTest.FORM, DialogWords.typed(jajce, "jajce"))
        assertEquals(WordTest.FORM, DialogWords.typed(jajce, "jajcev"))
        assertEquals(WordTest.MEANING, DialogWords.typed(jajce, "mleko"))
        assertEquals(WordTest.MEANING, DialogWords.typed(jajce, "dve jajci"))
    }

    @Test fun `the dialog's words, each once`() {
        val d = Dialog("d", listOf(
            DialogLine(who = "micka", sl = "Kaj rabiš?"),
            turn(DialogChoice("Daj mi žlico.", ok = true), DialogChoice("Daj mi vilice.", why = "fork")),
            DialogLine(who = "micka", sl = "Še kaj?"),
            turn(DialogChoice("Še eno žlico.", ok = true), DialogChoice("Še eno mizo.", why = "table")),
        ))
        val tested = DialogWords.of(d, words)
        assertEquals(setOf(1, 3), tested.keys)
        assertEquals(listOf(zlica), DialogWords.cards(tested))
        assertTrue(DialogWords.of(d, MyWords.NONE).isEmpty())
    }

    @Test fun `a form of a word the learner has as written comes first`() {
        val sedeti = MyWord("a", "sedeti", "to sit", setOf("sedi", "sedim"))
        val sesti = MyWord("b", "sesti", "to sit down", setOf("sedi", "sedem"))
        val both = MyWords(listOf(sesti, sedeti))
        assertEquals(sesti, both.of("sedi"))
        assertEquals(sedeti, both.of("sedeti"))
        assertTrue(both.isFormOf(sedeti, "sedi"))
        assertNull(both.of("kruh"))
    }
}
