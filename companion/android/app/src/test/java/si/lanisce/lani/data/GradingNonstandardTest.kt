package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Hint
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.Lang
import java.io.File

/**
 * A grading table's `nonstandard` (COURSE.md "Spoken forms", [Spelling.nonstandard]): what people say that writing doesn't want
 * is almost, one way only, with the standard form; every other answer grades as it did, and a table without the key is unchanged.
 */
class GradingNonstandardTest {
    private fun en(answer: String, vararg accept: String, before: String = "") = Grading.check(answer, accept.toList(), Lang.EN, before)
    private fun almost(answer: String, vararg accept: String, before: String = "", standard: String = accept.first()) {
        val r = en(answer, *accept, before = before)
        assertEquals("$answer for ${accept.toList()}", Triple(Verdict.ALMOST, Hint.NONSTANDARD, standard), Triple(r.verdict, r.hint, r.expected))
    }
    private fun wrong(answer: String, vararg accept: String, before: String = "") =
        assertEquals("$answer for ${accept.toList()}", Verdict.WRONG, en(answer, *accept, before = before).verdict)

    private val tables = File("src/main/resources/grading").takeIf { it.isDirectory } ?: File("app/src/main/resources/grading")

    @Test fun `good for well is almost, with well shown, in a gap and in a sentence`() {
        almost("good", "well")
        almost("Granny Maggie sings good.", "Granny Maggie sings well.")
        almost("He plays football good", "He plays football well.")
        almost("she cooks good", "She cooks well.", standard = "She cooks well.")
    }

    @Test fun `only one way - well for good stays wrong, and good for any other word`() {
        wrong("well", "good")
        wrong("He is a well blacksmith.", "He is a good blacksmith.")
        wrong("good", "better")
        wrong("good", "fast")
        wrong("The soup smells well.", "The soup smells good.")
    }

    @Test fun `a flat adverb is almost for its adverb, one way only, each for its own`() {
        for ((flat, adverb) in listOf(
            "quick" to "quickly", "slow" to "slowly", "loud" to "loudly", "quiet" to "quietly", "clear" to "clearly",
            "careful" to "carefully", "bad" to "badly", "beautiful" to "beautifully", "terrible" to "terribly",
        )) {
            almost(flat, adverb)
            wrong(adverb, flat)
        }
        almost("Sam walks slow.", "Sam walks slowly.")
        almost("Mr Tyson drives careful", "Mr Tyson drives carefully.")
        almost("I play bad", "I play badly.")
        wrong("A carefully man", "A careful man")
        wrong("slow", "quickly")
        wrong("bad", "well")
        wrong("happy", "happily")
    }

    @Test fun `a double negative is almost after a negative in the answer's own words`() {
        almost("I didn't see nothing.", "I didn't see anything.")
        almost("I did not see nothing", "I didn't see anything.")
        almost("Sam doesn't know nothing about bees.", "Sam doesn't know anything about bees.")
        almost("I didn't see no one.", "I didn't see anyone.", "I didn't see anybody.")
        almost("I didn't see nobody.", "I didn't see anyone.", "I didn't see anybody.")
        almost("There isn't no-one in the barn.", "There isn't anyone in the barn.")
        almost("Meg didn't go nowhere.", "Meg didn't go anywhere.")
        almost("We haven't got no eggs.", "We haven't got any eggs.")
        almost("I can't see nobody.", "I can't see anyone.")
        almost("We never see no walkers.", "We never see any walkers.")
    }

    @Test fun `a gap's double negative needs the sentence before it`() {
        almost("nothing", "anything", before = "I didn't see ")
        almost("no one", "anyone", "anybody", before = "I can't see ")
        almost("nowhere", "anywhere", before = "Mr Tyson can't find ")
        almost("no", "any", before = "There isn't ")
        // no negative before it: another sentence, not a double negative
        wrong("nothing", "anything", before = "Is there ")
        wrong("no", "any", before = "Have you got ")
        wrong("nothing", "anything")
        wrong("Is there nothing to eat?", "Is there anything to eat?")
        wrong("Have you got no money?", "Have you got any money?")
    }

