package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import kotlin.random.Random

class HintsTest {
    private val surname = "Moj priimek je Novak."
    private fun sorted(bank: List<List<Char>>) = bank.map { it.sorted().joinToString("") }

    // --- level 1: shape ----------------------------------------------------------------

    @Test fun `shape keeps first letters and punctuation`() =
        assertEquals("M__ p______ j_ N____.", Hints.shape(surname))

    @Test fun `shape keeps diacritics of the first letter and hyphenated parts`() {
        assertEquals("Č______ j_ č______!", Hints.shape("Četrtek je čudovit!"))
        assertEquals("e-p____", Hints.shape("e-pošta"))
    }

    // --- level 2: next word --------------------------------------------------------------

    @Test fun `next word reveals the first word not typed yet`() =
        assertEquals("Moj p______ j_ N____.", Hints.nextWord("", surname))

    @Test fun `next word keeps typed words and reveals the one being typed`() =
        assertEquals("Moj priimek j_ N____.", Hints.nextWord("moj pri", surname))

    @Test fun `next word reveals a wrongly typed word`() =
        assertEquals("Moj priimek j_ N____.", Hints.nextWord("Moj primek", surname))

    @Test fun `the last hidden word is only half revealed`() =
        assertEquals("Moj priimek je No___.", Hints.nextWord("Moj priimek je", surname))

    @Test fun `a word typed without its diacritics isn't done yet`() =
        assertEquals("Četrtek j_ l__.", Hints.nextWord("cetrtek je", "Četrtek je lep."))

    @Test fun `a single word is only half revealed`() {
        assertEquals("čet____", Hints.nextWord("", "četrtek"))
        assertEquals("h_", Hints.nextWord("", "hm"))
    }

    // --- level 3: letter bank ------------------------------------------------------------

    @Test fun `letter bank holds the letters of unfinished words`() =
        assertEquals(listOf("Mjo", "eiikmpr", "ej", "Nakov"), sorted(Hints.letterBank("", surname, Random(1))))

    @Test fun `letter bank leaves out a correctly typed start`() {
        assertEquals(listOf("eikm", "ej", "Nakov"), sorted(Hints.letterBank("Moj pri", surname, Random(1))))
        assertEquals(listOf("ekrt"), sorted(Hints.letterBank("čet", "četrtek", Random(1))))
    }

    @Test fun `letter bank keeps a wrongly started word whole`() =
        assertEquals(listOf("ceekrtt"), sorted(Hints.letterBank("cat", "cetrtek", Random(1))))

    @Test fun `letter bank is shuffled`() {
        val bank = Hints.letterBank("", "priimek", Random(7)).single()
        assertEquals("eiikmpr", bank.sorted().joinToString(""))
        assertFalse(bank.joinToString("") == "priimek")
    }

    @Test fun `levels build on each other`() {
        val accept = listOf(surname)
        assertEquals(Hints.Shown(1, "M__ p______ j_ N____."), Hints.hint(1, "", accept))
        assertEquals("Moj p______ j_ N____.", Hints.hint(2, "", accept)!!.line)
        val l3 = Hints.hint(3, "Moj", accept, Random(3))!!
        assertEquals("Moj priimek j_ N____.", l3.line)
        assertEquals(listOf("eiikmpr", "ej", "Nakov"), sorted(l3.bank))
        assertNull(Hints.hint(0, "", accept))
        assertNull(Hints.hint(1, "", emptyList()))
    }

    // --- which accepted answer ----------------------------------------------------------

    @Test fun `nothing typed hints the first accepted answer`() =
        assertEquals("Kako ste?", Hints.target("", listOf("Kako ste?", "Kako si?")))

    @Test fun `the answer being built is picked by its start`() {
        val accept = listOf("Moj priimek je Novak.", "Pišem se Novak.")
        assertEquals("Pišem se Novak.", Hints.target("Pis", accept))
        assertEquals("Moj priimek je Novak.", Hints.target("Moj p", accept))
        assertEquals("Kako si?", Hints.target("kako si", listOf("Kako ste?", "Kako si?")))
    }

