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
 * English B2's units 13-18 (companion/course/en, docs/plans/05-built/course-en-b2-c.md): what grading/en.json gained for them (the
 * spoken forms to recognise, almost with the standard form shown; make for take and the wrong word parts of word-building, wrong and
 * no slip), what people say over these units (It was Jack what found her, It was that cold, safe for safely, Cheers), that no choice
 * or bank offers any of it, their outline lines and their six new pages with a hand-written card in every base.
 */
class EnglishB2cContentTest {
    private fun en(answer: String, vararg accept: String) = Grading.check(answer, accept.toList(), Lang.EN)
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "course/en/course.json").isFile }
    private val json = Json { ignoreUnknownKeys = true }
    private val units = listOf("cleft-sentences", "so-such-whereas", "phrasal-verbs-more", "word-building", "collocations", "formal-informal")
    private val pages = listOf("emphasis", "result-linkers", "phrasal-verbs-more", "word-building", "collocations", "formal-informal")
    private val course = checkNotNull(Courses.bundled("en"))

    private fun items(unit: String): List<SheetItem> {
        val u = course.units.getValue(unit)
        return (u.worksheets.flatMap { it.exercises } + u.test.exercises).filter { it !is Exercise.Sort }.filter { it !is Exercise.Choice }.flatMap { Sheets.items(it, Lang.EN) }
    }

    @Test fun `what people say to be understood is almost with the standard form shown`() {
        for ((typed, right) in listOf(
            "I'm gonna write to the council." to "I am going to write to the council.",
            "We gotta talk." to "We have got to talk.",
            "We've gotta talk." to "We have got to talk.",
            "We gotta talk." to "We have to talk.",
            "I dunno when the bus comes." to "I do not know when the bus comes.",
            "I wanna see the manager." to "I want to see the manager.",
            "Gimme the timetable." to "Give me the timetable.",
            "Lemme see." to "Let me see.",
            "It's kinda late." to "It's kind of late.",
            "We stayed cos it snowed." to "We stayed because it snowed.",
            "It was Jack what found her." to "It was Jack who found her.",
            "It's Sam what brought the van." to "It's Sam who brought the van.",
            "It were Jack who found her." to "It was Jack who found her.",
        )) {
            val r = en(typed, right)
            assertEquals(typed, Verdict.ALMOST to Hint.NONSTANDARD, r.verdict to r.hint)
            assertEquals(typed, right, r.expected)
        }
        val gap = SheetItem(accept = listOf("who", "that"), grammar = null, frame = "It was Jack ${Sheets.BLANK} found her.")
        assertEquals(Verdict.ALMOST to Hint.NONSTANDARD, Sheets.check(gap, "what", Lang.EN).let { it.verdict to it.hint })
    }

    @Test fun `the rows don't reach further than what they name`() {
        // what for who only after it was, and a question stays a question
        assertEquals(Verdict.WRONG, en("what", "who").verdict)
        assertEquals(Verdict.WRONG, en("What found her?", "Who found her?").verdict)
        assertEquals(Verdict.WRONG, en("gonna", "want to").verdict)
        assertEquals(Verdict.WRONG, en("wanna", "going to").verdict)
    }

    @Test fun `the unit's mistakes are wrong, while a slip on another word stays a slip`() {
        for ((typed, right) in listOf(
            "take" to "make", "make" to "take", "taking" to "making", "have" to "gave", "much" to "such", "such" to "much", "though" to "through", "through" to "though", "onto" to "into", "into" to "onto",
            "inpossible" to "impossible", "unpossible" to "impossible", "inhappy" to "unhappy", "unregular" to "irregular", "inregular" to "irregular", "inlegal" to "illegal", "unlegal" to "illegal",
            "inpolite" to "impolite", "unpolite" to "impolite", "inpatient" to "impatient", "misappear" to "disappear", "disunderstand" to "misunderstand", "unagree" to "disagree",
            "happyness" to "happiness", "beautyful" to "beautiful", "beautifull" to "beautiful", "carefull" to "careful", "visiter" to "visitor", "visiters" to "visitors", "farmor" to "farmer",
            "strong rain" to "heavy rain", "take a photo" to "make a photo",
            "She did rang twice." to "She did ring twice.", "He does works hard." to "He does work hard.", "What Jack did was rang the inn." to "What Jack did was ring the inn.",
            "I look forward to see you." to "I look forward to seeing you.", "They called off it." to "They called it off.", "Yours sincerely" to "Yours faithfully",
        )) assertEquals("$typed for $right", Verdict.WRONG, en(typed, right).verdict)
        for ((typed, right) in listOf("inportant" to "important", "imformation" to "information", "unterstand" to "understand", "sincerly" to "sincerely", "accross" to "across", "decition" to "decision", "sucess" to "success"))
            assertEquals("$typed for $right", Verdict.ALMOST to Hint.TYPO, en(typed, right).let { it.verdict to it.hint })
    }

    @Test fun `the units' spoken forms are right, labelled with the standard form`() {
        fun said(unit: String, form: String, standard: String) {
            val found = items(unit).filter { form in it.spoken && it.accept.first() == standard }
            assertTrue("$unit: $form for $standard", found.isNotEmpty())
            for (i in found) assertEquals("$unit: $form", Verdict.CORRECT to standard, Sheets.check(i, form, Lang.EN).let { it.verdict to it.standard })
        }
        said("cleft-sentences", "It was Jack found her.", "It was Jack who found her.")
        said("cleft-sentences", "What Jack did was he rang the inn.", "What Jack did was ring the inn.")
        said("so-such-whereas", "that", "so")
        said("so-such-whereas", "The hall was that cold we kept our coats on.", "The hall was so cold that we kept our coats on.")
        said("word-building", "safe", "safely")
        said("formal-informal", "Cheers", "Thanks")
        said("formal-informal", "Ta", "Thanks")
    }

    @Test fun `what's right in English is listed, beside the form the unit teaches`() {
        fun right(unit: String, line: String, vararg answers: String) {
            val item = items(unit).first { it.accept.any { a -> a.equals(line, ignoreCase = true) } }
            for (a in answers) assertEquals("$unit: $a", Verdict.CORRECT, Sheets.check(item, a, Lang.EN).verdict)
        }
        right("cleft-sentences", "It was Jack who found her.", "It was Jack that found her.", "It's Jack who found her.", "It's Jack that found her.")
        right("cleft-sentences", "It was on Saturday that she got lost.", "It was Saturday that she got lost.", "It was on Saturday when she got lost.", "It was on Saturday she got lost.")
        right("cleft-sentences", "What I need is a rest.", "What I need's a rest.", "What I need is some rest.")
        right("cleft-sentences", "What Jack did was ring the inn.", "What Jack did was to ring the inn.")
        right("so-such-whereas", "The hall was so cold that we kept our coats on.", "The hall was so cold we kept our coats on.")
        right("so-such-whereas", "It was such a cold night that we stayed in.", "It was such a cold night we stayed in.", "It was so cold a night that we stayed in.")
        right("phrasal-verbs-more", "They called off the dance.", "They called the dance off.")
        right("collocations", "Jack made a decision.", "Jack made a decision")
        right("formal-informal", "I am going to write to the council.", "I'm going to write to the council.")
        right("formal-informal", "I do not know when the bus comes.", "I don't know when the bus comes.")
        // the American form too: take a decision and the rest are the British beside make
        val gap = items("collocations").first { it.accept.first() == "made" && "took" in it.accept }
        assertEquals(Verdict.CORRECT, Sheets.check(gap, "took", Lang.EN).verdict)
        val letter = items("formal-informal").first { it.accept.first() == "faithfully" }
        assertEquals(Verdict.CORRECT, Sheets.check(letter, "truly", Lang.EN).verdict)
    }

    @Test fun `no choice and no bank of these units offers what people say as the wrong form`() {
        val said = Regex("""(?i)\b(?:gonna|gotta|wanna|dunno|innit|gimme|lemme|kinda|cos|cheers|ta|mate|aye|marra|that cold|were)\b""")
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
                        assertTrue("$id: «$o» is said, in «${c.prompt}»", !said.containsMatchIn(o))
                    }
                }
            }
            for (e in all.filterIsInstance<Exercise.GapText>()) for (d in e.distractors) for (item in Sheets.items(e, Lang.EN)) {
                assertTrue("$id: the distractor «$d» in ${item.frame}", Grading.check(d, item.accept, Lang.EN, item.frame.substringBefore(Sheets.BLANK, "")).hint != Hint.NONSTANDARD)
                assertTrue("$id: the distractor «$d» is said in ${item.frame}", d !in item.spoken && !said.containsMatchIn(d))
            }
        }
    }

    @Test fun `no text of these units says never of a form people say`() {
        val said = Regex("""\b(?:never|not|nie|nicht|nikoli|ne|mai|non)\s+\*[^*]*(?:what found|It were|that cold|gonna|gotta|wanna|dunno|innit)[^*]*\*""", RegexOption.IGNORE_CASE)
        val texts = units.flatMap { id ->
            val unit = json.parseToJsonElement(File(companion, "course/en/units/$id.json").readText()).jsonObject
            unit["intro"]!!.jsonObject["steps"]!!.jsonArray.flatMap { s -> s.jsonObject["say"]!!.jsonObject.values.map { "$id: ${it.jsonPrimitive.content}" } }
        } + pages.flatMap { id ->
            val p = json.parseToJsonElement(File(companion, "grammar/en/$id.json").readText()).jsonObject
            (p["rule"]!!.jsonObject.values + p["card"]!!.jsonObject["tip"]!!.jsonObject.values).map { "$id: ${it.jsonPrimitive.content}" }
        }
        assertEquals(emptyList<String>(), texts.filter { said.containsMatchIn(it) })
    }

    @Test fun `the six units are written in the outline's order at B2, naming their pages, their sheets within 35 items`() {
        val b2 = json.parseToJsonElement(File(companion, "course/en/course.json").readText()).jsonObject["levels"]!!.jsonArray
            .map { it.jsonObject }.first { it["level"]!!.jsonPrimitive.content == "B2" }["units"]!!.jsonArray.map { it.jsonObject }
        assertEquals(units, b2.takeLast(6).map { it["id"]!!.jsonPrimitive.content })
        val book = Grammar.bundled("en").associateBy { it.id }
        val want = mapOf(
            "cleft-sentences" to listOf("emphasis"), "so-such-whereas" to listOf("result-linkers", "contrast-linkers"), "phrasal-verbs-more" to listOf("phrasal-verbs-more", "phrasal-verbs"),
            "word-building" to listOf("word-building"), "collocations" to listOf("collocations"), "formal-informal" to listOf("formal-informal"),
        )
        fun count(e: Exercise) = if (e is Exercise.Sort) e.columns.sumOf { it.words.size } else if (e is Exercise.Choice) 1 else Sheets.items(e, Lang.EN).size
        for (line in b2.takeLast(6)) {
            val id = line["id"]!!.jsonPrimitive.content
            assertTrue("$id is written", "planned" !in line)
            val named = line["grammar"]!!.jsonArray.map { it.jsonPrimitive.content }
            assertEquals("$id's pages", want[id], named)
            named.forEach { assertNotNull("$id names $it", book[it]) }
            val unit = json.parseToJsonElement(File(companion, "course/en/units/$id.json").readText()).jsonObject
            assertEquals("$id: the unit's pages as the outline's", named, unit["grammar"]!!.jsonArray.map { it.jsonPrimitive.content })
            val u = course.units.getValue(id)
            val n = u.worksheets.flatMap { it.exercises }.sumOf { count(it) }
            assertTrue("$id: $n items on its sheets", n in 15..35)
            assertTrue("$id: its test", u.test.exercises.sumOf { count(it) } in 12..15)
            assertTrue("$id is written", id in course.playable("B2").map { it.id })
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
