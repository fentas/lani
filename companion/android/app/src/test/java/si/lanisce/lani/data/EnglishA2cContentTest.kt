package si.lanisce.lani.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 * English A2's units 13-18 (companion/course/en, docs/plans/05-built/course-en-a2-c.md): what grading/en.json gained for them
 * (a contraction is one answer: 's before going to and a participle, I'd love), a contraction typed without its apostrophe as
 * almost, the paired mistakes a slip in the stem would forgive; the spoken forms they name; their outline lines and their three
 * new pages with a hand-written card in every base.
 */
class EnglishA2cContentTest {
    private fun en(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.EN)
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "course/en/course.json").isFile }
    private val json = Json { ignoreUnknownKeys = true }
    private val units = listOf("going-to", "will", "have-to", "would-like", "have-you-ever", "if-it-rains")
    private val course = checkNotNull(Courses.bundled("en"))

    @Test fun `going to and a participle after 's are one answer with the long form`() {
        assertEquals(Verdict.CORRECT, en("Jack's going to wash the van.", "Jack is going to wash the van.").verdict)
        assertEquals(Verdict.CORRECT, en("She's seen a raven.", "She has seen a raven.").verdict)
        assertEquals(Verdict.CORRECT, en("She has seen a raven.", "She's seen a raven.").verdict)
        assertEquals(Verdict.CORRECT, en("Jack's never been to Keswick.", "Jack has never been to Keswick.").verdict)
        assertEquals(Verdict.CORRECT, en("Who's gone?", "Who has gone?").verdict)
        assertEquals(Verdict.CORRECT, en("It's stopped raining.", "It has stopped raining.").verdict)
        assertEquals(Verdict.CORRECT, en("She's lost her key.", "She has lost her key.").verdict)
        assertEquals(Verdict.CORRECT, en("She's just finished.", "She has just finished.").verdict)
        assertEquals(Verdict.CORRECT, en("I'd love to.", "I would love to.").verdict)
        assertEquals(Verdict.CORRECT, en("I'd like to go.", "I would like to go.").verdict)
    }

    @Test fun `be for have and a passive stay what they are`() {
        assertEquals(Verdict.WRONG, en("She is seen a raven.", "She has seen a raven.").verdict)
        assertEquals(Verdict.WRONG, en("He is gone to Keswick.", "He has gone to Keswick.").verdict)
        assertEquals(Verdict.CORRECT, en("It's written on the gate.", "It is written on the gate.").verdict)
        assertEquals(Verdict.CORRECT, en("She's called Poppy.", "She is called Poppy.").verdict)
        assertEquals(Verdict.CORRECT, en("It's closed.", "It is closed.").verdict)
    }

    @Test fun `a contraction typed without its apostrophe is almost, before another word only`() {
        for ((typed, right) in listOf(
            "Hes seen a raven." to "He's seen a raven.", "Shes never been there." to "She's never been there.",
            "Id like a cup of tea." to "I'd like a cup of tea.", "Wed love to." to "We'd love to.",
            "Ill help you." to "I'll help you.", "Well go tomorrow." to "We'll go tomorrow.",
            "I wont forget." to "I won't forget.", "You mustnt go." to "You mustn't go.", "You shouldnt go." to "You shouldn't go.",
        )) {
            val r = en(typed, right)
            assertEquals(typed, Verdict.ALMOST to Hint.APOSTROPHE, r.verdict to r.hint)
        }
        assertEquals(Verdict.WRONG, en("well", "we'll").verdict)
        assertEquals(Verdict.CORRECT, en("Well done!", "Well done!").verdict)
    }

    @Test fun `the paired mistakes are wrong while a slip on another word stays one`() {
        for ((typed, right) in listOf(
            "never" to "ever", "ever" to "never", "gone" to "done", "done" to "gone", "been" to "seen", "seen" to "been",
            "came" to "come", "come" to "came", "gave" to "give", "give" to "gave", "made" to "make", "make" to "made",
            "wrote" to "write", "write" to "wrote", "met" to "meet", "could" to "would", "would" to "could",
            "went" to "want", "want" to "went", "then" to "when", "when" to "then", "swam" to "swum", "sang" to "sung",
            "drank" to "drunk", "began" to "begun",
        )) assertEquals("$typed for $right", Verdict.WRONG, en(typed, right).verdict)
        assertEquals(Verdict.ALMOST, en("aready", "already").verdict)
        assertEquals(Verdict.ALMOST, en("promse", "promise").verdict)
    }

    @Test fun `the spoken forms are right, labelled with the standard form`() {
        val spoken = SheetItem(accept = listOf("are going to walk"), grammar = null, hint = null, explain = null, frame = "you ___", spoken = listOf("are gonna walk"))
        val mark = Sheets.check(spoken, "are gonna walk", Lang.EN)
        assertEquals(Verdict.CORRECT, mark.verdict)
        assertEquals("are going to walk", mark.standard)
    }

    @Test fun `what people say is almost with the standard form shown, one way only`() {
        for ((typed, right) in listOf(
            "It were raining." to "It was raining.", "We was late." to "We were late.", "Old Wilf were sitting by the fire." to "Old Wilf was sitting by the fire.",
            "I seen a raven yesterday." to "I saw a raven yesterday.", "I done the washing up." to "I did the washing up.",
            "I've saw a raven." to "I've seen a raven.", "He's went to Keswick." to "He's gone to Keswick.", "She's took the bus." to "She has taken the bus.",
            "Have you ate yet?" to "Have you eaten yet?", "I haven't never been to Carlisle." to "I've never been to Carlisle.",
            "Mr Tyson hasn't never seen the sea." to "Mr Tyson has never seen the sea.", "I sung at the dance." to "I sang at the dance.",
            "Yes, I were." to "Yes, I was.", "They wasn't at home." to "They weren't at home.", "There was a lot of walkers." to "There were a lot of walkers.",
        )) {
            val r = en(typed, right)
            assertEquals(typed, Verdict.ALMOST to Hint.NONSTANDARD, r.verdict to r.hint)
            assertEquals(typed, right, r.expected)
        }
        // a participle for a past only as the past of see and do, a past for a participle only after have; the cue copied stays wrong
        for ((typed, right) in listOf(
            "saw" to "seen", "went" to "gone", "I saw a raven." to "I've seen a raven.", "He come home." to "He came home.", "She give me a scone." to "She gave me a scone.",
            "We have was there." to "We have been there.", "I've seen a raven." to "I saw a raven.",
            // a child's or a learner's regular past isn't what grown-ups say, as gooder and more bigger aren't
            "He telled us a story." to "He told us a story.", "Jack catched the bus." to "Jack caught the bus.", "She knowed him." to "She knew him.", "He throwed it." to "He threw it.",
            "I've knew him for years." to "I've known him for years.", "I've flew to Keswick." to "I've flown to Keswick.",
        )) assertEquals("$typed for $right", Verdict.WRONG, en(typed, right).verdict)
        val gap = SheetItem(accept = listOf("seen"), grammar = null, frame = "I've ${Sheets.BLANK} a raven.")
        val said = Sheets.check(gap, "saw", Lang.EN)
        assertEquals(Verdict.ALMOST to "seen", said.verdict to said.expected)
        val cell = Sheets.items(course.units.getValue("have-you-ever").worksheets.first().exercises.first(), Lang.EN).first { it.accept == listOf("gone") }
        assertEquals("the table's own cell, no have before it", Verdict.WRONG, Sheets.check(cell, "went", Lang.EN).verdict)
    }

    @Test fun `Will I and gonna are right, labelled with the standard form`() {
        fun items(unit: String): List<SheetItem> {
            val u = course.units.getValue(unit)
            return (u.worksheets.flatMap { it.exercises } + u.test.exercises).filter { it !is Exercise.Sort }.flatMap { Sheets.items(it, Lang.EN) }
        }
        val shall = items("will").filter { "Will" in it.spoken }
        assertTrue("the offers' gaps", shall.size >= 2)
        for (i in shall) assertEquals(Verdict.CORRECT to "Shall", Sheets.check(i, "Will", Lang.EN).let { it.verdict to it.standard })
        val line = items("will").first { it.accept.first() == "Shall I open the window?" }
        assertEquals(Verdict.CORRECT to "Shall I open the window?", Sheets.check(line, "Will I open the window?", Lang.EN).let { it.verdict to it.standard })
        val gonna = items("going-to").first { "are gonna" in it.spoken }
        assertEquals(Verdict.CORRECT to "are going to", Sheets.check(gonna, "are gonna", Lang.EN).let { it.verdict to it.standard })
    }

    /** A1's and A2's 36 units: part b's EnglishA2bContentTest holds its six, this the rest. */
    private val levels by lazy { course.levels.filter { it.level == "A1" || it.level == "A2" }.flatMap { l -> l.units.map { it.id } } }

    @Test fun `no choice and no word bank of A1 and A2 offers what people say as the wrong form`() {
        assertEquals(36, levels.size)
        for (id in levels) {
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

    @Test fun `no text of A1 and A2 says never of a form people say`() {
        val forms = "amn't|I've saw|I've went|haven't never|hasn't never|It were|he were|you was|we was|I were|they wasn't|I seen|I done"
        val said = Regex("""\b(?:never|not|nie|nicht|nikoli|ne|mai|non)\s+\*?[^*.;]*?(?:$forms)""", RegexOption.IGNORE_CASE)
        fun strings(e: JsonElement): List<String> = when (e) {
            is JsonObject -> e.values.flatMap { strings(it) }
            is JsonArray -> e.flatMap { strings(it) }
            is JsonPrimitive -> if (e.isString) listOf(e.content) else emptyList()
        }
        val units = levels.flatMap { id -> strings(json.parseToJsonElement(File(companion, "course/en/units/$id.json").readText()).jsonObject).map { "$id: $it" } }
        val pages = File(companion, "grammar/en").listFiles()!!.map { json.parseToJsonElement(it.readText()).jsonObject }.filter { it["level"]!!.jsonPrimitive.content in listOf("A1", "A2") }
            .flatMap { p -> strings(p).map { "${p["id"]!!.jsonPrimitive.content}: $it" } }
        assertEquals(emptyList<String>(), (units + pages).filter { said.containsMatchIn(it) })
    }

    @Test fun `A1 names what people say, and writing's form beside it`() {
        fun says(id: String) = json.parseToJsonElement(File(companion, "course/en/units/$id.json").readText()).jsonObject["intro"]!!.jsonObject["steps"]!!.jsonArray
            .flatMap { s -> s.jsonObject["say"]!!.jsonObject.values.map { it.jsonPrimitive.content } }
        for ((id, form) in listOf("to-be" to "*I amn't*", "was-were" to "*you was*", "was-were" to "*Yes, I were*")) {
            assertEquals("$id names $form in every base", 4, says(id).count { form in it })
        }
    }

    @Test fun `the six units are written in the outline's order at A2, naming their pages`() {
        val a2 = json.parseToJsonElement(File(companion, "course/en/course.json").readText()).jsonObject["levels"]!!.jsonArray
            .map { it.jsonObject }.first { it["level"]!!.jsonPrimitive.content == "A2" }["units"]!!.jsonArray.map { it.jsonObject }
        assertEquals(units, a2.takeLast(6).map { it["id"]!!.jsonPrimitive.content })
        val pages = Grammar.bundled("en").associateBy { it.id }
        val want = mapOf(
            "going-to" to listOf("future", "present-continuous"), "will" to listOf("future"), "have-to" to listOf("must-have-to", "can"),
            "would-like" to listOf("would-like"), "have-you-ever" to listOf("present-perfect", "past-simple"), "if-it-rains" to listOf("first-conditional", "future"),
        )
        for (line in a2.takeLast(6)) {
            val id = line["id"]!!.jsonPrimitive.content
            assertTrue("$id is written", "planned" !in line)
            val named = line["grammar"]!!.jsonArray.map { it.jsonPrimitive.content }
            assertEquals("$id's pages", want[id], named)
            named.forEach { assertNotNull("$id names $it", pages[it]) }
            val unit = json.parseToJsonElement(File(companion, "course/en/units/$id.json").readText()).jsonObject
            assertEquals("$id: the unit's pages as the outline's", named, unit["grammar"]!!.jsonArray.map { it.jsonPrimitive.content })
        }
    }

    @Test fun `each new page has its own card, in every base`() {
        for (id in listOf("must-have-to", "would-like", "first-conditional")) {
            val page = json.parseToJsonElement(File(companion, "grammar/en/$id.json").readText()).jsonObject
            assertEquals(id, "A2", page["level"]!!.jsonPrimitive.content)
            val card = page["card"]!!.jsonObject
            for (key in listOf("title", "rule", "tip")) {
                val texts = card[key] as JsonObject
                assertTrue("$id card's $key: ${texts.keys}", texts.keys.containsAll(listOf("en", "sl", "de", "it")))
            }
            assertTrue("$id: two examples on its card", card["examples"]!!.jsonArray.size >= 2)
            val parsed = Grammar.bundled("en").first { it.id == id }.card
            assertNotNull("$id: the app reads its card", parsed)
        }
    }
}