    @Test fun `a hint never shows another accepted answer`() {
        val shown = Hints.hint(2, "Pise", listOf("Moj priimek je Novak.", "Pišem se Novak."))!!
        assertEquals("Pišem s_ N____.", shown.line)
    }

    // --- cost ---------------------------------------------------------------------------

    @Test fun `each hint costs one quality point down to 3`() {
        assertEquals(5, Hints.penalize(5, 0))
        assertEquals(4, Hints.penalize(5, 1))
        assertEquals(3, Hints.penalize(5, 2))
        assertEquals(3, Hints.penalize(5, 3))
        assertEquals(3, Hints.penalize(4, 5))
        assertEquals(3, Hints.penalize(3, 1))
        assertEquals(1, Hints.penalize(1, 2)) // a wrong answer isn't raised
    }

    @Test fun `the village pays a hinted correct answer as almost`() {
        assertEquals(Verdict.ALMOST, Hints.paid(Verdict.CORRECT, 1))
        assertEquals(Verdict.CORRECT, Hints.paid(Verdict.CORRECT, 0))
        assertEquals(Verdict.WRONG, Hints.paid(Verdict.WRONG, 2))
        assertEquals(Verdict.ALMOST, Hints.paid(Verdict.ALMOST, 1))
    }

    @Test fun `review quality with hints`() =
        assertEquals(4, Hints.penalize(ReviewPlanner.quality(ReviewPlanner.Variant.TYPE, Verdict.CORRECT), 1))

    // --- speak: until it can be read aloud ------------------------------------------------

    @Test fun `speak hints reveal the shape, then the words in reading order, then all of it`() {
        assertEquals(5, Hints.spokenLevels(surname))
        assertNull(Hints.spoken(surname, 0))
        assertEquals(
            listOf("M__ p______ j_ N____.", "Moj p______ j_ N____.", "Moj priimek j_ N____.", "Moj priimek je N____.", surname),
            (1..5).map { Hints.spoken(surname, it) },
        )
        assertEquals(surname, Hints.spoken(surname, 9)) // past the last level: still the whole sentence
    }

    @Test fun `every speak hint shows more, and the last shows the whole sentence`() {
        for (say in listOf(surname, "Hvala.", "Dober dan, jaz sem Jan in to je moj pes Rex.", "En kruh, prosim.", "Četrtek je – lep dan!")) {
            val max = Hints.spokenLevels(say)
            val hidden = (1..max).map { l -> Hints.spoken(say, l)!!.count { it == '_' } }
            assertTrue("$say: $hidden", hidden.zipWithNext().all { (a, b) -> b < a })
            assertEquals(say, Hints.spoken(say, max))
        }
    }

    @Test fun `a long sentence takes a few words a tap, at most five taps in all`() {
        val long = "Dober dan, jaz sem Jan in to je moj pes Rex."
        assertEquals(5, Hints.spokenLevels(long))
        assertEquals("Dober dan, jaz s__ J__ i_ t_ j_ m__ p__ R__.", Hints.spoken(long, 2))
        assertEquals(2, Hints.spokenLevels("Hvala."))
        assertEquals("H____.", Hints.spoken("Hvala.", 1))
        assertEquals(3, Hints.spokenLevels("Hvala lepa!"))
    }

    @Test fun `a sentence without words has no speak hints`() {
        assertEquals(0, Hints.spokenLevels(""))
        assertEquals(0, Hints.spokenLevels(" ? "))
        assertNull(Hints.spoken("?", 1))
    }

    // --- reorder --------------------------------------------------------------------------

    private val tiles = listOf("Moj", "priimek", "je", "Novak", "sem")
    private val solutions = listOf(listOf("Moj", "priimek", "je", "Novak"))

    @Test fun `reorder hint places the next right tile`() {
        assertEquals(listOf(0), Hints.nextTile(tiles, emptyList(), solutions))
        assertEquals(listOf(0, 1), Hints.nextTile(tiles, listOf(0), solutions))
    }

    @Test fun `reorder hint takes back a wrong tile first`() =
        assertEquals(listOf(0, 1), Hints.nextTile(tiles, listOf(0, 4, 2), solutions))

