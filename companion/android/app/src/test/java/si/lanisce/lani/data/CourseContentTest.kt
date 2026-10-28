package si.lanisce.lani.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.l10n.Lang
import java.io.File

/**
 * The course's content as the app plays it (companion/COURSE.md): every unit file of companion/course/sl is a unit the
 * app parses and plays, at its place in the outline; every exercise is a type it knows; every right answer of a sheet,
 * a test and an intro's question is graded right by the app's own grading, and a word bank's distractors are wrong for
 * every gap; every page a unit names is in the bundled grammar book. The bridge's smoke checks the format; this checks
 * what the app makes of it, for every unit written (A1 to B2), so a unit the app would drop or misgrade can't ship.
 */
class CourseContentTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "course/sl/course.json").isFile }
    private val json = Json { ignoreUnknownKeys = true }
    private val course = checkNotNull(Courses.bundled("sl")) { "the bundled course (companion/course/sl)" }
    private val outline = json.parseToJsonElement(File(companion, "course/sl/course.json").readText()).jsonObject
    private val files = File(companion, "course/sl/units").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
    private val raw: Map<String, JsonObject> = files.associate { it.nameWithoutExtension to json.parseToJsonElement(it.readText()).jsonObject }
    private val pages = Grammar.bundled("sl").map { it.id }.toSet()

    private fun JsonElement?.objects(): List<JsonObject> = (this as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    /** Every exercise of [u]: its worksheets' and its test's, with where each is. */
    private fun exercises(u: CourseUnit): List<Pair<String, Exercise>> =
        u.worksheets.flatMap { w -> w.exercises.mapIndexed { i, e -> "${u.id}/${w.id}#$i" to e } } +
            u.test.exercises.mapIndexed { i, e -> "${u.id}/test#$i" to e }

    @Test fun `every unit file is a unit the app plays, at its level in the outline, and none of the outline's written units is missing`() {
        val outlined = outline["levels"].objects().flatMap { l -> l["units"].objects().map { u -> Triple(l["level"]!!.jsonPrimitive.content, u["id"]!!.jsonPrimitive.content, (u["planned"] as? JsonPrimitive)?.booleanOrNull == true) } }
        for (f in files) {
            val id = f.nameWithoutExtension
            val u = course.units[id]
            assertTrue("$id: parsed by the app", u != null)
            assertEquals("$id: its outline's level", course.levelOf(id), u!!.level)
            assertTrue("$id: not planned in the outline (\"planned\": true left on a written unit)", outlined.none { it.second == id && it.third })
            assertTrue("$id: playable", id in course.playable(u.level).map { it.id })
        }
        val written = outlined.filter { !it.third }.map { it.second }
        assertEquals("the outline's written units each have their file", emptyList<String>(), written.filter { it !in raw })
    }

    @Test fun `every exercise is one the app knows - the worksheets, the test, the intro's steps and questions`() {
        for ((id, o) in raw) {
            val u = course.units.getValue(id)
            for ((where, e) in exercises(u)) assertTrue("$where: a type the app plays (${(e as? Exercise.Unsupported)?.kind})", e !is Exercise.Unsupported)
            val steps = (o["intro"] as? JsonObject)?.get("steps").objects()
            val parsed = u.intro?.steps.orEmpty()
            assertEquals("$id: every intro step parsed", steps.size, parsed.size)
            steps.forEachIndexed { i, s ->
                assertEquals("$id: step ${i + 1}'s examples", s["examples"].objects().size, parsed[i].examples.size)
                assertEquals("$id: step ${i + 1}'s rows (as long as the head)", (s["rows"] as? JsonArray)?.size ?: 0, parsed[i].rows.size)
                assertEquals("$id: step ${i + 1}'s quick question (a choice or a cloze)", s["ask"] != null, parsed[i].ask != null)
            }
            val sheets = o["worksheets"].objects()
            assertEquals("$id: every worksheet parsed", sheets.size, u.worksheets.size)
            sheets.forEachIndexed { i, w -> assertEquals("$id/${u.worksheets[i].id}: every exercise", w["exercises"].objects().size, u.worksheets[i].exercises.size) }
        }
    }

    @Test fun `every right answer is graded right, and a word bank's distractors wrong for every gap`() {
        fun right(where: String, item: SheetItem) = item.accept.forEach { a ->
            assertEquals("$where: «$a» in «${item.frame}»", Verdict.CORRECT, Grading.check(a, item.accept, Lang.SL).verdict)
        }
        // a distractor almost right for a gap pays half when tapped: a slip in the stem is forgiven (Grading), so a
        // distractor that is the gap's word with its stem wrong is almost: mestnik's ogenju for ognju,
        // sklanjatev-posebnosti's pesom for psom, vid-vsi-casi's sedli (sesti) for sedeli (the other aspect: worth a
        // look by whoever writes those units); none other
        val almost = mutableListOf<String>()
        for (u in course.units.values) {
            for ((where, e) in exercises(u)) {
                if (e !is Exercise.Sort) Sheets.items(e).forEach { right(where, it) }
                when (e) {
                    is Exercise.GapText -> {
                        val items = Sheets.items(e)
                        for (d in e.distractors) for (item in items) {
                            val v = Grading.check(d, item.accept, Lang.SL).verdict
                            assertTrue("$where: the distractor «$d» is right for «${item.frame}»", v != Verdict.CORRECT)
                            if (v == Verdict.ALMOST) almost += "${u.id}: $d"
                        }
                        assertTrue("$where: a bank holds every gap's first answer", !e.bank || Sheets.bank(e).containsAll(e.gaps.map { it.accept.first() }))
                    }
                    is Exercise.Choice -> assertTrue("$where: the answer is an option", e.answer in e.options.indices)
                    is Exercise.Multi -> assertTrue("$where: the answers are options", e.answers.all { it in e.options.indices })
                    is Exercise.Reorder -> assertTrue("$where: every solution uses the tokens", e.solutions.all { s -> s.map { it.lowercase() }.sorted() == e.tokens.map { it.lowercase() }.sorted() })
                    is Exercise.Sort -> {
                        val words = e.columns.flatMap { it.words }
                        assertEquals("$where: no word twice", words.size, words.map { it.lowercase() }.toSet().size)
                    }
                    else -> {}
                }
            }
            u.intro?.steps.orEmpty().forEachIndexed { i, s ->
                when (val a = s.ask) {
                    is Exercise.Cloze -> a.accept.forEach { x -> assertEquals("${u.id} step ${i + 1}: «$x»", Verdict.CORRECT, Grading.check(x, a.accept, Lang.SL).verdict) }
                    is Exercise.Choice -> assertTrue("${u.id} step ${i + 1}: the answer is an option", a.answer in a.options.indices)
                    else -> {}
                }
            }
        }
        assertEquals("distractors almost right for a gap", listOf("mestnik: ogenju", "sklanjatev-posebnosti: pesom", "vid-vsi-casi: sedli"), almost.distinct().sorted())
    }

    @Test fun `every page a unit names is a page of the bundled book`() {
        fun named(e: JsonElement): List<String> = when (e) {
            is JsonObject -> e.flatMap { (k, v) ->
                when {
                    k == "grammar" && v is JsonPrimitive -> listOfNotNull(v.contentOrNull)
                    k == "grammar" && v is JsonArray -> v.jsonArray.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    else -> named(v)
                }
            }
            is JsonArray -> e.flatMap(::named)
            else -> emptyList()
        }
        for ((id, o) in raw) {
            val missing = named(o).distinct().filter { it !in pages }
            assertEquals("$id: pages the book lacks", emptyList<String>(), missing)
            assertTrue("$id: its first page is its summary", course.units.getValue(id).grammar.firstOrNull() in pages)
        }
    }
}
