package si.lanisce.lani.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.Slovene
import si.lanisce.lani.game.TentMove

/** What a right answer said, kept on its grammar page ("Tvoji stavki · Your sentences"): the learner's words, not the prompt's. */
class SaidOfTest {
    private val isSlovene: (String) -> Boolean = Slovene::looks

    @Test fun `a tent question keeps the place picked, not Luka's whole description`() {
        val q = TentMove.exercises(3, canSpeak = true).first() as Exercise.Choice
        assertEquals("ob ribniku", saidOf(q, isSlovene))
    }

    @Test fun `a gap is filled with the answer, without the translation in brackets`() {
        val choice = Exercise.Choice("Otroci so ___ šoli. (The children are at school.)", listOf("v", "na", "iz"), 0, sayOptions = true)
        assertEquals("Otroci so v šoli.", saidOf(choice, isSlovene))
        val cloze = Exercise.Cloze("Babica je ___ kuhinji. (Grandma is in the kitchen.)", listOf("v"))
        assertEquals("Babica je v kuhinji.", saidOf(cloze, isSlovene))
    }

    @Test fun `options in the base language keep the Slovene the exercise asked about`() {
        val recognize = Exercise.Choice("dober dan", listOf("good day", "good night", "thank you"), 0, say = "dober dan")
        assertEquals("dober dan", saidOf(recognize, isSlovene))
        val reorder = Exercise.Reorder("My name is Jan.", listOf("se", "Jan.", "Imenujem"), listOf(listOf("Imenujem", "se", "Jan.")))
        assertEquals("Imenujem se Jan.", saidOf(reorder, isSlovene))
    }
}
