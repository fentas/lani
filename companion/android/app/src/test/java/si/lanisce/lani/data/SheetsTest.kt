package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict.ALMOST
import si.lanisce.lani.data.Grading.Verdict.CORRECT
import si.lanisce.lani.data.Grading.Verdict.WRONG

/** The school's worksheet types (companion/COURSE.md): their items, grading, partial credit and what they count on the rules. */
class SheetsTest {
    private val unit = CourseFixture.course.units.getValue("biti")
    private val sort = unit.worksheets[0].exercises[0] as Exercise.Sort
    private val table = unit.worksheets[0].exercises[1] as Exercise.Table
    private val gap = unit.worksheets[1].exercises[0] as Exercise.GapText
    private val transform = unit.worksheets[1].exercises[1] as Exercise.Transform

    @Test fun `a gap text's items - each gap in its own sentence, the others filled`() {
        val items = Sheets.items(gap)
        assertEquals(3, items.size)
        assertEquals("Danes ___ pri babici.", items[0].frame)
        assertEquals("Babica ___ v kuhinji.", items[1].frame)
        assertEquals("Otroci ___ na vrtu.", items[2].frame)
        assertEquals("ona", items[1].hint)
        // a gap's own rule, else the exercise's
        assertEquals(listOf("biti", "biti", "mnozina"), items.map { it.grammar })
        assertEquals("Danes sem pri babici. Babica je v kuhinji. Otroci so na vrtu.", Sheets.filled(gap, gap.gaps.map { it.accept.first() }))
    }

    @Test fun `each gap graded as a cloze - a missing č is almost, a wrong ending wrong`() {
        val item = SheetItem(listOf("ženska"), "spol-samostalnikov")
        assertEquals(CORRECT, Sheets.check(item, "ženska").verdict)
        assertEquals(ALMOST, Sheets.check(item, "zenska").verdict)
        assertEquals(Grading.Hint.DIACRITICS, Sheets.check(item, "zenska").hint)
        assertEquals(WRONG, Sheets.check(item, "ženske").verdict)
        assertEquals(WRONG, Sheets.check(item, "").verdict)
        // a whole line of a transform list
        val line = Sheets.items(transform)[1]
        assertEquals(CORRECT, Sheets.check(line, "babica ni v kuhinji").verdict)
        assertEquals(WRONG, Sheets.check(line, "Babica je v kuhinji.").verdict)
    }

    @Test fun `a table's items are its cells to fill, with the word's forms`() {
        val items = Sheets.items(table)
        assertEquals(3, items.size)
        assertEquals("1. oseba: ___ (množina)", items[0].frame)
        assertEquals(listOf("pres.1pl", "pres.3sg", "pres.3pl"), items.map { it.form })
        assertTrue(items.all { it.lemma == "biti" })
        val marks = listOf(Sheets.check(items[0], "smo"), Sheets.check(items[1], "so"), Sheets.check(items[2], "so"))
        assertEquals(
            listOf(Triple("biti", "pres.1pl", true), Triple("biti", "pres.3sg", false), Triple("biti", "pres.3pl", true)),
            Sheets.forms(items, marks),
        )
    }

    @Test fun `a sort - each word in its column, one not placed is wrong`() {
        assertEquals(4, Sheets.items(sort).size)
        assertEquals(CORRECT, Sheets.sorted(sort, "kruh", 0).verdict)
        assertEquals(WRONG, Sheets.sorted(sort, "miza", 0).verdict)
        assertEquals("ženski", Sheets.sorted(sort, "miza", 0).expected)
        assertEquals(WRONG, Sheets.sorted(sort, "stol", null).verdict)
        // the pool is the same shuffle each time it is shown, every word once
        assertEquals(Sheets.sortWords(sort), Sheets.sortWords(sort))
        assertEquals(setOf("kruh", "stol", "miza", "hiša"), Sheets.sortWords(sort).toSet())
    }

