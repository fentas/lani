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
 * English B2's units 1-6 (companion/course/en, docs/plans/05-built/course-en-b2-a.md): what grading/en.json gained for them (had have
 * after wish, ought to have, provided that, Jack's used to), what people say over these units (was sat, shoulda, should of, I'm use to,
 * If I'd of known: right on the item or almost by the table), what stays wrong, that no choice or bank offers what people say, their
 * outline lines and their six new pages with a hand-written card in every base.
 */
class EnglishB2aContentTest {
    private fun en(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.EN)
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "course/en/course.json").isFile }
    private val json = Json { ignoreUnknownKeys = true }
    private val units = listOf("narrative-tenses", "future-perfect", "be-used-to", "should-have", "i-wish", "mixed-conditionals")
    private val pages = listOf("narrative-tenses", "future-perfect", "would-used-to", "past-modals", "wish-if-only", "mixed-conditionals")
    private val course = checkNotNull(Courses.bundled("en"))

    private fun items(unit: String): List<SheetItem> {
        val u = course.units.getValue(unit)
        return (u.worksheets.flatMap { it.exercises } + u.test.exercises).filter { it !is Exercise.Sort }.filter { it !is Exercise.Choice }.flatMap { Sheets.items(it, Lang.EN) }
    }

    @Test fun `contractions of these tenses are one answer with the long form`() {
        for ((said, long) in listOf(
            "I'd been walking for hours." to "I had been walking for hours.",
            "He'd been crying." to "He had been crying.",
            "I'll have finished by Friday." to "I will have finished by Friday.",
            "I'll've finished by Friday." to "I will have finished by Friday.",
            "We won't have finished." to "We will not have finished.",
            "We'd walk to school." to "We would walk to school.",
            "I'm used to it." to "I am used to it.",
            "Jack's used to the cold." to "Jack is used to the cold.",
            "Jack's getting used to the cold." to "Jack is getting used to the cold.",
            "She's used to the rain." to "She is used to the rain.",
            "You should've told me." to "You should have told me.",
            "It must've been Sam." to "It must have been Sam.",
            "It can't've been Poppy." to "It cannot have been Poppy.",
            "I needn't have run." to "I need not have run.",
            "I wish I'd listened." to "I wish I had listened.",
            "I wish I'd come." to "I wish I had come.",
            "If only we'd come." to "If only we had come.",
            "If I'd listened, I'd be there now." to "If I had listened, I would be there now.",
            "If I'd come, I'd know." to "If I had come, I would know.",
            "If she'd been here, she'd know." to "If she had been here, she would know.",
        )) {
            assertEquals(said, Verdict.CORRECT, en(said, long).verdict)
            assertEquals(long, Verdict.CORRECT, en(long, said).verdict)
        }
        // would stays would where a wish is about someone else
        assertEquals(Verdict.CORRECT, en("I wish he'd come.", "I wish he would come.").verdict)
        assertEquals(Verdict.CORRECT, en("I wish you'd stop.", "I wish you would stop.").verdict)
    }

    @Test fun `ought to have is should have, provided that is provided, so long as is as long as`() {
        assertEquals(Verdict.CORRECT, en("You ought to have told me.", "You should have told me.").verdict)
        assertEquals(Verdict.CORRECT, en("You oughtn't to have told me.", "You shouldn't have told me.").verdict)
        assertEquals(Verdict.CORRECT, en("You can stay provided you help.", "You can stay provided that you help.").verdict)
        assertEquals(Verdict.CORRECT, en("You can stay providing that you help.", "You can stay providing you help.").verdict)
        assertEquals(Verdict.CORRECT, en("You can stay so long as you help.", "You can stay as long as you help.").verdict)
    }

    @Test fun `what people say in these units is almost with the standard form shown`() {
        for ((typed, right) in listOf(
            "I was sat by the fire." to "I was sitting by the fire.",
            "We were stood by the door." to "We were standing by the door.",
            "I'd been sat there for hours." to "I had been sitting there for hours.",
            "I been walking for hours." to "I had been walking for hours.",
            "I'll of finished by Friday." to "I will have finished by Friday.",
            "I'm use to it." to "I'm used to it.",
            "You'll get use to it." to "You'll get used to it.",
            "You should of told me." to "You should have told me.",
            "You shoulda told me." to "You should have told me.",
            "You could of told me." to "You could have told me.",
            "It musta been Sam." to "It must have been Sam.",
            "It might of been the wind." to "It might have been the wind.",
            "It may of been the wind." to "It may have been the wind.",
            "It can't of been Poppy." to "It can't have been Poppy.",
            "I needn't of run." to "I needn't have run.",
            "You should have went." to "You should have gone.",
            "It must have took an hour." to "It must have taken an hour.",
            "I wish I'd have known." to "I wish I had known.",
            "If only I'd have known." to "If only I had known.",
            "I wish I had have known." to "I wish I had known.",
            "I wish I'd of known." to "I wish I had known.",
            "I wish I had of known." to "I wish I had known.",
            "If I'd of listened, I'd be there now." to "If I had listened, I would be there now.",
            "If I'd have listened, I'd be there now." to "If I had listened, I would be there now.",
        )) {
            val r = en(typed, right)
            assertEquals(typed, Verdict.ALMOST to Hint.NONSTANDARD, r.verdict to r.hint)
            assertEquals(typed, right, r.expected)
        }
    }

    @Test fun `what isn't said stays wrong and the rows don't reach further`() {
        for ((typed, right) in listOf(
            // a learner's mistakes, the units' points
            "I am used to get up early." to "I am used to getting up early.",
            "I am using to getting up." to "I am used to getting up.",
            "We had been walk." to "We had been walking.",
            "We had being walking." to "We had been walking.",
            "We will have mend the gate." to "We will have mended the gate.",
            "By the time you will get here, we'll have eaten." to "By the time you get here, we'll have eaten.",
            "You should have tell me." to "You should have told me.",
            "You should told me." to "You should have told me.",
            "It can have been Sam." to "It can't have been Sam.",
            "I wish I have a dog." to "I wish I had a dog.",
            "I wish I know." to "I wish I knew.",
            "I wish I can swim." to "I wish I could swim.",
            "We'll go unless it won't rain." to "We'll go unless it rains.",
            "Take a coat in case it will rain." to "Take a coat in case it rains.",
            // would after if, and after wish for what's over: the classic mistake, an American form by the sources, not this variety's
            "If I would have listened, I'd be there now." to "If I had listened, I would be there now.",
            "I wish I would have known." to "I wish I had known.",
            "If only I would have known." to "If only I had known.",
            // a tense mixed, the unit's point
            "If I'd listened, I'd have been there now." to "If I had listened, I would be there now.",
            // a state with would
            "We would have a dog." to "We used to have a dog.",
            // the rows' reach: what sat and stood stand for needs was, were or been before them
            "I sat by the fire." to "I was sitting by the fire.",
        )) assertEquals("$typed for $right", Verdict.WRONG, en(typed, right).verdict)
        // and they aren't one another
        assertEquals(Verdict.WRONG, en("by", "until").verdict)
        assertEquals(Verdict.WRONG, en("hope", "wish").verdict)
    }

    @Test fun `the units' spoken forms are right, labelled with the standard form`() {
        fun said(unit: String, form: String, standard: String) {
            val found = items(unit).filter { form in it.spoken && it.accept.first() == standard }
            assertTrue("$unit: $form for $standard", found.isNotEmpty())
            for (i in found) assertEquals("$unit: $form", Verdict.CORRECT to standard, Sheets.check(i, form, Lang.EN).let { it.verdict to it.standard })
        }
        said("narrative-tenses", "was sat", "was sitting")
        said("narrative-tenses", "was stood", "was standing")
        said("should-have", "shoulda told", "should have told")
        said("should-have", "musta been", "must have been")
        said("should-have", "You shoulda told me.", "You should have told me.")
        said("mixed-conditionals", "was", "were")
        said("mixed-conditionals", "If Sam was a walker, he would have come.", "If Sam were a walker, he would have come.")
    }

    @Test fun `each form of what's right on a gap is graded right`() {
        val cant = items("should-have").filter { it.accept.first() == "can't have been" }
        assertTrue("the gaps with can't have", cant.isNotEmpty())
        for (i in cant) for (form in listOf("couldn't have been", "mustn't have been", "must not have been", "won't have been", "can't've been", "cannot have been")) assertEquals(form, Verdict.CORRECT, Sheets.check(i, form, Lang.EN).verdict)
        val wished = items("i-wish").filter { it.accept.contains("was") && it.accept.contains("were") }
        assertTrue("was and were after wish", wished.size >= 2)
        for (i in wished) {
            assertEquals(Verdict.CORRECT, Sheets.check(i, "was", Lang.EN).verdict)
            assertEquals(Verdict.CORRECT, Sheets.check(i, "were", Lang.EN).verdict)
        }
    }

    @Test fun `no choice and no bank of these units offers what people say as the wrong form`() {
        val said = Regex("""(?i)would of|should of|shoulda|coulda|woulda|musta|mighta|was sat|was stood|^use$|use to|'d have known|had have""")
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
                        if (o != "use") assertTrue("$id: «$o» is said, in «${c.prompt}»", !said.containsMatchIn(o))
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
        val said = Regex("""\b(?:never|not|nie|nicht|nikoli|ne|mai|non)\s+\*[^*]*(?:was sat|was stood|I'll of|use to it|should of|shoulda|should have went|'d have known|'d have listened)[^*]*\*""", RegexOption.IGNORE_CASE)
        val texts = units.flatMap { id ->
            val unit = json.parseToJsonElement(File(companion, "course/en/units/$id.json").readText()).jsonObject
            unit["intro"]!!.jsonObject["steps"]!!.jsonArray.flatMap { s -> s.jsonObject["say"]!!.jsonObject.values.map { "$id: ${it.jsonPrimitive.content}" } }
        } + pages.flatMap { id ->
            val p = json.parseToJsonElement(File(companion, "grammar/en/$id.json").readText()).jsonObject
            (p["rule"]!!.jsonObject.values + p["card"]!!.jsonObject["tip"]!!.jsonObject.values).map { "$id: ${it.jsonPrimitive.content}" }
        }
        assertEquals(emptyList<String>(), texts.filter { said.containsMatchIn(it) })
    }

    @Test fun `the units' sheets keep within 35 items and their tests within 15`() {
        fun size(e: Exercise) = if (e is Exercise.Sort) e.columns.sumOf { it.words.size } else if (e is Exercise.Choice) 1 else Sheets.items(e, Lang.EN).size
        for (id in units) {
            val u = course.units.getValue(id)
            val n = u.worksheets.flatMap { it.exercises }.sumOf { size(it) }
            assertTrue("$id: $n items on its sheets", n in 15..35)
            val t = u.test.exercises.sumOf { size(it) }
            assertTrue("$id: $t items in its test", t in 8..15)
            assertTrue("$id is written", id in course.playable("B2").map { it.id })
        }
    }

    @Test fun `the six units are written in the outline's order at B2, naming their pages`() {
        val b2 = json.parseToJsonElement(File(companion, "course/en/course.json").readText()).jsonObject["levels"]!!.jsonArray
            .map { it.jsonObject }.first { it["level"]!!.jsonPrimitive.content == "B2" }["units"]!!.jsonArray.map { it.jsonObject }
        assertEquals(units, b2.take(6).map { it["id"]!!.jsonPrimitive.content })
        val book = Grammar.bundled("en").associateBy { it.id }
        val want = mapOf(
            "narrative-tenses" to listOf("narrative-tenses", "past-simple", "past-perfect", "past-continuous"), "future-perfect" to listOf("future-perfect", "future", "future-continuous"),
            "be-used-to" to listOf("would-used-to", "used-to"), "should-have" to listOf("past-modals", "might-must"),
            "i-wish" to listOf("wish-if-only", "second-conditional", "third-conditional"), "mixed-conditionals" to listOf("mixed-conditionals", "second-conditional", "third-conditional"),
        )
        for (line in b2.take(6)) {
            val id = line["id"]!!.jsonPrimitive.content
            assertTrue("$id is written", "planned" !in line)
            val named = line["grammar"]!!.jsonArray.map { it.jsonPrimitive.content }
            assertEquals("$id's pages", want[id], named)
            named.forEach { assertNotNull("$id names $it", book[it]) }
            val unit = json.parseToJsonElement(File(companion, "course/en/units/$id.json").readText()).jsonObject
            assertEquals("$id: the unit's pages as the outline's", named, unit["grammar"]!!.jsonArray.map { it.jsonPrimitive.content })
        }
    }

    @Test fun `each new page has its own card, in every base`() {
        for (id in pages) {
            val page = json.parseToJsonElement(File(companion, "grammar/en/$id.json").readText()).jsonObject
            assertEquals(id, "B2", page["level"]!!.jsonPrimitive.content)
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