    @Test fun `each word only for its own - no pair across the group, and the words themselves stay as they were`() {
        wrong("nothing", "anyone", before = "I didn't see ")
        wrong("nowhere", "anything", before = "I didn't see ")
        wrong("no", "anything", before = "I didn't see ")
        wrong("something", "anything", before = "I didn't see ")
        wrong("noone", "anyone", before = "I didn't see ")
        wrong("anything", "nothing", before = "I didn't see ")
    }

    @Test fun `every other answer grades as it did`() {
        assertEquals(Verdict.CORRECT, en("well", "well").verdict)
        assertEquals(Verdict.CORRECT, en("I didn't see anything", "I did not see anything.").verdict)
        val slip = en("usualy", "usually")
        assertEquals(Verdict.ALMOST to Hint.TYPO, slip.verdict to slip.hint)
        val apostrophe = en("I didnt see anything", "I didn't see anything.")
        assertEquals(Verdict.ALMOST to Hint.APOSTROPHE, apostrophe.verdict to apostrophe.hint)
        wrong("wel", "well")
        wrong("goodly", "well")
        val typo = en("goud", "good")
        assertEquals(Verdict.ALMOST to Hint.TYPO, typo.verdict to typo.hint)
    }

    @Test fun `on a sheet - the gap's sentence and a cell's row are its context, no standard label, and an offered form stays wrong`() {
        fun item(frame: String, vararg accept: String, wrong: List<String> = emptyList(), spoken: List<String> = emptyList()) =
            SheetItem(accept.toList(), null, null, null, frame, wrong = wrong, spoken = spoken)
        val gap = Sheets.check(item("I didn't see ___ on the fell.", "anything"), "nothing", Lang.EN)
        assertEquals(Triple(Verdict.ALMOST, Hint.NONSTANDARD, "anything"), Triple(gap.verdict, gap.hint, gap.expected))
        assertNull("the label 'and right too' is a spoken form's", gap.standard)
        assertEquals(Verdict.ALMOST, Sheets.check(item("We haven't got …: ___ (eggs)", "any eggs"), "no eggs", Lang.EN).verdict)
        assertEquals(Verdict.WRONG, Sheets.check(item("Is there ___ to eat?", "anything"), "nothing", Lang.EN).verdict)
        // a form the sheet offers as wrong stays wrong; a unit that offers one is a mistake in the unit
        assertEquals(Verdict.WRONG, Sheets.check(item("She sings ___.", "well", wrong = listOf("good")), "good", Lang.EN).verdict)
        // an item's spoken form stays what it was: right, labelled
        val said = Sheets.check(item("Sam walks ___.", "slowly", spoken = listOf("slow")), "slow", Lang.EN)
        assertEquals(Triple(Verdict.CORRECT, "slowly", Hint.NONE), Triple(said.verdict, said.standard, said.hint))
    }

    @Test fun `a table without the key is unchanged - only Slovene has none now, and it parses as before`() {
        assertTrue(Spelling.of(Lang.SL).nonstandard.isEmpty())
        assertFalse(File(tables, "sl.json").readText().contains("\"nonstandard\""))
        assertTrue("English has its table", Spelling.of(Lang.EN).nonstandard.isNotEmpty())
        // those languages' words for the same thing are simply wrong, or their slips, as before
        assertEquals(Verdict.WRONG, Grading.check("dobro", listOf("lepo"), Lang.SL).verdict)
        assertEquals(Verdict.WRONG, Grading.check("gut", listOf("gern"), Lang.DE).verdict)
        assertEquals(Verdict.WRONG, Grading.check("buono", listOf("bene"), Lang.IT).verdict)
    }

    @Test fun `the key changes nothing else of the table, and a key a build doesn't know is ignored, as an older app ignores this one`() {
        val raw = File(tables, "en.json").readText()
        val with = Spelling.parse(Lang.EN, raw)
        val future = Spelling.parse(Lang.EN, raw.replaceFirst("{", "{\n  \"a_key_of_tomorrow\": [{\"anything\": 1}],"))
        for (t in listOf(with, future)) {
            assertEquals(Spelling.of(Lang.EN).same.size, t.same.size)
            assertEquals(Spelling.of(Lang.EN).loose.size, t.loose.size)
            assertEquals(Spelling.of(Lang.EN).wrong.size, t.wrong.size)
            assertEquals(Hint.APOSTROPHE, t.looseHint)
        }
    }
}
