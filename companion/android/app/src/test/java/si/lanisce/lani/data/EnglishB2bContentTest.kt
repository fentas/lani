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
import si.lanisce.lani.game.Forms
import si.lanisce.lani.l10n.Lang
import java.io.File

/**
 * English B2's units 7-12 (docs/plans/05-built/course-en-b2-b.md): clauses with commas, participle clauses, the passive in every
 * tense, have it done, reporting verbs, both and neither, with their six new pages and what grading/en.json gained for them. What
 * every unit of the course must do is [EnglishCourseContentTest]'s; this is what these six add: what people say is never a wrong
 * choice and never "never" in a text, and typed it is almost or, where the unit lists it, right.
 */
class EnglishB2bContentTest {
    private val units = listOf("non-defining", "participle-clauses", "passive-more", "have-it-done", "reporting-verbs", "both-neither")
    private val newPages = listOf("relative-clauses-more", "participle-clauses", "passive-more", "have-it-done", "reporting-verbs", "quantifiers")
    private val pages = mapOf(
        "non-defining" to listOf("relative-clauses-more", "relative-clauses"), "participle-clauses" to listOf("participle-clauses", "relative-clauses"),
        "passive-more" to listOf("passive-more", "passive"), "have-it-done" to listOf("have-it-done", "passive"),
        "reporting-verbs" to listOf("reporting-verbs", "reported-speech", "verb-patterns"), "both-neither" to listOf("quantifiers", "some-any"),
    )
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "course/en/course.json").isFile }
    private val json = Json { ignoreUnknownKeys = true }
    private val course = checkNotNull(Courses.bundled("en"))

    private fun en(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.EN)
    private fun items(unit: String): List<SheetItem> {
        val u = course.units.getValue(unit)
        return (u.worksheets.flatMap { it.exercises } + u.test.exercises).filter { it !is Exercise.Sort && it !is Exercise.Choice }.flatMap { Sheets.items(it, Lang.EN) }
    }
    private fun size(e: Exercise) = when (e) {
        is Exercise.Sort -> e.columns.sumOf { it.words.size }
        is Exercise.GapText, is Exercise.Table, is Exercise.Transform -> Sheets.items(e, Lang.EN).size
        else -> 1
    }

    @Test fun `units 7 to 12 of B2 are written in the outline's order, at the size of the level, naming their pages`() {
        assertEquals(units, course.refs("B2").subList(6, 12).map { it.id })
        val book = Grammar.bundled("en").associateBy { it.id }
        val outline = json.parseToJsonElement(File(companion, "course/en/course.json").readText()).jsonObject["levels"]!!.jsonArray
            .map { it.jsonObject }.first { it["level"]!!.jsonPrimitive.content == "B2" }["units"]!!.jsonArray.map { it.jsonObject }.associateBy { it["id"]!!.jsonPrimitive.content }
        for (id in units) {
            val u = course.units.getValue(id)
            assertEquals(id, "B2", u.level)
            assertEquals("$id: steps", 6, u.intro?.steps?.size)
            assertEquals("$id: worksheets", 3, u.worksheets.size)
            assertEquals("$id: pass", 80, u.test.pass)
            assertTrue("$id: flagged for a native check", u.machineWritten && u.review.orEmpty().contains("native speaker"))
            val sheets = u.worksheets.flatMap { it.exercises }.sumOf(::size)
            assertTrue("$id: $sheets items on the sheets", sheets in 28..35)
            assertEquals("$id: items in the test", 12, u.test.exercises.sumOf(::size))
            assertEquals(id, pages[id], u.grammar)
            assertTrue("$id is written", "planned" !in outline.getValue(id))
            assertEquals(id, pages[id], outline.getValue(id)["grammar"]!!.jsonArray.map { it.jsonPrimitive.content })
            u.grammar.forEach { assertNotNull("$id names $it", book[it]) }
        }
    }

    @Test fun `each new page has its own card in every base, at B2, within the page size`() {
        for (id in newPages) {
            val page = json.parseToJsonElement(File(companion, "grammar/en/$id.json").readText()).jsonObject
            assertEquals(id, "B2", page["level"]!!.jsonPrimitive.content)
            page["rule"]!!.jsonObject.forEach { (b, t) -> assertTrue("$id rule in $b", t.jsonPrimitive.content.length <= 1200) }
            val card = page["card"]!!.jsonObject
            for (key in listOf("title", "rule", "tip")) assertTrue("$id card's $key", (card[key] as JsonObject).keys.containsAll(listOf("en", "sl", "de", "it")))
            assertTrue("$id: two examples on its card", card["examples"]!!.jsonArray.size >= 2)
            assertNotNull("$id: the app reads its card", Grammar.bundled("en").first { it.id == id }.card)
        }
    }

    @Test fun `what people say is almost with the standard form shown`() {
        for ((typed, right) in listOf(
            "Mr Tyson, to who I spoke, was kind." to "Mr Tyson, to whom I spoke, was kind.",
            "The van broke down, what was a pity." to "The van broke down, which was a pity.",
            "The man stood by the gate is Jack." to "The man standing by the gate is Jack.",
            "The man sat by the fire is Old Wilf." to "The man sitting by the fire is Old Wilf.",
            "I am sat here." to "I am sitting here.",
            "I don't like neither of them." to "I don't like either of them.",
            "I haven't got none." to "I haven't got any.",
            "Neither Jack or Sam is coming." to "Neither Jack nor Sam is coming.",
            "The both of them are asleep." to "Both of them are asleep.",
        )) {
            val r = en(typed, right)
            assertEquals(typed, Verdict.ALMOST to Hint.NONSTANDARD, r.verdict to r.hint)
            assertEquals(typed, right, r.expected)
        }
        fun gap(frame: String, typed: String, vararg accept: String) =
            assertEquals("$typed in $frame", Verdict.ALMOST, Sheets.check(SheetItem(accept = accept.toList(), grammar = null, frame = frame), typed, Lang.EN).verdict)
        gap("Arthur, to ${Sheets.BLANK} we sold the hives, was pleased.", "who", "whom")
        gap("It rained all day, ${Sheets.BLANK} meant we stayed inside.", "what", "which")
        gap("The man ${Sheets.BLANK} by the fire is Old Wilf.", "sat", "sitting")
        gap("Neither Jack ${Sheets.BLANK} Sam came.", "or", "nor")
        gap("${Sheets.BLANK} of them are asleep.", "the both", "both")
    }

    @Test fun `those forms are one way only, and need what stands before them`() {
        for ((typed, right) in listOf(
            "whom" to "who", "Who is here?" to "Whom is here?", "What is that?" to "Which is that?", "which" to "what",
            "sitting" to "sat", "standing" to "stood", "stood" to "standing", "sat" to "sitting",
            "either" to "neither", "neither" to "either", "one" to "none", "none" to "one", "nor" to "or",
            "We have none." to "We have any.", "both" to "the both",
        )) assertEquals("$typed for $right", Verdict.WRONG, en(typed, right).verdict)
    }

    @Test fun `what the units teach as a mistake stays wrong, never a slip`() {
        for ((typed, right) in listOf(
            "Mr Dixon, that lives by the beck, is our blacksmith." to "Mr Dixon, who lives by the beck, is our blacksmith.",
            "The Packhorse, who is old, is on the green." to "The Packhorse, which is old, is on the green.",
            "who's" to "whose", "The bridge, over that we walked, is old." to "The bridge, over which we walked, is old.",
            "Walked up the fell, I saw a raven." to "Walking up the fell, I saw a raven.", "a mill building in 1800" to "a mill built in 1800",
            "Having finish the hay, we went." to "Having finished the hay, we went.", "Knowing not the way, we asked." to "Not knowing the way, we asked.",
            "The road is repairing." to "The road is being repaired.", "The gate has being painted." to "The gate has been painted.", "Is said that it's deep." to "It is said that it's deep.",
            "I had cut my hair." to "I had my hair cut.", "I let my hair cut." to "I had my hair cut.", "I had the gate mend." to "I had the gate mended.", "I'll get Sam carry it." to "I'll get Sam to carry it.",
            "Sam suggested me to go." to "Sam suggested going.", "Sam suggested to go." to "Sam suggested going.", "Old Wilf refused paying." to "Old Wilf refused to pay.",
            "Harry denied to take it." to "Harry denied taking it.", "He advised me wait." to "He advised me to wait.", "She apologised to be late." to "She apologised for being late.",
            "every of them" to "every one of them", "Every sheep have a mark." to "Every sheep has a mark.", "Neither Jack nor Sam isn't coming." to "Neither Jack nor Sam is coming.", "Both of Jack and Sam are coming." to "Both Jack and Sam are coming.",
        )) assertEquals("$typed for $right", Verdict.WRONG, en(typed, right).verdict)
    }

    @Test fun `what the table adds keeps one answer of a contraction or a that`() {
        for ((said, long) in listOf(
            "Sam suggested we go by bus." to "Sam suggested that we go by bus.",
            "Jack promised me he'd come." to "Jack promised me that he would come.",
            "She warned us the ice was thin." to "She warned us that the ice was thin.",
            "He admitted he had taken it." to "He admitted that he had taken it.",
            "It is thought Old Wilf is ninety." to "It is thought that Old Wilf is ninety.",
            "It's said that it's deep." to "It is said that it is deep.",
            "It's been mended." to "It has been mended.", "It's being mended." to "It is being mended.",
            "She's had her hair cut." to "She has had her hair cut.",
        )) {
            assertEquals(said, Verdict.CORRECT, en(said, long).verdict)
            assertEquals(long, Verdict.CORRECT, en(long, said).verdict)
        }
        assertEquals("He denied that. isn't He denied.", Verdict.WRONG, en("He denied that.", "He denied.").verdict)
    }

    @Test fun `the spoken forms are right, labelled with the standard form`() {
        fun said(unit: String, typed: String, standard: String) {
            val found = items(unit).filter { typed in it.spoken }
            assertTrue("$unit: $typed is listed", found.isNotEmpty())
            for (i in found) assertEquals(unit, Verdict.CORRECT to standard, Sheets.check(i, typed, Lang.EN).let { it.verdict to it.standard })
        }
        said("passive-more", "My bike has got stolen.", "My bike has been stolen.")
        said("reporting-verbs", "to not touch", "not to touch")
        said("reporting-verbs", "Mrs Birkett warned us to not walk on the ice.", "Mrs Birkett warned us not to walk on the ice.")
        said("both-neither", "have", "has")
        said("both-neither", "are", "is")
        said("both-neither", "Neither Jack nor Sam are coming.", "Neither Jack nor Sam is coming.")
        said("both-neither", "I don't like neither tea nor coffee.", "I like neither tea nor coffee.")
    }

    @Test fun `no gap or line of the six forgets an answer that is plainly right`() {
        fun takes(unit: String, frame: String, vararg typed: String) {
            val item = items(unit).first { frame in it.frame }
            for (t in typed) assertEquals("$frame: $t", Verdict.CORRECT, Sheets.check(item, t, Lang.EN).verdict)
        }
        takes("non-defining", "Old Wilf, ", "who", "whom")
        takes("participle-clauses", "his soup, Wilf fell asleep", "Having finished", "After finishing", "After having finished")
        takes("passive-more", "It ${Sheets.BLANK} (open) again", "will be opened", "is going to be opened")
        takes("have-it-done", "She ${Sheets.BLANK} the roof mended", "had", "got")
        takes("reporting-verbs", "He admitted", "forgetting", "having forgotten")
        takes("both-neither", "None of the walkers", "is", "are")
        fun line(unit: String, from: String, vararg typed: String) {
            val item = items(unit).first { it.accept.any { a -> a.contains(from) } }
            for (t in typed) assertEquals(t, Verdict.CORRECT, Sheets.check(item, t, Lang.EN).verdict)
        }
        line("non-defining", "Mr Dixon, who lives", "Mr Dixon, who lives by the beck, is our blacksmith.", "Mr Dixon, who is our blacksmith, lives by the beck.")
        line("passive-more", "The tarn is said", "It is said that the tarn has no bottom.", "It is said the tarn has no bottom.", "The tarn is said to have no bottom.")
        line("have-it-done", "I had my boots mended", "I had my boots mended.", "I got my boots mended.", "I had my boots mended by Mr Dixon.", "I got my boots mended by Mr Dixon.")
        line("reporting-verbs", "Harry admitted", "Harry admitted taking the cake.", "Harry admitted having taken the cake.", "Harry admitted to taking the cake.", "Harry admitted he took the cake.", "Harry admitted that he'd taken the cake.")
        line("both-neither", "Both Jack and Sam", "Both Jack and Sam are coming.", "Jack and Sam are both coming.")
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

    @Test fun `a choice that turns on commas or on who is meant is never typed`() {
        // its options grade alike without their commas, so it stays a tap: only a prompt with a gap is ever typed (Adaptive)
        val u = course.units.getValue("non-defining")
        val noGap = (u.worksheets.flatMap { it.exercises } + u.test.exercises).filterIsInstance<Exercise.Choice>().filterNot { Sheets.GAP.containsMatchIn(it.prompt) } +
            u.intro?.steps.orEmpty().mapNotNull { it.ask as? Exercise.Choice }.filterNot { Sheets.GAP.containsMatchIn(it.prompt) }
        assertTrue("the comma's choices are there", noGap.size >= 4)
        for (c in noGap) assertEquals(c.prompt, null, Forms.gap(c.prompt, c.options[c.answer]))
    }

    @Test fun `no text of the six says never of a form people say, and each names it`() {
        val forms = """to who|what was a pity|the man stood|a raven flew|It's been broke|the both of them|neither Jack or Sam|I don't like neither|I haven't got none"""
        val said = Regex("""\b(?:never|not|nie|nicht|nikoli|ne|mai|non)\s+\*[^*]*(?:$forms)[^*]*\*""", RegexOption.IGNORE_CASE)
        fun says(unit: String) = json.parseToJsonElement(File(companion, "course/en/units/$unit.json").readText()).jsonObject["intro"]!!.jsonObject["steps"]!!.jsonArray
            .flatMap { s -> s.jsonObject["say"]!!.jsonObject.values.map { it.jsonPrimitive.content } }
        fun rule(page: String) = json.parseToJsonElement(File(companion, "grammar/en/$page.json").readText()).jsonObject["rule"]!!.jsonObject.values.map { it.jsonPrimitive.content }
        val texts = units.flatMap(::says) + newPages.flatMap(::rule)
        assertEquals(emptyList<String>(), texts.filter { said.containsMatchIn(it) })
        for ((unit, form) in listOf(
            "non-defining" to "to who", "non-defining" to "what was a pity", "participle-clauses" to "the man stood by the gate", "participle-clauses" to "a raven flew over me",
            "passive-more" to "It's been broke", "both-neither" to "the both of them", "both-neither" to "neither Jack or Sam", "both-neither" to "I don't like neither of them", "both-neither" to "I haven't got none",
        )) assertEquals("$unit names $form in each base", 4, says(unit).count { form in it })
    }
}