    @Test fun `reorder hint never places the last tile`() {
        assertNull(Hints.nextTile(tiles, listOf(0, 1, 2), solutions))
        assertNull(Hints.nextTile(tiles, listOf(0, 1, 2, 3), solutions))
        assertEquals(listOf(0, 1, 2), Hints.nextTile(tiles, listOf(0, 1, 2, 4), solutions))
    }

    @Test fun `reorder hint follows the solution being built`() {
        val t = listOf("Kako", "si", "ti", "?")
        val sols = listOf(listOf("Kako", "si", "ti"), listOf("ti", "si", "Kako"))
        assertEquals(listOf(2, 1), Hints.nextTile(t, listOf(2), sols))
    }

    @Test fun `repeated words use distinct tiles`() =
        assertEquals(listOf(0, 2), Hints.nextTile(listOf("zelo", "dobro", "zelo"), listOf(0), listOf(listOf("zelo", "zelo", "dobro"))))

    // --- suggestions ----------------------------------------------------------------------

    private val vocab = Hints.vocabulary(
        listOf("dober dan", "živjo", "Dobro jutro", "četrtek", "hvala (lepa)", "Kako ste? / Kako si?", "iz Ljubljane / iz Gorice", "dobra", "dobrodošli", "Dober večer"),
    )

    @Test fun `vocabulary splits phrases into distinct words`() {
        assertTrue("dober" in vocab && "lepa" in vocab && "kako" in vocab && "Ljubljane" in vocab)
        assertFalse("Kako" in vocab || "Dobro" in vocab || "Dober" in vocab)
        assertEquals(vocab.map { it.lowercase() }.distinct().size, vocab.size)
        assertFalse(vocab.any { it.length < 2 })
    }

    @Test fun `suggestions ignore case and diacritics`() {
        assertEquals(listOf("živjo"), Hints.suggest("zi", 2, vocab))
        assertEquals(listOf("četrtek"), Hints.suggest("cet", 3, vocab))
        assertEquals(listOf("Živjo"), Hints.suggest("Zi", 2, vocab))
        assertEquals(listOf("Ljubljane"), Hints.suggest("iz lj", 5, vocab))
    }

    @Test fun `suggestions need two letters and stop at four`() {
        assertTrue(Hints.suggest("d", 1, vocab).isEmpty())
        val s = Hints.suggest("do", 2, vocab)
        assertEquals(4, s.size)
        assertEquals(s.distinct(), s)
        assertTrue(s.all { it.startsWith("do") })
        assertEquals(listOf("dober", "dobro", "dobra"), s.take(3)) // shortest first
    }

    @Test fun `suggestions skip the word already typed`() {
        assertFalse("dober" in Hints.suggest("dober", 5, vocab))
        assertEquals(listOf("živjo"), Hints.suggest("zivjo", 5, vocab)) // but offer its spelling
    }

    @Test fun `suggestions use the word at the cursor`() {
        assertEquals(listOf("četrtek"), Hints.suggest("dober cet dan", 9, vocab))
        assertTrue(Hints.suggest("dober cet dan", 13, vocab).isEmpty())
    }

    // --- replacing the word at the cursor --------------------------------------------------

    @Test fun `word at cursor`() {
        assertEquals(6 until 9, Hints.wordAt("dober cet dan", 8))
        assertEquals(6 until 9, Hints.wordAt("dober cet dan", 6))
        assertTrue(Hints.wordAt("dober ", 6).isEmpty())
    }

    @Test fun `replacing the last word adds a space`() =
        assertEquals("dober četrtek " to 14, Hints.replaceWord("dober cet", 9, "četrtek"))

    @Test fun `replacing a word in the middle keeps the rest`() {
        assertEquals("dober četrtek dan" to 14, Hints.replaceWord("dober cet dan", 9, "četrtek"))
        assertEquals("dober četrtek dan" to 14, Hints.replaceWord("dober cetrt dan", 8, "četrtek"))
        assertEquals("živjo!" to 5, Hints.replaceWord("zi!", 2, "živjo"))
    }

    @Test fun `level 2 always adds a letter to a one-word answer but never gives it away`() {
        assertEquals("sv_", Hints.nextWord("s", "sva"))
        assertEquals("sv_", Hints.nextWord("", "sva"))
        assertEquals("svar_", Hints.nextWord("sva", "svari"))
    }
}
