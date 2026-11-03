package si.lanisce.lani.data

import kotlinx.serialization.json.Json
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
 * English B1, part a (units 1 to 6: time; docs/plans/05-built/course-en-b1-a.md): the units as the outline has them, their new
 * pages with their own cards, what grading/en.json gained for them, and what people say over the six. The content itself is
 * CourseContentTest's.
 */
class EnglishB1aContentTest {
    private fun en(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.EN)
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "course/en/course.json").isFile }
    private val json = Json { ignoreUnknownKeys = true }
    private val course = checkNotNull(Courses.bundled("en"))
    private val units = mapOf(
        "for-since" to listOf("for-since", "present-perfect"),
        "been-waiting" to listOf("present-perfect-continuous", "present-perfect"),
        "past-perfect" to listOf("past-perfect", "past-simple"),
        "used-to" to listOf("used-to", "past-simple"),
        "future-forms" to listOf("future-continuous", "future"),
        "question-tags" to listOf("question-tags", "to-be"),
    )
    private val newPages = units.values.map { it.first() }

    @Test fun `the six units open B1 in the outline's order, each with its pages`() {
        assertEquals(units.keys.toList(), course.levels.first { it.level == "B1" }.units.map { it.id }.take(6))
        for ((id, pages) in units) assertEquals(id, pages, course.units.getValue(id).grammar)
    }

    @Test fun `each new page is B1 with a card of its own, in every base, and within the page's limit`() {
        val book = Grammar.bundled("en").associateBy { it.id }
        for (id in newPages) {
            assertEquals(id, "B1", book.getValue(id).level)
            val raw = json.parseToJsonElement(File(companion, "grammar/en/$id.json").readText()).jsonObject
            val card = raw["card"]!!.jsonObject
            for (key in listOf("title", "rule", "tip")) assertTrue("$id card's $key", (card[key] as JsonObject).keys.containsAll(listOf("en", "sl", "de", "it")))
            assertTrue("$id: two examples on its card", card["examples"]!!.jsonArray.size >= 2)
            checkNotNull(book.getValue(id).card) { "$id: the app reads its card" }
            for ((base, text) in raw["rule"]!!.jsonObject) assertTrue("$id's rule in $base", text.jsonPrimitive.content.length <= 1200)
        }
    }

    @Test fun `'d before a participle is had, before a base form it stays what it was`() {
        for ((typed, right) in listOf(
            "She'd gone." to "She had gone.", "We'd never seen the sea." to "We had never seen the sea.", "I'd already eaten." to "I had already eaten.",
            "Sam'd never been on a horse." to "Sam had never been on a horse.", "He'd left." to "He had left.",
        )) assertEquals(typed, Verdict.CORRECT, en(typed, right).verdict)
        assertEquals(Verdict.CORRECT, en("I'd like a cup of tea.", "I would like a cup of tea.").verdict)
        assertEquals(Verdict.WRONG, en("She'd go.", "She had gone.").verdict)
        val r = en("Shed gone.", "She'd gone.")
        assertEquals(Verdict.ALMOST to Hint.APOSTROPHE, r.verdict to r.hint)
    }

    @Test fun `a negative question's long form is one answer with its contraction`() {
        for ((typed, right) in listOf("Is it not?" to "Isn't it?", "Do you not?" to "Don't you?", "Can she not?" to "Can't she?", "Will it not?" to "Won't it?", "Amn't I?" to "Am I not?", "I usedn't to like tea." to "I used not to like tea.")) {
            assertEquals(typed, Verdict.CORRECT, en(typed, right).verdict)
        }
        assertEquals(Verdict.CORRECT, en("I don't live there anymore.", "I don't live there any more.").verdict)
    }

    @Test fun `what people say is almost with the standard form shown, one way only`() {
        for ((typed, right) in listOf(
            "I been waiting for an hour." to "I've been waiting for an hour.",
            "I didn't used to like tea." to "I didn't use to like tea.", "Did you used to live here?" to "Did you use to live here?", "I use to live in Leeds." to "I used to live in Leeds.",
            "It's cold, ain't it?" to "It's cold, isn't it?", "I ain't seen Jack." to "I haven't seen Jack.",
            "You're coming, innit?" to "You're coming, aren't you?", "You're coming, isn't it?" to "You're coming, aren't you?", "She can swim, isn't it?" to "She can swim, can't she?",
            "I'd went home." to "I'd gone home.", "He had saw it." to "He had seen it.",
        )) {
            val r = en(typed, right)
            assertEquals(typed, Verdict.ALMOST to Hint.NONSTANDARD, r.verdict to r.hint)
            assertEquals(typed, right, r.expected)
        }
        // one way only; the cue copied stays wrong: the unit's point, and on the native list. Ireland's present for the perfect is outside
        // the Lake District's British English the course teaches, and the commonest mistake here, so it is recorded (the lead, 2026-11-03)
        for ((typed, right) in listOf(
            "I'm waiting for an hour." to "I've been waiting for an hour.", "Jack is walking since six." to "Jack has been walking since six.",
            "I'm here since Monday." to "I've been here since Monday.", "We are in the village for two weeks." to "We've been in the village for two weeks.",
            "I've been waiting." to "I'm waiting.", "I am here." to "I have been here.", "I am to Keswick." to "I have been to Keswick.",
            "I used to live in Leeds." to "I use to live in Leeds.", "I know Mrs Hartley since Easter." to "I've known Mrs Hartley since Easter.",
            "I live here for ten years." to "I've lived here for ten years.", "I've lived here since ten years." to "I've lived here for ten years.",
            "Jack has Meg for six years." to "Jack has had Meg for six years.", "She'd gone." to "She'd go.",
        )) assertEquals("$typed for $right", Verdict.WRONG, en(typed, right).verdict)
    }

    @Test fun `the spoken forms are right, labelled with the standard form`() {
        fun items(unit: String): List<SheetItem> {
            val u = course.units.getValue(unit)
            return (u.worksheets.flatMap { it.exercises } + u.test.exercises).filter { it !is Exercise.Sort }.flatMap { Sheets.items(it, Lang.EN) }
        }
        val tag = items("question-tags").first { "innit" in it.spoken }
        assertEquals(Verdict.CORRECT to "isn't it", Sheets.check(tag, "innit", Lang.EN).let { it.verdict to it.standard })
        val agree = items("question-tags").first { it.accept.first() == "So do I." }
        assertEquals(Verdict.CORRECT to "So do I.", Sheets.check(agree, "Me too.", Lang.EN).let { it.verdict to it.standard })
        val neither = items("question-tags").first { it.accept.first() == "Neither can I." }
        assertEquals(Verdict.CORRECT to "Neither can I.", Sheets.check(neither, "Me neither.", Lang.EN).let { it.verdict to it.standard })
        val gonna = items("future-forms").first { "'s gonna rain" in it.spoken }
        assertEquals(Verdict.CORRECT to "is going to rain", Sheets.check(gonna, "'s gonna rain", Lang.EN).let { it.verdict to it.standard })
    }

    @Test fun `a form without its ending is grammar, never a slip in the stem`() {
        assertEquals(emptyList<String>(), SLIPS.filter { (typed, right) -> en(typed, right).verdict != Verdict.WRONG }.map { (typed, right) -> "$typed for $right" })
        assertEquals(Verdict.ALMOST, en("sinse", "since").verdict)
        assertEquals(Verdict.ALMOST, en("alredy", "already").verdict)
    }

    @Test fun `no choice and no word bank of the six offers what people say as the wrong form`() {
        for (id in units.keys) {
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

    @Test fun `no text of the six says never of a form people say`() {
        val said = Regex("""\b(?:never|not|nie|nicht|nikoli|ne|mai|non)\s+\*[^*]*(?:didn't used to|I use to|I been waiting|I'd went|innit|isn't it|amn't)[^*]*\*""", RegexOption.IGNORE_CASE)
        val texts = units.keys.flatMap { id ->
            val unit = json.parseToJsonElement(File(companion, "course/en/units/$id.json").readText()).jsonObject
            unit["intro"]!!.jsonObject["steps"]!!.jsonArray.flatMap { s -> s.jsonObject["say"]!!.jsonObject.values.map { "$id: ${it.jsonPrimitive.content}" } }
        } + newPages.flatMap { id ->
            json.parseToJsonElement(File(companion, "grammar/en/$id.json").readText()).jsonObject["rule"]!!.jsonObject.values.map { "$id: ${it.jsonPrimitive.content}" }
        }
        assertEquals(emptyList<String>(), texts.filter { said.containsMatchIn(it) })
    }

    private companion object {
        val SLIPS = listOf(
            "know" to "known", "live" to "lived", "work" to "worked", "wait" to "waited", "stay" to "stayed", "paint" to "painted", "clean" to "cleaned",
            "mend" to "mended", "bake" to "baked", "close" to "closed", "start" to "started", "lock" to "locked", "use" to "used", "see" to "seen",
            "take" to "taken", "eat" to "eaten", "feed" to "fed", "fed" to "feed", "leave" to "leaves", "start" to "starts", "open" to "opens", "come" to "comes",
            "had" to "has", "has" to "had", "have" to "had", "is it" to "isn't it", "do you" to "don't you", "don't you" to "doesn't he", "did" to "does",
        )
    }
}
