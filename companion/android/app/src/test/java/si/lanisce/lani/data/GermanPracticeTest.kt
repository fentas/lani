package si.lanisce.lani.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Hint
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.Content
import si.lanisce.lani.game.ContentPool
import si.lanisce.lani.game.Res
import si.lanisce.lani.l10n.Lang
import java.io.File
import kotlin.random.Random

/**
 * German's practice as data, read as this app reads it (docs/plans/05-built/practice-de.md). The modules as the bridge serves
 * them (companion/modules/de, texts per base sl, en, it as plain strings, bridge/src/store.ts): each parses, none has an
 * exercise the app doesn't know, each names its page, a run at each level A1 to B2 has its drills; every typed answer is
 * graded right by the German grading, and no wrong option is a form the table says people say (nonstandard: weil with the
 * verb second, trotzdem for obwohl, the indicative after a verb of saying …). The car's drills bundled (companion/drills/de):
 * one of each kind, the numbers counted in German, Opa Sepp telling the riddles.
 */
class GermanPracticeTest {
    private val dir = listOf(File("../../modules/de"), File("../modules/de"), File("companion/modules/de")).first { it.isDirectory }
    private val based = setOf("explain", "hint", "instruction", "translation", "cue", "label", "prompt", "title", "description", "story", "note", "back")

    /** [e] in [base], as the bridge's inBase does with a curated module's keys. */
    private fun inBase(e: JsonElement, base: String, key: String? = null): JsonElement = when (e) {
        is JsonArray -> JsonArray(e.map { inBase(it, base) })
        is JsonObject -> {
            val isText = key in based && e.isNotEmpty() && e.values.all { it is JsonPrimitive && it.isString }
            if (isText) e[base] ?: e.getValue("en")
            else JsonObject(e.mapValues { (k, v) -> inBase(v, base, k) })
        }
        else -> e
    }

