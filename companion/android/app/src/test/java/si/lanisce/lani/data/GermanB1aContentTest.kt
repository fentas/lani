package si.lanisce.lani.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Hint
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.Lang
import java.io.File

/**
 * German B1's first six units (werden-futur to wegen-trotz: docs/plans/05-built/course-de-b1-a.md) as the app grades them:
 * werden's vowel and wurde against werde and würde, a strong past's vowel, a mixed verb's, -ete and wegen and statt against
 * their neighbours are known mistakes, each only for a word of its group; the genitive's -s is an ending; the spoken forms
 * (wie for als, wann for wenn, von + the dative, wegen dem Regen, the double Perfekt) are right, labelled with the standard form.
 */
class GermanB1aContentTest {
    private fun de(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.DE)
    private fun assertGraded(verdict: Verdict, hint: Hint, r: Grading.Result) {
        assertEquals("verdict of «${r.expected}»", verdict, r.verdict)
        assertEquals("hint of «${r.expected}»", hint, r.hint)
    }

    /** A sheet's item with [accept] and [spoken]: what the sheet makes of [answer]. */
    private fun sheet(answer: String, accept: List<String>, spoken: List<String> = emptyList()) =
        Sheets.check(SheetItem(accept, null, null, null, "", spoken = spoken), answer, Lang.DE)

    private fun wrong(answer: String, right: String) = assertEquals("«$answer» for «$right»", Verdict.WRONG, de(answer, right).verdict)

    @Test fun `werden's vowel and its past are known mistakes, each way`() {
        wrong("werd", "wird")
        wrong("werst", "wirst")
        wrong("wirdst", "wirst")
        wrong("wirde", "werde")
        wrong("wirden", "werden")
        wrong("wurde", "werde")
        wrong("werde", "wurde")
        wrong("wurden", "werden")
        wrong("würde", "wurde")
        wrong("wurde", "würde")
        // the person's ending stays the ending
        assertGraded(Verdict.WRONG, Hint.ENDING, de("wirst", "wird"))
        assertGraded(Verdict.WRONG, Hint.ENDING, de("wird", "wirst"))
    }

