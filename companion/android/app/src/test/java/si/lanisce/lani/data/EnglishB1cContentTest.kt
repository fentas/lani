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
 * English B1's units 13-18 (companion/course/en, docs/plans/05-built/course-en-b1-c.md): what grading/en.json gained for them
 * (the two 'd, that and whether after a reporting verb), what people say over these units (would after if, would of, a question's
 * order kept in a reported question: almost; was for were, mustn't for can't, woulda, the tense kept: right and labelled), that no
 * choice or bank offers any of it, their outline lines and their six new pages with a hand-written card in every base.
 */
class EnglishB1cContentTest {
    private fun en(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.EN)
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "course/en/course.json").isFile }
    private val json = Json { ignoreUnknownKeys = true }
    private val units = listOf("if-i-had", "might-must", "if-i-had-known", "although-however", "reported-speech", "reported-questions")
    private val course = checkNotNull(Courses.bundled("en"))

    private fun items(unit: String): List<SheetItem> {
        val u = course.units.getValue(unit)
        return (u.worksheets.flatMap { it.exercises } + u.test.exercises).filter { it !is Exercise.Sort }.filter { it !is Exercise.Choice }.flatMap { Sheets.items(it, Lang.EN) }
    }

    @Test fun `the two 'd are had and would`() {
        for ((said, long) in listOf(
            "If I'd known, I'd have come." to "If I had known, I would have come.",
            "I'd have come if I'd known." to "I would have come if I had known.",
            "If she'd asked, he'd have stayed." to "If she had asked, he would have stayed.",
            "If I'd come earlier, I'd have seen him." to "If I had come earlier, I would have seen him.",
            "If it hadn't rained, we wouldn't have stayed." to "If it had not rained, we would not have stayed.",
            "We'd've gone." to "We would have gone.",
            "I'd row across." to "I would row across.",
            "If I had a boat, she'd come." to "If I had a boat, she would come.",
            "I'd never seen it." to "I had never seen it.",
            "We'd walked all day." to "We had walked all day.",
            "I'd need a boat." to "I would need a boat.",
            "You'd better go." to "You had better go.",
        )) {
            assertEquals(said, Verdict.CORRECT, en(said, long).verdict)
            assertEquals(long, Verdict.CORRECT, en(long, said).verdict)
        }
        // a gap takes the contraction alone
        assertEquals(Verdict.CORRECT, en("'d have come", "would have come").verdict)
        assertEquals(Verdict.CORRECT, en("'d known", "had known").verdict)
        // and they aren't one another
        assertEquals(Verdict.WRONG, en("If I'd known.", "If I would known.").verdict)
        assertEquals(Verdict.WRONG, en("I'd row.", "I had row.").verdict)
    }

    @Test fun `a 'd typed without its apostrophe is almost, before another word`() {
        for ((typed, right) in listOf("Id have come." to "I'd have come.", "If Id known." to "If I'd known.", "Hed go." to "He'd go.", "Theyd left." to "They'd left.")) {
            val r = en(typed, right)
            assertEquals(typed, Verdict.ALMOST to Hint.APOSTROPHE, r.verdict to r.hint)
        }
    }

    @Test fun `that after said and told is one answer, and so is whether for if after asked`() {
        assertEquals(Verdict.CORRECT, en("She said that she was tired.", "She said she was tired.").verdict)
        assertEquals(Verdict.CORRECT, en("She told me that she was tired.", "She told me she was tired.").verdict)
        assertEquals(Verdict.CORRECT, en("Sam told Mr Dixon that he was late.", "Sam told Mr Dixon he was late.").verdict)
        assertEquals(Verdict.CORRECT, en("She asked me whether I wanted tea.", "She asked me if I wanted tea.").verdict)
        assertEquals(Verdict.CORRECT, en("I don't know whether Jack is coming.", "I don't know if Jack is coming.").verdict)
        assertEquals(Verdict.WRONG, en("that", "if").verdict)
    }

    @Test fun `what people say in these units is almost with the standard form shown`() {
        for ((typed, right) in listOf(
            "If I'd have known, I'd have come." to "If I had known, I would have come.",
            "If I had have known, I would have come." to "If I had known, I would have come.",
            "I would have come if I'd have known." to "I would have come if I had known.",
            "I would of come." to "I would have come.",
            "We could of helped." to "We could have helped.",
            "He asked where did I live." to "He asked where I lived.",
            "He asked me where did I live." to "He asked me where I lived.",
            "He asked did I want tea." to "He asked if I wanted tea.",
            "She asked me was I tired." to "She asked me if I was tired.",
            "Do you know where is the inn?" to "Do you know where the inn is?",
            "Could you tell me where does the bus stop?" to "Could you tell me where the bus stops?",
            "Do you know what time does the inn open?" to "Do you know what time the inn opens?",
        )) {
            val r = en(typed, right)
            assertEquals(typed, Verdict.ALMOST to Hint.NONSTANDARD, r.verdict to r.hint)
            assertEquals(typed, right, r.expected)
        }
        val gap = SheetItem(accept = listOf("the inn is"), grammar = null, frame = "Do you know where ${Sheets.BLANK}?")
        assertEquals(Verdict.ALMOST to Hint.NONSTANDARD, Sheets.check(gap, "is the inn", Lang.EN).let { it.verdict to it.hint })
    }

    @Test fun `what isn't said stays wrong and the rows don't reach further`() {
        for ((typed, right) in listOf(
            // would after if: German's and Slovene's mistake, not what this English says (the lead's ruling, 2026-11-03)
            "If I would have a boat, I'd row across." to "If I had a boat, I'd row across.",
            "If it would rain, we'd stay." to "If it rained, we'd stay.",
            "If I wouldn't have the sheep, I'd sleep." to "If I didn't have the sheep, I'd sleep.",
            "If I would know the way, I'd tell you." to "If I knew the way, I'd tell you.",
            "If I would have known, I would have come." to "If I had known, I would have come.",
            "I would have come if I would have known." to "I would have come if I had known.",
            "If I wouldn't have known, I'd have stayed." to "If I hadn't known, I'd have stayed.",
            "If I'd have a boat, I'd row across." to "If I had a boat, I'd row across.",
            // a tense mixed, typed where the unit asks for the past
            "If I know the way, I'd tell you." to "If I knew the way, I'd tell you.",
            "If I had a boat, I would rowing." to "If I had a boat, I would row.",
            // a learner's mistakes nobody grown up says
            "She said me she was tired." to "She told me she was tired.",
            "She told to me she was tired." to "She told me she was tired.",
            "Despite of the rain, we walked." to "Despite the rain, we walked.",
            "Although it rained, but we walked." to "Although it rained, we walked.",
            "He asked to me where I lived." to "He asked me where I lived.",
            "He asked where lived I." to "He asked where I lived.",
            "I would be come." to "I would have come.",
            // a direct question stays a direct question
            "Where did you live?" to "Where do you live?",
            "Do you want tea?" to "Did you want tea?",
        )) assertEquals("$typed for $right", Verdict.WRONG, en(typed, right).verdict)
        val direct = SheetItem(accept = listOf("do you live"), grammar = null, frame = "Where ${Sheets.BLANK}?")
        assertEquals(Verdict.WRONG, Sheets.check(direct, "did you live", Lang.EN).verdict)
        val had = SheetItem(accept = listOf("had known"), grammar = null, frame = "If I ${Sheets.BLANK}, I'd have come.")
        assertEquals(Verdict.WRONG, Sheets.check(had, "would have known", Lang.EN).verdict)
    }

    @Test fun `the paired mistakes are wrong while a slip on another word stays one`() {
        for ((typed, right) in listOf("most" to "must", "must" to "most", "what" to "that", "that" to "what", "where" to "there", "there" to "where", "here" to "there", "there" to "here"))
            assertEquals("$typed for $right", Verdict.WRONG, en(typed, right).verdict)
        assertEquals(Verdict.ALMOST, en("altough", "although").verdict)
        assertEquals(Verdict.ALMOST, en("dispite", "despite").verdict)
    }

    @Test fun `the units' spoken forms are right, labelled with the standard form`() {
        fun said(unit: String, form: String, standard: String) {
            val found = items(unit).filter { form in it.spoken && it.accept.first() == standard }
            assertTrue("$unit: $form for $standard", found.isNotEmpty())
            for (i in found) assertEquals("$unit: $form", Verdict.CORRECT to standard, Sheets.check(i, form, Lang.EN).let { it.verdict to it.standard })
        }
        said("if-i-had", "was", "were")
        said("if-i-had", "If I was you, I'd wait for the bus.", "If I were you, I'd wait for the bus.")
        said("if-i-had-known", "woulda come", "would have come")
        said("if-i-had-known", "If I had known, I woulda come.", "If I had known, I would have come.")
        said("although-however", "so", "so that")
        said("although-however", "cos of", "because of")
        said("reported-speech", "is", "was")
        said("reported-speech", "Edna said she is tired.", "Edna said she was tired.")
        said("reported-speech", "will", "would")
        said("reported-questions", "live", "lived")
        said("reported-questions", "He asked me where I work.", "Mr Dixon asked me where I worked.")
    }

    @Test fun `mustn't and must not are right for can't, as America, Scotland and the North say it`() {
        val cant = items("might-must").filter { it.accept.first() == "can't" }
        assertTrue("the gaps with can't", cant.isNotEmpty())
        for (i in cant) for (form in listOf("mustn't", "must not", "couldn't", "won't")) assertEquals(form, Verdict.CORRECT, Sheets.check(i, form, Lang.EN).verdict)
        assertEquals(Verdict.CORRECT, Sheets.check(items("might-must").first { it.accept.first() == "That can't be Jack." }, "That mustn't be Jack.", Lang.EN).verdict)
    }

    @Test fun `no choice and no bank of these units offers what people say as the wrong form`() {
        val said = mapOf(
            "if-i-had" to Regex("""(?i)^was$"""), "might-must" to Regex("""(?i)mustn't|must not|^does$|^doesn't$"""),
            "if-i-had-known" to Regex("""(?i)^knew$|would of|woulda|'d have known"""), "although-however" to Regex("""(?i)^so$|cos of"""),
            "reported-speech" to Regex("""(?i)^(is|are|will|can|don't)$"""), "reported-questions" to Regex("""(?i)did i |does the|is the|^i live$|^i want$"""),
        )
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
                        assertTrue("$id: «$o» is said, in «${c.prompt}»", !said.getValue(id).containsMatchIn(o))
                    }
                }
            }
            for (e in all.filterIsInstance<Exercise.GapText>()) for (d in e.distractors) for (item in Sheets.items(e, Lang.EN)) {
                assertTrue("$id: the distractor «$d» in ${item.frame}", Grading.check(d, item.accept, Lang.EN, item.frame.substringBefore(Sheets.BLANK, "")).hint != Hint.NONSTANDARD)
                assertTrue("$id: the distractor «$d» is said in ${item.frame}", d !in item.spoken)
            }
        }
    }

    @Test fun `no text of these units says never of a form people say`() {
        val said = Regex("""\b(?:never|not|nie|nicht|nikoli|ne|mai|non)\s+\*[^*]*(?:If I was|mustn't|would of|woulda|If I'd have|did I want|did I live|where's the inn)[^*]*\*""", RegexOption.IGNORE_CASE)
        val ours = listOf("second-conditional", "might-must", "third-conditional", "contrast-linkers", "reported-speech", "reported-questions")
        val texts = units.flatMap { id ->
            val unit = json.parseToJsonElement(File(companion, "course/en/units/$id.json").readText()).jsonObject
            unit["intro"]!!.jsonObject["steps"]!!.jsonArray.flatMap { s -> s.jsonObject["say"]!!.jsonObject.values.map { "$id: ${it.jsonPrimitive.content}" } }
        } + ours.flatMap { id ->
            val p = json.parseToJsonElement(File(companion, "grammar/en/$id.json").readText()).jsonObject
            (p["rule"]!!.jsonObject.values + p["card"]!!.jsonObject["tip"]!!.jsonObject.values).map { "$id: ${it.jsonPrimitive.content}" }
        }
        assertEquals(emptyList<String>(), texts.filter { said.containsMatchIn(it) })
    }

    /** All 18 of B1: parts a and b hold their own six, this the level, as EnglishA2cContentTest does A2. */
    private val b1 by lazy { course.levels.first { it.level == "B1" }.units.map { it.id } }

    @Test fun `no choice and no word bank of the whole level offers what people say as the wrong form`() {
        assertEquals(18, b1.size)
        for (id in b1) {
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

    @Test fun `no text of the whole level says never of a form people say`() {
        val said = Regex("""\b(?:never|not|nie|nicht|nikoli|ne|mai|non)\s+\*[^*]*(?:If I was|mustn't|would of|woulda|If I'd have|did I want|did I live|where's the inn|I been|didn't used|I use to|ain't|innit|was took|hisself|meself|theirselves|the \p{L}+ what|amn't|I've saw|haven't never|It were|I seen|I done)[^*]*\*""", RegexOption.IGNORE_CASE)
        val pages = b1.flatMap { course.units.getValue(it).grammar }.distinct().filter { p -> json.parseToJsonElement(File(companion, "grammar/en/$p.json").readText()).jsonObject["level"]!!.jsonPrimitive.content == "B1" }
        val texts = b1.flatMap { id ->
            val unit = json.parseToJsonElement(File(companion, "course/en/units/$id.json").readText()).jsonObject
            unit["intro"]!!.jsonObject["steps"]!!.jsonArray.flatMap { s -> s.jsonObject["say"]!!.jsonObject.values.map { "$id: ${it.jsonPrimitive.content}" } }
        } + pages.flatMap { id ->
            val p = json.parseToJsonElement(File(companion, "grammar/en/$id.json").readText()).jsonObject
            (p["rule"]!!.jsonObject.values + p["card"]!!.jsonObject["tip"]!!.jsonObject.values).map { "$id: ${it.jsonPrimitive.content}" }
        }
        assertEquals(18, pages.size)
        assertEquals(emptyList<String>(), texts.filter { said.containsMatchIn(it) })
    }

    @Test fun `the whole level is written, its sheets within 35 items`() {
        fun items(e: Exercise) = if (e is Exercise.Sort) e.columns.sumOf { it.words.size } else if (e is Exercise.Choice) 1 else Sheets.items(e, Lang.EN).size
        for (id in b1) {
            val n = course.units.getValue(id).worksheets.flatMap { it.exercises }.sumOf { items(it) }
            assertTrue("$id: $n items on its sheets", n in 15..35)
            assertTrue("$id is written", id in course.playable("B1").map { it.id })
        }
    }

    @Test fun `the six units are written in the outline's order at B1, naming their pages`() {
        val b1 = json.parseToJsonElement(File(companion, "course/en/course.json").readText()).jsonObject["levels"]!!.jsonArray
            .map { it.jsonObject }.first { it["level"]!!.jsonPrimitive.content == "B1" }["units"]!!.jsonArray.map { it.jsonObject }
        assertEquals(units, b1.takeLast(6).map { it["id"]!!.jsonPrimitive.content })
        val pages = Grammar.bundled("en").associateBy { it.id }
        val want = mapOf(
            "if-i-had" to listOf("second-conditional", "first-conditional"), "might-must" to listOf("might-must", "must-have-to"),
            "if-i-had-known" to listOf("third-conditional", "second-conditional", "past-perfect"), "although-however" to listOf("contrast-linkers", "linking-words"),
            "reported-speech" to listOf("reported-speech", "past-simple"), "reported-questions" to listOf("reported-questions", "reported-speech", "questions-do"),
        )
        for (line in b1.takeLast(6)) {
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
        for (id in listOf("second-conditional", "might-must", "third-conditional", "contrast-linkers", "reported-speech", "reported-questions")) {
            val page = json.parseToJsonElement(File(companion, "grammar/en/$id.json").readText()).jsonObject
            assertEquals(id, "B1", page["level"]!!.jsonPrimitive.content)
            val card = page["card"]!!.jsonObject
            for (key in listOf("title", "rule", "tip")) {
                val texts = card[key] as JsonObject
                assertTrue("$id card's $key: ${texts.keys}", texts.keys.containsAll(listOf("en", "sl", "de", "it")))
            }
            assertTrue("$id: two examples on its card", card["examples"]!!.jsonArray.size >= 2)
            assertNotNull("$id: the app reads its card", Grammar.bundled("en").first { it.id == id }.card)
        }
    }
}
