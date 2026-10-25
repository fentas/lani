package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Hint
import si.lanisce.lani.data.Grading.Verdict

class GradingTest {
    private fun check(answer: String, vararg accept: String) = Grading.check(answer, accept.toList())

    @Test fun `case punctuation and spacing are ignored`() =
        assertEquals(Verdict.CORRECT, Grading.match("  imenujem se   jan ", listOf("Imenujem se Jan.")))

    @Test fun `missing diacritics are almost`() {
        val r = check("dober vecer", "Dober večer")
        assertEquals(Verdict.ALMOST, r.verdict)
        assertEquals(Hint.DIACRITICS, r.hint)
        assertEquals("č", r.expected.substring(r.marks.single()))
    }

    // --- typos in the stem are forgiven -------------------------------------------------

    @Test fun `missing letter mid-word is a typo`() {
        val r = check("zivo", "živjo")
        assertEquals(Verdict.ALMOST, r.verdict)
        assertEquals(Hint.TYPO, r.hint)
    }

    @Test fun `swapped letters are a typo`() = assertEquals(Hint.TYPO, check("hvlaa", "hvala").hint)

    @Test fun `typo inside a sentence is a typo`() =
        assertEquals(Verdict.ALMOST, check("Imneujem se Jan", "Imenujem se Jan.").verdict)

    @Test fun `verb ending is grammar, not a typo`() =
        assertEquals(Hint.ENDING, check("Imenujme se Jan", "Imenujem se Jan.").hint)

    @Test fun `typo marks point at the changed letters`() {
        val r = check("hvlaa", "hvala")
        assertEquals("al", r.expected.substring(r.marks.single()))
    }

    // --- endings carry grammar: never forgiven -------------------------------------------

    @Test fun `gender ending is wrong`() {
        val r = check("moj partnerka", "moja partnerka")
        assertEquals(Verdict.WRONG, r.verdict)
        assertEquals(Hint.ENDING, r.hint)
    }

    @Test fun `case ending is wrong`() {
        val r = check("iz Berlin", "iz Berlina")
        assertEquals(Verdict.WRONG, r.verdict)
        assertEquals(Hint.ENDING, r.hint)
        assertEquals("a", r.expected.substring(r.marks.single()))
    }

    @Test fun `neuter adjective ending is wrong`() =
        assertEquals(Verdict.WRONG, check("dober jutro", "Dobro jutro").verdict)

    @Test fun `short words get no typo tolerance`() =
        assertEquals(Verdict.WRONG, check("sen", "sem").verdict)

    @Test fun `two typos are wrong`() = assertEquals(Verdict.WRONG, check("zvio", "živjo").verdict)

    @Test fun `two wrong words are wrong without hint`() {
        val r = check("dobra noc", "Dober dan")
        assertEquals(Verdict.WRONG, r.verdict)
        assertEquals(Hint.NONE, r.hint)
    }

    @Test fun `closest accepted answer is used for feedback`() =
        assertEquals("Kako si?", check("kako sj", "Kako ste?", "Kako si?").expected)

    @Test fun `a picked option is right, almost when it's one of the exercise's near misses, else wrong`() {
        assertEquals(Verdict.CORRECT, Grading.picked(0, answer = 0, almost = listOf(1)))
        assertEquals(Verdict.ALMOST, Grading.picked(1, answer = 0, almost = listOf(1)))
        assertEquals(Verdict.WRONG, Grading.picked(2, answer = 0, almost = listOf(1)))
        assertEquals(Verdict.WRONG, Grading.picked(1, answer = 0))
        val greet = Exercise.Choice("Soseda: »Dober dan!«", listOf("Dober dan!", "Živjo!", "Lahko noč!"), 0, almost = listOf(1))
        assertEquals(listOf(Verdict.CORRECT, Verdict.ALMOST, Verdict.WRONG), greet.options.indices.map(greet::verdict))
    }

    @Test fun `select all, every right one and none else is right, one off almost`() {
        val answers = listOf(0, 2)
        assertEquals(Verdict.CORRECT, Grading.multi(listOf(2, 0), answers))
        assertEquals(Verdict.ALMOST, Grading.multi(listOf(0), answers)) // one missed: clitic-se-basics #1 picked as a single choice
        assertEquals(Verdict.ALMOST, Grading.multi(listOf(0, 1, 2), answers)) // one too many
        assertEquals(Verdict.ALMOST, Grading.multi(listOf(0, 1), answers)) // one missed, one too many
        assertEquals(Verdict.WRONG, Grading.multi(listOf(1, 3), answers))
        assertEquals(Verdict.WRONG, Grading.multi(emptyList(), answers))
    }

    @Test fun `reorder compares case-insensitively`() {
        val sols = listOf(listOf("Tudi", "jaz", "se", "imenujem", "Jan."), listOf("Jaz", "se", "tudi", "imenujem", "Jan."))
        assertTrue(Grading.reorder(listOf("jaz", "se", "Tudi", "imenujem", "Jan."), sols))
        assertFalse(Grading.reorder(listOf("se", "jaz", "Tudi", "imenujem", "Jan."), sols))
    }
}
