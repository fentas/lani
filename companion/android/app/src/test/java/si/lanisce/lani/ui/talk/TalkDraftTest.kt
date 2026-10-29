package si.lanisce.lani.ui.talk

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.SttWord

/** The talk's input field: spoken or typed, and the unclear words following Jan's edits. */
class TalkDraftTest {
    private fun spoken(text: String, vararg unclear: String) = TalkDraft().apply {
        append(text, unclear.map { w -> text.indexOf(w).let { it until it + w.length } })
    }

    @Test fun `typed text is typed - spoken text stays spoken when a word is fixed`() {
        val d = TalkDraft()
        d.edit(TextFieldValue("Dober dan"))
        assertEquals(Said("Dober dan", emptyList(), typed = true), d.take())

        d.append("rad bi kafo")
        d.edit(TextFieldValue("rad bi kavo"))
        assertEquals(Said("rad bi kavo", emptyList(), typed = false), d.take())

        d.append("napačno")
        d.edit(TextFieldValue("")) // all gone: what comes now is typed
        d.edit(TextFieldValue("Hvala"))
        assertTrue(d.take()!!.typed)
        assertNull(d.take())
    }

    @Test fun `pieces go in after a space - a sentence boundary gets a full stop once`() {
        val d = TalkDraft()
        d.append(" Dober dan ")
        d.append("kako si")
        assertEquals("Dober dan kako si", d.text)
        d.punctuate()
        d.punctuate()
        assertEquals("Dober dan kako si.", d.text)
        d.append("Dobro?")
        d.punctuate()
        assertEquals("Dober dan kako si. Dobro?", d.text)
        assertEquals(TextRange(d.text.length), d.value.selection)
    }

    @Test fun `marks follow the edits around them and go with an edited word`() {
        val d = spoken("Poštal prinaša pismo sosedi.", "Poštal", "pismo")
        assertEquals(listOf("Poštal", "pismo"), d.marks.map { it.word })

        d.edit(TextFieldValue("Naš Poštal prinaša pismo sosedi.")) // before both
        assertEquals(listOf(4, 19), d.marks.map { it.start })
        assertEquals("pismo", d.text.substring(d.marks[1].start, d.marks[1].end))

        d.edit(TextFieldValue("Naš Poštar prinaša pismo sosedi.")) // the word itself
        assertEquals(listOf("pismo"), d.marks.map { it.word })

        d.edit(TextFieldValue("Naš Poštar prinaša pismoo sosedi.")) // typed right against it
        assertTrue(d.marks.isEmpty())
    }

    @Test fun `a cursor put in a marked word picks it - type it selects it`() {
        val d = spoken("Rad bi kafo.", "kafo")
        assertNull(d.picked)
        d.edit(d.value.copy(selection = TextRange(8)))
        assertEquals("kafo", d.picked?.word)
        d.select(d.picked!!)
        assertEquals(TextRange(7, 11), d.value.selection)
        assertNull(d.picked)
        d.edit(TextFieldValue("Rad bi kavo.", TextRange(11)))
        assertTrue(d.marks.isEmpty())
        d.unmark(Unclear(99, 0, 3, "Rad")) // nothing to wave off
        assertEquals("Rad bi kavo.", d.text)
    }

    @Test fun `a word said again takes the marked one's place, cased to fit`() {
        val d = spoken("poštal prinaša pismo.", "poštal", "pismo")
        val (first, second) = d.marks
        assertTrue(d.replace(first.id, " Poštar. "))
        assertEquals("poštar prinaša pismo.", d.text)
        assertEquals(listOf(second.id), d.marks.map { it.id })
        assertTrue(d.replace(second.id, "pisma"))
        assertEquals("poštar prinaša pisma.", d.text)
        assertFalse("gone", d.replace(first.id, "x"))
        assertEquals("Poštar", TalkDraft.fit("Poštal", "poštar"))
        assertEquals("Dva psa", TalkDraft.fit("dvapsa", "Dva psa.")) // several words stay as said
    }

    @Test fun `unclear words - the least sure, one-letter words left out`() {
        val text = "V trgovini kupim kruh in mleko."
        val words = listOf(
            SttWord(" V", prob = 0.01), SttWord(" trgovini", prob = 0.2), SttWord(" kupim", prob = 0.9),
            SttWord(" kruh", prob = 0.25), SttWord(" in", prob = 0.8), SttWord(" mleko.", prob = 0.15),
        )
        val ranges = TalkDraft.unclear(text, words, max = 2)
        assertEquals(listOf("trgovini", "mleko"), ranges.map { text.substring(it) })
        assertTrue(TalkDraft.unclear(text, words, max = 0).isEmpty())
        assertEquals(3, TalkDraft.unclear(text, words, max = 5).size)
    }

    @Test fun `a line the node heard surely may go by itself until it is touched`() {
        val d = TalkDraft()
        d.append("Al ruscello.", sure = true)
        assertTrue(d.sure)
        d.edit(TextFieldValue("Al ruscello!"))
        assertFalse("edited: Jan sends it", d.sure)

        d.clear()
        d.append("Ciao!", sure = true)
        d.append("Come stai?", sure = true)
        assertTrue("every piece sure", d.sure)
        d.append("Bene.", sure = false)
        assertFalse(d.sure)

        d.clear()
        d.append("Poštal je tu.", listOf(0 until 6), sure = true)
        assertFalse("a marked word is never sure", d.sure)

        d.clear()
        d.append("Al ruscello.", sure = true)
        d.hold() // ✋ Keep
        assertFalse(d.sure)
        assertEquals("Al ruscello.", d.text)
    }

    @Test fun `the takes of a turn count until it goes out - the other readings and the unclear words go along`() {
        val d = TalkDraft()
        d.took()
        d.took() // the first take landed nothing
        d.append("Amigo, come stai?", listOf(12 until 16), alternatives = listOf("Amico, come stai?"))
        d.clear() // ✕: said again
        d.took()
        d.append("Amigo, come stai?", listOf(12 until 16), alternatives = listOf("Amico, come stai?"))
        assertEquals(
            Said("Amigo, come stai?", listOf("Amico, come stai?"), typed = false, takes = 3, unclear = listOf("stai")),
            d.take(),
        )
        assertEquals(0, d.takes)
        d.took()
        d.append("Ciao", alternatives = listOf("Chao"))
        d.edit(TextFieldValue("Ciao!")) // Jan's own now: not what was heard, no other readings
        assertEquals(Said("Ciao!", emptyList(), typed = false, takes = 1), d.take())
    }
}