    private fun modules(base: String): List<Module> = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }.map { f ->
        val served = inBase(json.parseToJsonElement(f.readText()), base).jsonObject
        parseModule(JsonObject(served + ("version" to JsonPrimitive(1))).toString())
    }

    @Test fun `every module parses in each base, every exercise one the app knows, naming its page`() {
        for (base in listOf("sl", "en", "it")) {
            val all = modules(base)
            assertTrue(all.size >= 24)
            assertEquals(setOf("A1", "A2", "B1", "B2"), all.map { it.level }.toSet())
            for (m in all) for (e in m.exercises) {
                assertFalse("${m.id}: $e", e is Exercise.Unsupported)
                assertTrue("${m.id}: $e", !e.grammar.isNullOrBlank())
            }
        }
    }

    @Test fun `a learner from Slovene reads Slovene, one from Italian Italian`() {
        val sl = modules("sl").first { it.id == "dativ" }.exercises.first { it is Exercise.Choice } as Exercise.Choice
        assertTrue(sl.instruction, sl.instruction!!.contains("Izberi"))
        val it = modules("it").first { it.id == "dativ" }.exercises.first { it is Exercise.Choice } as Exercise.Choice
        assertTrue(it.instruction, it.instruction!!.contains("Scegli"))
    }

    @Test fun `a run of grammar or conversation has its drills at each level, no note that there are none`() {
        val all = modules("en")
        for (level in listOf("A1", "A2", "B1", "B2")) {
            val pool = ContentPool(
                cards = emptyList(),
                moduleExercises = all.filter { it.level == level }.flatMap { m -> m.exercises.map { m.id to it } },
                canSpeak = true,
            )
            for (res in listOf(Res.STONE, Res.WISDOM)) {
                val picked = Content.pick(res, 8, pool, Random(5))
                assertTrue("$level $res", picked.items.isNotEmpty())
                assertNull("$level $res: ${picked.note}", picked.note)
            }
        }
    }

    @Test fun `every typed answer is graded right, and an umlaut typed as ae is almost, never wrong`() {
        val problems = mutableListOf<String>()
        for (m in modules("en")) for (e in m.exercises) {
            val typed = when (e) {
                is Exercise.Cloze -> e.accept
                is Exercise.Translate -> e.accept
                is Exercise.Speak -> e.answers()
                else -> emptyList()
            }
            for (a in typed) {
                if (a.contains('{')) problems += "${m.id}: a placeholder in the German «$a»"
                if (Grading.check(a, typed, Lang.DE).verdict != Verdict.CORRECT) problems += "${m.id}: «$a» not right"
                val folded = a.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue")
                if (folded != a && Grading.check(folded, typed, Lang.DE).verdict == Verdict.WRONG) problems += "${m.id}: «$folded» wrong"
            }
        }
        assertEquals(emptyList<String>(), problems)
    }

    @Test fun `no choice and no multi offers as wrong what the table says people say`() {
        val problems = mutableListOf<String>()
        for (m in modules("en")) for (e in m.exercises) when (e) {
            is Exercise.Choice -> {
                val right = e.options[e.answer]
                for ((i, o) in e.options.withIndex()) {
                    if (i == e.answer) continue
                    val alone = Grading.check(o, listOf(right), Lang.DE)
                    val inIt = Grading.check(e.prompt.replace(Regex("_{3,}"), o), listOf(e.prompt.replace(Regex("_{3,}"), right)), Lang.DE)
                    if (alone.hint == Hint.NONSTANDARD || inIt.hint == Hint.NONSTANDARD) problems += "${m.id}: «$o» in «${e.prompt}»"
                }
            }
            is Exercise.Multi -> {
                val rights = e.answers.map { e.options[it] }
                for ((i, o) in e.options.withIndex()) {
                    if (i in e.answers) continue
                    if (Grading.check(o, rights, Lang.DE).hint == Hint.NONSTANDARD) problems += "${m.id}: «$o»"
                }
            }
            is Exercise.Reorder -> {
                // a distractor in place of a word of the first solution never makes a form people say
                val sol = e.solutions.first()
                for (d in e.distractors) for (k in sol.indices) {
                    val built = sol.toMutableList().also { it[k] = d }.joinToString(" ")
                    if (Grading.check(built, e.solutions.map { it.joinToString(" ") }, Lang.DE).hint == Hint.NONSTANDARD) problems += "${m.id}: «$built»"
                }
            }
            else -> {}
        }
        assertEquals(emptyList<String>(), problems)
    }

    @Test fun `what the grading table says people say is almost in a typed gap, never wrong`() {
        val weil = modules("en").first { it.id == "weil-dass" }.exercises.filterIsInstance<Exercise.Cloze>().first { it.accept == listOf("denn") }
        val r = Grading.check(weil.text.replace("___", "weil"), listOf(weil.text.replace("___", "denn")), Lang.DE)
        assertEquals(Verdict.ALMOST, r.verdict)
        assertEquals(Hint.NONSTANDARD, r.hint)
    }

    @Test fun `the four drills are bundled, German, the numbers counted in German, Opa Sepp telling the riddles`() {
        val de = Drills.bundled("de")
        assertEquals(setOf(DrillKind.TRANSFORM, DrillKind.RAPID, DrillKind.BUILD, DrillKind.RIDDLE), de.map { d -> d.kind }.toSet())
        assertTrue(de.all { d -> d.language == "de" })
        val numbers = de.first { d -> d.kind == DrillKind.RAPID }.rapid.flatMap { s -> s.items("de") }.filter { i -> i.figure != null }
        assertEquals("dreiundzwanzig", numbers.first { i -> i.figure == "23" }.text.said("de"))
        assertEquals("triindvajset", numbers.first { i -> i.figure == "23" }.text.meaning("sl"))
        assertEquals("sepp", de.first { d -> d.kind == DrillKind.RIDDLE }.teller)
        val transform = de.first { d -> d.kind == DrillKind.TRANSFORM }
        assertEquals(setOf("A1", "A2", "B1", "B2"), transform.transforms.map { it.level }.toSet())
        // every cue in the learner's base, Slovene and Italian too
        for (s in transform.transforms) for (base in listOf("sl", "it", "en")) assertTrue("${s.id} $base", s.instruction.texts[base]?.isNotBlank() == true)
    }
}
