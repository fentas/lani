package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.Lang

/**
 * German A2's units 7 to 12 (docs/plans/05-built/course-de-a2-b.md), by what [GermanCourseContentTest] doesn't check:
 * their order and pages, the Austrian forms beside the German-German ones, and the known mistakes of grading/de.json
 * that these units brought, each only for a word of its group, a keyboard's slip elsewhere still forgiven.
 */
class GermanA2bContentTest {
    private val units = listOf("muessen-duerfen", "partizip-perfekt", "war-hatte", "reflexiv", "warten-auf", "am-ersten-mai")
    private val course = checkNotNull(Courses.bundled("de"))
    private val pages = Grammar.bundled("de").associateBy { it.id }

    private fun verdict(typed: String, right: String) = Grading.check(typed, listOf(right), Lang.DE).verdict

    /** Every item of [unit] to be typed, the test's too. */
    private fun items(unit: String): List<SheetItem> {
        val u = course.units.getValue(unit)
        return (u.worksheets.flatMap { it.exercises } + u.test.exercises).filter { it !is Exercise.Sort }.flatMap { Sheets.items(it, Lang.DE) }
    }

    @Test fun `the six units are written and in the outline's order, each opening with its page`() {
        val playable = course.playable("A2").map { it.id }
        assertTrue(playable.toString(), playable.containsAll(units))
        assertEquals(units, playable.filter { it in units })
        val first = mapOf("muessen-duerfen" to "modalverben", "partizip-perfekt" to "partizip-zwei", "war-hatte" to "praeteritum-kurz", "reflexiv" to "reflexive-verben", "warten-auf" to "verben-praepositionen", "am-ersten-mai" to "ordinalzahlen")
        for ((unit, page) in first) assertEquals(unit, page, course.units.getValue(unit).grammar.first())
    }

    @Test fun `the new pages are in the book at A2, each with its own card`() {
        for (id in listOf("partizip-zwei", "praeteritum-kurz", "reflexive-verben", "verben-praepositionen", "ordinalzahlen")) {
            val page = checkNotNull(pages[id]) { "$id isn't in the book" }
            assertEquals(id, "A2", page.level)
            assertNotNull("$id has no hand-written card", page.card)
        }
    }

    @Test fun `ich bin gesessen is taught and ich habe gesessen graded right, wherever such a state is typed`() {
        val states = items("partizip-perfekt").filter { i -> i.accept.any { Regex("\\b(gesessen|gestanden|gelegen)\\b").containsMatchIn(it) } }
        assertTrue("the unit asks for the states", states.size >= 4)
        for (item in states) {
            val first = item.accept.first()
            val other = first.replace(Regex("\\bist\\b"), "hat")
            assertTrue("«$first» without «$other» beside it", other in item.accept)
            assertEquals(Verdict.CORRECT, Grading.check(other, item.accept, Lang.DE).verdict)
        }
        // what you put somewhere stays haben, and regular
        assertEquals(Verdict.WRONG, verdict("gestanden", "gestellt"))
        assertEquals(Verdict.WRONG, verdict("gelegen", "gelegt"))
    }

    @Test fun `Jänner and Januar, Feber and Februar are both right where a date is typed in words`() {
        val items = items("am-ersten-mai")
        val jaenner = items.first { i -> i.accept.any { "ersten Jänner" in it } }
        assertEquals(Verdict.CORRECT, Grading.check("am ersten Januar", jaenner.accept, Lang.DE).verdict)
        val feber = items.first { i -> i.accept.any { "einundzwanzigsten Februar" in it } }
        assertEquals(Verdict.CORRECT, Grading.check("am einundzwanzigsten Feber", feber.accept, Lang.DE).verdict)
        assertEquals(Verdict.ALMOST, verdict("Janner", "Jänner"))
        val siebte = items.first { i -> i.accept.any { it == "siebte" } }
        assertEquals(Verdict.CORRECT, Grading.check("siebente", siebte.accept, Lang.DE).verdict)
    }

    @Test fun `a participle for another a letter away is wrong, a slip in one stays one`() {
        for ((typed, right) in listOf("gesessen" to "gegessen", "gegessen" to "gesessen", "bekommen" to "gekommen", "gekommen" to "bekommen", "genommen" to "gekommen", "gewesen" to "gelesen", "gelegen" to "gelesen", "besucht" to "gesucht", "bestellt" to "gestellt", "mitgenommen" to "mitgekommen", "gewisst" to "gewusst", "gekennt" to "gekannt", "aufgewisst" to "aufgewusst")) {
            assertEquals("$typed for $right", Verdict.WRONG, verdict(typed, right))
        }
        for ((typed, right) in listOf("gesesen" to "gesessen", "geholen" to "geholfen", "gwusst" to "gewusst", "vergesen" to "vergessen", "telefonirt" to "telefoniert")) {
            assertEquals("$typed for $right", Verdict.ALMOST, verdict(typed, right))
        }
    }

    @Test fun `a modal's wrong vowel is wrong, in the present and in the short past`() {
        for ((typed, right) in listOf("könne" to "kann", "konn" to "kann", "müsse" to "muss", "wolle" to "will", "wollst" to "willst", "dürfe" to "darf", "möge" to "mag", "musst" to "müsst", "müsst" to "musst", "kannte" to "konnte", "darfte" to "durfte", "willte" to "wollte", "könnte" to "konnte", "müsste" to "musste", "dürfte" to "durfte", "hätte" to "hatte", "hatte" to "hätte", "konnte" to "könnte", "wärst" to "warst", "wären" to "waren", "waren" to "wären")) {
            assertEquals("$typed for $right", Verdict.WRONG, verdict(typed, right))
        }
        for ((typed, right) in listOf("kanst" to "kannst", "duefte" to "durfte")) assertEquals("$typed for $right", Verdict.ALMOST, verdict(typed, right))
        // the umlaut written ue stays almost, as everywhere
        assertEquals(Verdict.ALMOST, verdict("muessen", "müssen"))
    }

    @Test fun `an ordinal's -te for -ste is wrong, auch for euch too, and other slips stay slips`() {
        for ((typed, right) in listOf("zwanzigte" to "zwanzigste", "einundzwanzigten" to "einundzwanzigsten", "dreißigte" to "dreißigste", "neunzehnste" to "neunzehnte", "achtte" to "achte", "ersten" to "erste", "erste" to "ersten", "dem" to "den", "einem" to "einen", "auch" to "euch")) {
            assertEquals("$typed for $right", Verdict.WRONG, verdict(typed, right))
        }
        for ((typed, right) in listOf("zwazigste" to "zwanzigste", "dreissigste" to "dreißigste", "neunzente" to "neunzehnte")) assertEquals("$typed for $right", Verdict.ALMOST, verdict(typed, right))
    }

    @Test fun `what grading already took stays right - hab, and the capital of Sie`() {
        assertEquals(Verdict.CORRECT, verdict("ich hab mich gefreut", "ich habe mich gefreut"))
        assertEquals(Verdict.CORRECT, verdict("setzen sie sich", "Setzen Sie sich!"))
    }
}
