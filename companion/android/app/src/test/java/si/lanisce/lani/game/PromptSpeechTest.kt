package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Exercise

class PromptSpeechTest {
    private val italian: (String) -> Boolean = { "Il" in it || "Io" in it }

    @Test fun `a choice says its instruction and its question, a gap a pause, never an option`() {
        val e = Exercise.Choice(prompt = "Io ____ a casa.", options = listOf("sono", "ho"), answer = 0, instruction = "Wähle das richtige Wort.")
        val said = PromptSpeech.of(e, italian)
        assertEquals(listOf(HintPiece("Wähle das richtige Wort.", false), HintPiece("Io … a casa.", true)), said)
        assertFalse(said.any { "sono" in it.text })
    }

    @Test fun `a line in both languages is said each half in its own voice`() {
        val e = Exercise.Choice(prompt = "Il cane", options = listOf("a", "b"), answer = 0, instruction = "C'è o ci sono? · Sortiere")
        assertEquals(listOf(HintPiece("C'è o ci sono?", true), HintPiece("Sortiere", false), HintPiece("Il cane", true)), PromptSpeech.of(e, italian))
    }

    @Test fun `a sentence with a gap is the language learned's, even with few words known by it, unless it reads as the base`() {
        val gap = Exercise.Choice(prompt = "Maestra, Lei ____ tedesco? (parlare)", options = listOf("parla", "parli"), answer = 0)
        assertEquals(listOf(HintPiece("Maestra, Lei … tedesco? (parlare)", true)), PromptSpeech.of(gap, { false }, { false }))
        val base = Exercise.Choice(prompt = "Wie heißt ____ auf Italienisch?", options = listOf("a", "b"), answer = 0)
        assertEquals(listOf(HintPiece("Wie heißt … auf Italienisch?", false)), PromptSpeech.of(base, { false }, { "Wie" in it }))
        val plain = Exercise.Choice(prompt = "Wähle.", options = listOf("a", "b"), answer = 0)
        assertEquals("no gap: by the words alone", listOf(HintPiece("Wähle.", false)), PromptSpeech.of(plain, { false }, { false }))
    }

    @Test fun `word chips say their question, never the sentence they build`() {
        val e = Exercise.Reorder(prompt = "Baue den Satz.", tokens = listOf("Il", "gatto", "dorme"), solutions = listOf(listOf("Il", "gatto", "dorme")))
        assertEquals(listOf(HintPiece("Baue den Satz.", false)), PromptSpeech.of(e, italian))
    }

    @Test fun `Markdown marks aren't read, what has no letters isn't said, and a line isn't said twice`() {
        val e = Exercise.Multi(prompt = "**Welche** passen?", options = listOf("a", "b"), answers = listOf(0), instruction = "**Welche** passen?")
        assertEquals(listOf(HintPiece("Welche passen?", false)), PromptSpeech.of(e, italian))
        assertTrue(PromptSpeech.of(Exercise.Choice(prompt = "____", options = listOf("a"), answer = 0), italian).isEmpty())
    }

    @Test fun `a gap text says only its instruction, its text holds the gaps`() {
        val e = Exercise.GapText(text = "Il gatto [1].", gaps = emptyList(), bank = true, instruction = "Setze die Wörter ein.")
        assertEquals(listOf(HintPiece("Setze die Wörter ein.", false)), PromptSpeech.of(e, italian))
    }

    @Test fun `an exercise that isn't a child's says nothing`() {
        assertTrue(PromptSpeech.of(Exercise.Cloze(text = "Il ____", accept = listOf("gatto")), italian).isEmpty())
    }
}