    @Test fun `the word bank - each gap's answer and the distractors, shuffled the same way`() {
        val bank = Sheets.bank(gap)
        assertEquals(listOf("je", "sem", "si", "so"), bank.sorted())
        assertEquals(bank, Sheets.bank(gap))
        assertTrue(Sheets.bank(gap.copy(bank = false)).isEmpty())
    }

    @Test fun `partial credit - an almost counts half, the verdict over the items`() {
        val items = listOf(CORRECT, CORRECT, ALMOST, WRONG)
        assertEquals(2.5, Sheets.score(items), 1e-9)
        assertEquals("2½/4", Sheets.scoreText(items))
        assertEquals("3/3", Sheets.scoreText(listOf(CORRECT, CORRECT, CORRECT)))
        assertEquals(CORRECT, Sheets.verdict(listOf(CORRECT, CORRECT)))
        assertEquals(ALMOST, Sheets.verdict(listOf(CORRECT, CORRECT, CORRECT, ALMOST))) // 3.5 of 4
        assertEquals(ALMOST, Sheets.verdict(List(9) { CORRECT } + WRONG)) // 9 of 10
        assertEquals(WRONG, Sheets.verdict(items)) // 2.5 of 4
        assertEquals(WRONG, Sheets.verdict(emptyList()))
        // a test's pass mark
        assertTrue(Sheets.passed(List(8) { CORRECT } + listOf(WRONG, WRONG), 80))
        assertFalse(Sheets.passed(List(7) { CORRECT } + listOf(ALMOST, WRONG, WRONG), 80)) // 7.5 of 10
        assertTrue(Sheets.passed(List(7) { CORRECT } + listOf(ALMOST, ALMOST, WRONG), 80)) // 8 of 10
        assertFalse(Sheets.passed(emptyList(), 50))
    }

    @Test fun `a right answer after its hint pays like an almost`() {
        val m = ItemMark(CORRECT, "je", "je", hinted = true)
        assertEquals(ALMOST, m.paid)
        assertEquals(WRONG, m.copy(verdict = WRONG).paid)
        assertEquals(CORRECT, m.copy(hinted = false).paid)
    }

    @Test fun `on the rules - one answer a rule, wrong with its first slip, right with a sentence`() {
        val items = Sheets.items(gap)
        val marks = listOf(Sheets.check(items[0], "sem"), Sheets.check(items[1], "so"), Sheets.check(items[2], "so"))
        val rules = Sheets.ruleAnswers(items, marks, sentences = true)
        assertEquals(listOf("biti", "mnozina"), rules.map { it.rule })
        val biti = rules[0]
        assertEquals(WRONG, biti.verdict)
        assertEquals("Babica so v kuhinji.", biti.answer)
        assertEquals("Babica je v kuhinji.", biti.correct)
        assertEquals("Danes sem pri babici.", biti.said)
        val plural = rules[1]
        assertEquals(CORRECT, plural.verdict)
        assertEquals("Otroci so na vrtu.", plural.said)
        // an almost on a rule (a forgotten č) is neither right nor a slip
        val almost = Sheets.ruleAnswers(listOf(SheetItem(listOf("ženska"), "x")), listOf(Sheets.check(SheetItem(listOf("ženska"), "x"), "zenska")), sentences = false)
        assertEquals(ALMOST, almost.single().verdict)
        assertEquals(null, almost.single().said)
    }

    @Test fun `the school's typed types are grammar, a sort vocabulary, each a sheet`() {
        for (e in listOf(table, gap, transform)) {
            assertTrue(Sheets.isSheet(e))
            assertEquals(si.lanisce.lani.game.Res.STONE, si.lanisce.lani.game.GameEngine.resourceOf(e))
        }
        // a word sorted is a tap among the columns, as a plain choice: vocabulary
        assertTrue(Sheets.isSheet(sort))
        assertEquals(si.lanisce.lani.game.Res.FOOD, si.lanisce.lani.game.GameEngine.resourceOf(sort))
        assertFalse(Sheets.isSheet(Exercise.Cloze("a ___", listOf("b"))))
        assertTrue(Sheets.items(Exercise.Cloze("a ___", listOf("b"))).isEmpty())
    }
}