    @Test fun `a strong past with the present's vowel is a mistake, and the other way`() {
        wrong("fahr", "fuhr")
        wrong("fahren", "fuhren")
        wrong("nehm", "nahm")
        wrong("find", "fand")
        wrong("sing", "sang")
        wrong("trink", "trank")
        wrong("sprech", "sprach")
        wrong("gang", "ging")
        wrong("fuhr", "fahr")
        wrong("fanden", "finden")
        // the regular -te on a strong or mixed verb, and liegen's own
        wrong("fahrte", "fuhr")
        wrong("kommte", "kam")
        wrong("bringte", "brachte")
        wrong("denkte", "dachte")
        wrong("liegte", "lag")
        // a slip that isn't the vowel stays one
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("brahte", "brachte"))
    }

    @Test fun `the past against would, a mixed verb's vowel and -ete are mistakes, not slips`() {
        wrong("dächte", "dachte")
        wrong("dachte", "dächte")
        wrong("brächte", "brachte")
        wrong("wüsste", "wusste")
        wrong("kämen", "kamen")
        wrong("kamen", "kämen")
        wrong("kennte", "kannte")
        wrong("nennte", "nannte")
        wrong("wisste", "wusste")
        wrong("konnte", "kannte")
        wrong("wartte", "wartete")
        wrong("arbeitte", "arbeitete")
        wrong("öffnte", "öffnete")
        wrong("regnte", "regnete")
        // what a keyboard lacks stays almost
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("sass", "saß"))
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("ass", "aß"))
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("waehrend", "während"))
        assertGraded(Verdict.ALMOST, Hint.UMLAUT, de("ausserhalb", "außerhalb"))
    }

    @Test fun `the genitive's ending is the ending, and the article and the noun go together`() {
        assertGraded(Verdict.WRONG, Hint.ENDING, de("des Vater", "des Vaters"))
        assertGraded(Verdict.WRONG, Hint.ENDING, de("meines Vater", "meines Vaters"))
        assertGraded(Verdict.WRONG, Hint.ENDING, de("der Mutters", "der Mutter"))
        assertGraded(Verdict.WRONG, Hint.ENDING, de("Stefan", "Stefans"))
        assertEquals(Verdict.WRONG, de("des Haus", "des Hauses").verdict)
        assertEquals(Verdict.WRONG, de("der Vaters", "des Vaters").verdict)
        assertEquals(Verdict.WRONG, de("meinem Vaters", "meines Vaters").verdict)
        assertEquals(Verdict.WRONG, de("meiner Vaters", "meines Vaters").verdict)
        // both are right where both are listed
        assertEquals(Verdict.CORRECT, de("des Hunds", "des Hundes", "des Hunds").verdict)
        assertEquals(Verdict.CORRECT, de("ihr hattet gesessen", "ihr wart gesessen", "ihr hattet gesessen").verdict)
    }

    @Test fun `wegen and statt against the words one letter away are mistakes, and a slip elsewhere stays one`() {
        wrong("gegen", "wegen")
        wrong("wegen", "gegen")
        wrong("regen", "wegen")
        wrong("Stadt", "statt")
        wrong("statt", "Stadt")
        wrong("satt", "statt")
        wrong("seid", "seit")
        wrong("seit", "seid")
        wrong("vor", "bevor")
        wrong("wirt", "wird")
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("Scnee", "Schnee"))
        assertGraded(Verdict.ALMOST, Hint.TYPO, de("wahrend", "während"))
    }

    @Test fun `a spoken form of these units is right, labelled with the standard one, and only where it is spoken`() {
        fun spoken(answer: String, accept: String) {
            val r = sheet(answer, listOf(accept), listOf(answer))
            assertEquals("«$answer»", Verdict.CORRECT to accept, r.verdict to r.standard)
        }
        spoken("wie", "als")
        spoken("wann", "wenn")
        spoken("von meinem Vater", "meines Vaters")
        spoken("wegen dem Regen", "wegen des Regens")
        spoken("trotz dem Schnee", "trotz des Schnees")
        spoken("während dem Essen", "während des Essens")
        spoken("statt dem Wein", "statt des Weins")
        spoken("Der Bus ist abgefahren gewesen.", "Der Bus war abgefahren.")
        spoken("Nachdem ich gegessen habe, bin ich spazieren gegangen.", "Nachdem ich gegessen hatte, ging ich spazieren.")
        // not spoken forms: the mistake of an article with the genitive's noun, and als or wenn for each other
        assertEquals(Verdict.WRONG, sheet("wegen dem Regens", listOf("wegen des Regens"), listOf("wegen dem Regen")).verdict)
        assertEquals(Verdict.WRONG, sheet("wenn", listOf("als"), listOf("wie")).verdict)
        assertEquals(Verdict.WRONG, sheet("als", listOf("wenn"), listOf("wann")).verdict)
    }

    @Test fun `the units are at the level's size, and the six new pages are in the book with their own cards`() {
        val course = checkNotNull(Courses.bundled("de"))
        val ids = listOf("werden-futur", "praeteritum", "als-waehrend", "plusquamperfekt", "genitiv", "wegen-trotz")
        val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "course/de/course.json").isFile }
        val json = Json { ignoreUnknownKeys = true }
        for (id in ids) {
            val u = course.units.getValue(id)
            assertEquals("$id: its level", "B1", u.level)
            assertEquals("$id: three sheets", 3, u.worksheets.size)
            assertTrue("$id: five steps or more", u.intro!!.steps.size >= 5)
            assertTrue("$id: a quick question a step", u.intro!!.steps.all { it.ask != null })
            val raw = json.parseToJsonElement(File(companion, "course/de/units/$id.json").readText()).jsonObject
            val items = raw.getValue("worksheets").jsonArray.flatMap { it.jsonObject.getValue("exercises").jsonArray }.sumOf { items(it.jsonObject) }
            assertTrue("$id: $items items on its sheets, at most 35", items <= 35)
            val test = raw.getValue("test").jsonObject.getValue("exercises").jsonArray.sumOf { items(it.jsonObject) }
            assertTrue("$id: $test items in its test, 8 to 15", test in 8..15)
        }
        val book = Grammar.bundled("de").associateBy { it.id }
        for (p in listOf("werden-futur", "praeteritum", "temporalsaetze", "plusquamperfekt", "genitiv", "praepositionen-genitiv")) {
            assertTrue("$p: its page", book.containsKey(p))
            assertTrue("$p: its own card", File(companion, "grammar/de/$p.json").readText().contains("\"card\""))
        }
    }

    /** One exercise's items, as COURSE.md counts them: a gap, a cell to fill, a line, a word sorted, or the exercise itself. */
    private fun items(e: JsonObject): Int = when (e.getValue("type").jsonPrimitive.content) {
        "gaptext" -> e.getValue("gaps").jsonArray.size
        "table" -> e.getValue("rows").jsonArray.sumOf { r -> r.jsonObject.getValue("cells").jsonArray.count { it is JsonObject } }
        "transform" -> e.getValue("items").jsonArray.size
        "sort" -> e.getValue("columns").jsonArray.sumOf { c -> (c.jsonObject.getValue("words") as JsonArray).size }
        else -> 1
    }
}
