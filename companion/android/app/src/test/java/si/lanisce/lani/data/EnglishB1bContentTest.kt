package si.lanisce.lani.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Hint
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.Lang
import java.io.File

/**
 * English B1's units 7-12 (docs/plans/05-built/course-en-b1-b.md): relative clauses, the passive, verb patterns, phrasal verbs,
 * too and enough, myself and each other, with their six new pages and what grading/en.json gained for them. What every unit of the
 * course must do is [EnglishCourseContentTest]'s; this is what these six add: what people say is never a wrong choice and never
 * "never" in a text, and typed it is almost or, where the unit lists it, right.
 */
class EnglishB1bContentTest {
    private val units = listOf("who-which", "passive", "verb-patterns", "phrasal-verbs", "too-enough", "myself")
    private val pages = mapOf(
        "who-which" to listOf("relative-clauses"), "passive" to listOf("passive", "past-simple"), "verb-patterns" to listOf("verb-patterns", "would-like"),
        "phrasal-verbs" to listOf("phrasal-verbs", "object-pronouns"), "too-enough" to listOf("too-enough", "comparatives"), "myself" to listOf("reflexive-pronouns", "object-pronouns"),
    )
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "course/en/course.json").isFile }
    private val json = Json { ignoreUnknownKeys = true }
    private val course = checkNotNull(Courses.bundled("en"))

    private fun en(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.EN)
    private fun items(unit: String): List<SheetItem> {
        val u = course.units.getValue(unit)
        return (u.worksheets.flatMap { it.exercises } + u.test.exercises).filter { it !is Exercise.Sort }.flatMap { Sheets.items(it, Lang.EN) }
    }
    private fun size(e: Exercise) = when (e) {
        is Exercise.Sort -> e.columns.sumOf { it.words.size }
        is Exercise.GapText, is Exercise.Table, is Exercise.Transform -> Sheets.items(e, Lang.EN).size
        else -> 1
    }

    @Test fun `units 7 to 12 of B1 are written in the outline's order, at the size of the level, naming their pages`() {
        assertEquals(units, course.refs("B1").subList(6, 12).map { it.id })
        val book = Grammar.bundled("en").associateBy { it.id }
        val outline = json.parseToJsonElement(File(companion, "course/en/course.json").readText()).jsonObject["levels"]!!.jsonArray
            .map { it.jsonObject }.first { it["level"]!!.jsonPrimitive.content == "B1" }["units"]!!.jsonArray.map { it.jsonObject }.associateBy { it["id"]!!.jsonPrimitive.content }
        for (id in units) {
            val u = course.units.getValue(id)
            assertEquals(id, "B1", u.level)
            assertTrue("$id: ${u.intro?.steps?.size} steps", u.intro?.steps?.size in 5..6)
            assertEquals("$id: worksheets", 3, u.worksheets.size)
            assertEquals("$id: pass", 80, u.test.pass)
            assertTrue("$id: flagged for a native check", u.machineWritten && u.review.orEmpty().contains("native speaker"))
            val sheets = u.worksheets.flatMap { it.exercises }.sumOf(::size)
            assertTrue("$id: $sheets items on the sheets", sheets in 28..35)
            assertTrue("$id: ${u.test.exercises.sumOf(::size)} items in the test", u.test.exercises.sumOf(::size) in 10..15)
            assertEquals(id, pages[id], u.grammar)
            assertTrue("$id is written", "planned" !in outline.getValue(id))
            assertEquals(id, pages[id], outline.getValue(id)["grammar"]!!.jsonArray.map { it.jsonPrimitive.content })
            u.grammar.forEach { assertNotNull("$id names $it", book[it]) }
        }
    }

    @Test fun `each new page has its own card in every base, at B1, within the page size`() {
        for (id in listOf("relative-clauses", "passive", "verb-patterns", "phrasal-verbs", "too-enough", "reflexive-pronouns")) {
            val page = json.parseToJsonElement(File(companion, "grammar/en/$id.json").readText()).jsonObject
            assertEquals(id, "B1", page["level"]!!.jsonPrimitive.content)
            page["rule"]!!.jsonObject.forEach { (b, t) -> assertTrue("$id rule in $b", t.jsonPrimitive.content.length <= 1200) }
            val card = page["card"]!!.jsonObject
            for (key in listOf("title", "rule", "tip")) assertTrue("$id card's $key", (card[key] as JsonObject).keys.containsAll(listOf("en", "sl", "de", "it")))
            assertTrue("$id: two examples on its card", card["examples"]!!.jsonArray.size >= 2)
            assertNotNull("$id: the app reads its card", Grammar.bundled("en").first { it.id == id }.card)
        }
    }

    @Test fun `what people say is almost with the standard form shown`() {
        for ((typed, right) in listOf(
            "The man what lives next door is a blacksmith." to "The man who lives next door is a blacksmith.",
            "That's the bus what goes to Keswick." to "That's the bus that goes to Keswick.",
            "The window was broke." to "The window was broken.", "The letter was wrote by Edna." to "The letter was written by Edna.",
            "It was took to Keswick." to "It was taken to Keswick.", "My bike was stole." to "My bike was stolen.",
            "He did it hisself." to "He did it himself.", "They did it theirselves." to "They did it themselves.", "They did it themself." to "They did it themselves.",
            "I did it meself." to "I did it myself.",
        )) {
            val r = en(typed, right)
            assertEquals(typed, Verdict.ALMOST to Hint.NONSTANDARD, r.verdict to r.hint)
            assertEquals(typed, right, r.expected)
        }
        val gap = SheetItem(accept = listOf("who", "that"), grammar = null, frame = "The man ${Sheets.BLANK} lives next door.")
        assertEquals(Verdict.ALMOST, Sheets.check(gap, "what", Lang.EN).verdict)
    }

    @Test fun `those forms are one way only, and need what stands before them`() {
        for ((typed, right) in listOf(
            "broke" to "broken", "took" to "taken", "wrote" to "written", "what" to "who", "What is that?" to "Who is that?", "Hello, what is that?" to "Hello, who is that?",
            "broken" to "broke", "The bus who goes to Keswick." to "The bus that goes to Keswick.",
        )) assertEquals("$typed for $right", Verdict.WRONG, en(typed, right).verdict)
        val cell = Sheets.items(course.units.getValue("passive").worksheets.first().exercises.first(), Lang.EN).first { it.accept == listOf("written") }
        assertEquals("the table's own cell, no be before it", Verdict.WRONG, Sheets.check(cell, "wrote", Lang.EN).verdict)
    }

    @Test fun `what the units teach as a mistake stays wrong, never a slip`() {
        for ((typed, right) in listOf(
            "ourselfs" to "ourselves", "yourselfs" to "yourselves", "themselfs" to "themselves", "yourself" to "yourselves", "yourselves" to "yourself",
            "himself" to "herself", "who's" to "whose", "who" to "whose", "whose" to "who", "one" to "ones", "ones" to "one", "own" to "one",
            "took" to "look", "look" to "took", "too" to "to", "to" to "too", "than" to "as", "build" to "built", "built" to "build",
            "Turn off it." to "Turn it off.", "Look them after." to "Look after them.", "I want that you go." to "I want you to go.",
            "I enjoy to walk." to "I enjoy walking.", "The water is enough warm." to "The water is warm enough.", "It's too cold for swim." to "It's too cold to swim.",
            "This is the book I read it." to "This is the book I read.", "I feel myself tired." to "I feel tired.",
        )) assertEquals("$typed for $right", Verdict.WRONG, en(typed, right).verdict)
    }

    @Test fun `the spoken forms are right, labelled with the standard form`() {
        fun said(unit: String, typed: String, standard: String) {
            val found = items(unit).filter { typed in it.spoken }
            assertTrue("$unit: $typed is listed", found.isNotEmpty())
            for (i in found) assertEquals(unit, Verdict.CORRECT to standard, Sheets.check(i, typed, Lang.EN).let { it.verdict to it.standard })
        }
        said("passive", "got", "was")
        said("passive", "My bike got stolen.", "My bike was stolen.")
        said("verb-patterns", "and come", "to come")
        said("too-enough", "right", "very")
        said("too-enough", "dead", "very")
        said("myself", "me", "myself")
    }

    @Test fun `a noun or a pronoun takes the place it takes, each right answer listed`() {
        val lines = items("phrasal-verbs")
        assertEquals(Verdict.CORRECT, Sheets.check(lines.first { "Turn it off." in it.accept }, "Turn it off.", Lang.EN).verdict)
        assertEquals(Verdict.WRONG, Sheets.check(lines.first { "Turn it off." in it.accept }, "Turn off it.", Lang.EN).verdict)
        val who = items("who-which")
        val cheese = who.first { "This is the cheese Arthur likes." in it.accept }
        for (a in listOf("This is the cheese Arthur likes.", "This is the cheese that Arthur likes.", "This is the cheese which Arthur likes.")) assertEquals(a, Verdict.CORRECT, Sheets.check(cheese, a, Lang.EN).verdict)
        val box = items("too-enough").first { "The box is too heavy to lift." in it.accept }
        for (a in listOf("The box is too heavy to lift.", "The box is too heavy for me to lift.")) assertEquals(a, Verdict.CORRECT, Sheets.check(box, a, Lang.EN).verdict)
    }

    @Test fun `no choice and no word bank offers what people say as the wrong form`() {
        for (id in units) {
            val u = course.units.getValue(id)
            val all = u.worksheets.flatMap { it.exercises } + u.test.exercises
            val choices = all.filterIsInstance<Exercise.Choice>() + u.intro?.steps.orEmpty().mapNotNull { it.ask as? Exercise.Choice }
            for (c in choices) {
                val right = c.options[c.answer]
                c.options.forEachIndexed { i, o ->
                    if (i != c.answer && i !in c.almost) {
                        val (typed, whole) = if (Sheets.GAP.containsMatchIn(c.prompt)) c.prompt.replace(Sheets.GAP, o) to c.prompt.replace(Sheets.GAP, right) else o to right
                        assertTrue("$id: «$typed» offered as wrong beside «$whole»", Grading.check(typed, listOf(whole), Lang.EN).hint != Hint.NONSTANDARD)
                    }
                }
            }
            for (e in all.filterIsInstance<Exercise.GapText>()) for (d in e.distractors) for (item in Sheets.items(e, Lang.EN)) {
                assertTrue("$id: the distractor «$d» in ${item.frame}", Grading.check(d, item.accept, Lang.EN, item.frame.substringBefore(Sheets.BLANK, "")).hint != Hint.NONSTANDARD)
                assertTrue("$id: the distractor «$d» is said in ${item.frame}", d !in item.spoken)
            }
        }
    }

    @Test fun `no text of the six says never of a form people say, and each names it`() {
        val said = Regex("""\b(?:never|not|nie|nicht|nikoli|ne|mai|non)\s+\*[^*]*(?:\bwhat\b|was broke|was took|hisself|theirselves|meself|off of|right cold|try and)[^*]*\*""", RegexOption.IGNORE_CASE)
        fun says(unit: String) = json.parseToJsonElement(File(companion, "course/en/units/$unit.json").readText()).jsonObject["intro"]!!.jsonObject["steps"]!!.jsonArray
            .flatMap { s -> s.jsonObject["say"]!!.jsonObject.values.map { it.jsonPrimitive.content } }
        fun rule(page: String) = json.parseToJsonElement(File(companion, "grammar/en/$page.json").readText()).jsonObject["rule"]!!.jsonObject.values.map { it.jsonPrimitive.content }
        val texts = units.flatMap(::says) + listOf("relative-clauses", "passive", "verb-patterns", "phrasal-verbs", "too-enough", "reflexive-pronouns").flatMap(::rule)
        assertEquals(emptyList<String>(), texts.filter { said.containsMatchIn(it) })
        for ((unit, form) in listOf("who-which" to "the man what lives", "passive" to "It was broke", "phrasal-verbs" to "get off of", "myself" to "hisself", "too-enough" to "right cold", "verb-patterns" to "try and come")) {
            assertEquals("$unit names $form in each base", 4, says(unit).count { form in it })
        }
    }

    @Test fun `no gap of the six forgets an answer that is plainly right`() {
        fun takes(unit: String, frame: String, vararg typed: String) {
            val item = items(unit).first { frame in it.frame }
            for (t in typed) assertEquals("$frame: $t", Verdict.CORRECT, Sheets.check(item, t, Lang.EN).verdict)
        }
        takes("who-which", "Mr Dixon is the man", "who", "that")
        takes("who-which", "only dog", "that", "which", "who")
        takes("too-enough", "We haven't got", "enough", "any", "many")
        takes("too-enough", "He says it's", "very", "really", "quite", "pretty", "a bit")
        takes("myself", "known", "each other", "one another")
        takes("phrasal-verbs", "at six (left)", "set off", "set out")
    }
}
