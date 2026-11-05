package si.lanisce.lani.ui.scene

import org.junit.Assert.assertEquals
import org.junit.Test
import si.lanisce.lani.data.Clips
import si.lanisce.lani.game.scene.Dialog
import si.lanisce.lani.game.scene.DialogChoice
import si.lanisce.lani.game.scene.DialogLine
import si.lanisce.lani.game.scene.DialogReply

class DialogAheadTest {
    private val voices = mapOf("micka" to Spoken("grandma", "female", "micka"), "janez" to Spoken("grandpa", "male", "janez"))
    private fun lines(d: Dialog) = DialogAhead.lines(d, "micka") { voices.getValue(it) }

    @Test fun `each line in its speaker's voice, in play order`() {
        val l = lines(Dialog("d", listOf(DialogLine(who = "micka", sl = "Dober dan, Jan!"), DialogLine(who = "janez", sl = "Živjo!"))))
        assertEquals(listOf("Dober dan, Jan!", "Živjo!"), l.map { it.text })
        assertEquals(listOf("grandma", "grandpa"), l.map { it.voice })
        assertEquals("micka", l[0].person)
    }

    @Test fun `at a turn the right answer in the learner's voice, then the reply to it, never a wrong answer's`() {
        val d = Dialog("d", listOf(
            DialogLine(who = "janez", sl = "Kako si?"),
            DialogLine(choices = listOf(
                DialogChoice("Slabo jaz.", reply = DialogReply("Kaj?")),
                DialogChoice("Dobro, hvala.", ok = true, reply = DialogReply("Lepo.")),
            )),
            DialogLine(who = "micka", sl = "Pa še ti?"),
        ))
        val l = lines(d)
        assertEquals(listOf("Kako si?", "Dobro, hvala.", "Lepo.", "Pa še ti?"), l.map { it.text })
        assertEquals(listOf("grandpa", Clips.PLAYER, "grandpa", "grandma"), l.map { it.voice })
    }

    @Test fun `a reply goes to whom the turn names`() {
        val d = Dialog("d", listOf(
            DialogLine(who = "micka", sl = "Povej mu."),
            DialogLine(to = "janez", choices = listOf(DialogChoice("Dober dan.", ok = true, reply = DialogReply("Živjo.")))),
        ))
        assertEquals("grandpa", lines(d).last().voice)
    }

    @Test fun `no text is nothing to get ready`() {
        assertEquals(emptyList<String>(), lines(Dialog("d", listOf(DialogLine(who = "micka")))).map { it.text })
    }
}
