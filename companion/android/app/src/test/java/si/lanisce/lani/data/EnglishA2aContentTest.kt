package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.Lang

/**
 * English A2, part a (units 1 to 6: commands and the past): the units as the outline has them, their new pages with their
 * own cards, and what grading/en.json gained for them. The content itself is [EnglishCourseContentTest]'s.
 */
class EnglishA2aContentTest {
    private fun en(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.EN).verdict
    private val course = checkNotNull(Courses.bundled("en")) { "the bundled course (companion/course/en)" }
    private val units = listOf("imperatives", "past-regular", "past-irregular", "did-you", "past-continuous", "because-so")

    @Test fun `the part's six units open A2 in the outline's order, each with its pages`() {
        assertEquals(units, course.playable("A2").map { it.id }.take(6))
        val pages = mapOf(
            "imperatives" to listOf("imperatives"),
            "past-regular" to listOf("past-simple"),
            "past-irregular" to listOf("past-simple"),
            "did-you" to listOf("past-simple", "questions-do"),
            "past-continuous" to listOf("past-continuous", "past-simple", "present-continuous"),
            "because-so" to listOf("linking-words", "past-simple"),
        )
        for ((id, names) in pages) assertEquals(id, names, course.units.getValue(id).grammar)
    }

    @Test fun `each new page is in the bundled book with a card of its own, in each base`() {
        val book = Grammar.bundled("en").associateBy { it.id }
        for (id in listOf("imperatives", "past-continuous", "linking-words")) {
            val page = assertNotNull(id, book[id]).let { book.getValue(id) }
            assertEquals(id, "A2", page.level)
            val card = checkNotNull(page.card) { "$id: its own card" }
            for (base in listOf("sl", "de", "it", "en")) assertTrue("$id: the card's rule in $base", card.rule[base].orEmpty().contains("**"))
        }
    }

    @Test fun `a regular past's spelling is grammar, never a slip`() {
        listOf("stoped" to "stopped", "planed" to "planned", "droped" to "dropped", "claped" to "clapped").forEach { (typed, right) -> assertEquals(typed, Verdict.WRONG, en(typed, right)) }
        listOf("openned" to "opened", "visitted" to "visited", "listenned" to "listened", "rainned" to "rained").forEach { (typed, right) -> assertEquals(typed, Verdict.WRONG, en(typed, right)) }
        listOf("carryed" to "carried", "tryed" to "tried", "studyed" to "studied", "staied" to "stayed", "plaied" to "played", "enjoied" to "enjoyed").forEach { (typed, right) -> assertEquals(typed, Verdict.WRONG, en(typed, right)) }
    }

    @Test fun `an irregular past and its base form for each other, or the participle for the past, are wrong`() {
        listOf(
            "came" to "come", "gave" to "give", "made" to "make", "wrote" to "write", "drove" to "drive", "rode" to "ride", "woke" to "wake", "knew" to "know",
            "threw" to "throw", "grew" to "grow", "fell" to "fall", "held" to "hold", "sang" to "sing", "rang" to "ring", "drank" to "drink", "swam" to "swim",
            "began" to "begin", "forgot" to "forget",
        ).forEach { (past, base) ->
            assertEquals("$base for $past", Verdict.WRONG, en(base, past))
            assertEquals("$past for $base", Verdict.WRONG, en(past, base))
        }
        // said too (I sung, I drunk): what people say, almost, the standard form shown (EnglishA2cContentTest)
        listOf("sung" to "sang", "drunk" to "drank", "begun" to "began", "swum" to "swam").forEach { (typed, right) -> assertEquals(typed, Verdict.ALMOST, en(typed, right)) }
        assertEquals(Verdict.WRONG, en("met", "meet"))
        assertEquals(Verdict.WRONG, en("feel", "fell"))
        assertEquals(Verdict.WRONG, en("want", "went"))
        assertEquals(Verdict.WRONG, en("brought", "bought"))
    }

    @Test fun `after did and didn't the past form is wrong in a whole sentence`() {
        assertEquals(Verdict.WRONG, en("Did you came?", "Did you come?"))
        assertEquals(Verdict.WRONG, en("I didn't went.", "I didn't go."))
        assertEquals(Verdict.WRONG, en("She didn't made it.", "She didn't make it."))
        assertEquals(Verdict.WRONG, en("We didn't walked.", "We didn't walk."))
        assertEquals(Verdict.CORRECT, en("I did not go.", "I didn't go."))
        assertEquals(Verdict.ALMOST, en("I didnt go.", "I didn't go."))
    }

    @Test fun `then, than and when are three words`() {
        assertEquals(Verdict.WRONG, en("when", "then"))
        assertEquals(Verdict.WRONG, en("than", "then"))
        assertEquals(Verdict.WRONG, en("then", "when"))
    }

    @Test fun `a slip on any other word stays a slip, and the American spelling stays right`() {
        assertEquals(Verdict.ALMOST, en("hapy", "happy"))
        assertEquals(Verdict.ALMOST, en("becouse", "because"))
        assertEquals(Verdict.ALMOST, en("walkd", "walked"))
        assertEquals(Verdict.ALMOST, en("gote", "gate"))
        assertEquals(Verdict.ALMOST, en("sadle", "saddle"))
        assertEquals(Verdict.CORRECT, en("traveled", "travelled"))
        assertEquals(Verdict.CORRECT, en("Don't run!", "Do not run!"))
    }

    @Test fun `the spoken cos for because is right, with because as the standard form`() {
        val sheets = course.units.getValue("because-so").worksheets.flatMap { it.exercises } + course.units.getValue("because-so").test.exercises
        val items = sheets.flatMap { if (it is Exercise.GapText) Sheets.items(it, Lang.EN) else emptyList() }.filter { it.accept.first().equals("because", ignoreCase = true) }
        assertTrue("a gap whose answer is because", items.isNotEmpty())
        for (item in items) {
            val mark = Sheets.check(item, "cos", Lang.EN)
            assertEquals(Verdict.CORRECT, mark.verdict)
            assertEquals("because", mark.standard?.lowercase())
        }
    }
}
