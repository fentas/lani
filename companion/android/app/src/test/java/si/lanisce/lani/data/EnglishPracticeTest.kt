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
import si.lanisce.lani.game.Content
import si.lanisce.lani.game.ContentPool
import si.lanisce.lani.game.Res
import java.io.File
import kotlin.random.Random

/**
 * English's practice as data, read as this app reads it (docs/plans/05-built/practice-en.md). The modules as the bridge serves
 * them (companion/modules/en, its texts per base as plain strings): each parses in Slovene, German and Italian, none has an
 * exercise the app doesn't know, every right answer of a choice is graded right by the English table and no wrong one is, and
 * a run of grammar or conversation at each level A1–B2 has its drills. The car's drills bundled (companion/drills/en): one of
 * each kind, each text's meaning in every base (never the English answer as the cue), the numbers counted in English.
 */
class EnglishPracticeTest {
    private val dir = listOf(File("../../modules/en"), File("../modules/en"), File("companion/modules/en")).first { it.isDirectory }
    private val based = setOf("explain", "hint", "instruction", "translation", "cue", "label", "prompt", "title", "description", "story", "note", "back")
    private val bases = listOf("sl", "de", "it")

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
        for (base in bases) {
            val all = modules(base)
            assertTrue(all.size >= 20)
            for (m in all) for (e in m.exercises) {
                assertFalse("${m.id}: $e", e is Exercise.Unsupported)
                assertTrue("${m.id}: $e", !e.grammar.isNullOrBlank())
            }
        }
    }

    @Test fun `a learner from Slovene reads Slovene, from Italian Italian`() {
        val sl = modules("sl").first { it.id == "be-and-have" }.exercises.first { it is Exercise.Choice } as Exercise.Choice
        assertTrue(sl.instruction, sl.instruction!!.contains("Izberi"))
        val it = modules("it").first { it.id == "be-and-have" }.exercises.first { it is Exercise.Choice } as Exercise.Choice
        assertTrue(it.instruction, it.instruction!!.contains("Scegli"))
    }

    @Test fun `a choice's answer is graded right by the English table, typed, and no other option is`() {
        val old = si.lanisce.lani.l10n.L10n.pair
        si.lanisce.lani.l10n.L10n.pair = si.lanisce.lani.l10n.LangPair(si.lanisce.lani.l10n.Lang.EN, si.lanisce.lani.l10n.Lang.SL)
        try {
            for (m in modules("sl")) for (e in m.exercises.filterIsInstance<Exercise.Choice>()) {
                val right = e.options[e.answer]
                e.options.forEachIndexed { i, o ->
                    if (i != e.answer && i !in e.almost) {
                        val g = Grading.check(o, listOf(right), si.lanisce.lani.l10n.Lang.EN)
                        assertTrue("${m.id}: «$o» would pass for «$right»", g.verdict != Grading.Verdict.CORRECT)
                    }
                }
            }
        } finally {
            si.lanisce.lani.l10n.L10n.pair = old
        }
    }

    @Test fun `a run of grammar or conversation has its drills at each level, no note that there are none`() {
        val all = modules("sl")
        for (level in listOf("A1", "A2", "B1", "B2")) {
            val pool = ContentPool(
                cards = emptyList(),
                moduleExercises = all.filter { it.level == level }.flatMap { m -> m.exercises.map { m.id to it } },
                canSpeak = true,
            )
            for (res in listOf(Res.STONE, Res.WISDOM)) {
                val picked = Content.pick(res, 8, pool, Random(3))
                assertTrue("$level $res", picked.items.isNotEmpty())
                assertNull("$level $res: ${picked.note}", picked.note)
            }
        }
    }

    @Test fun `the four drills are bundled, English, every meaning in each base, the numbers counted in English`() {
        val en = Drills.bundled("en")
        assertEquals(setOf(DrillKind.TRANSFORM, DrillKind.RAPID, DrillKind.BUILD, DrillKind.RIDDLE), en.map { d -> d.kind }.toSet())
        assertTrue(en.all { d -> d.language == "en" })
        val numbers = en.first { d -> d.kind == DrillKind.RAPID }.rapid.flatMap { s -> s.items("en") }.filter { i -> i.figure != null }
        assertEquals("twenty-three", numbers.first { i -> i.figure == "23" }.text.said("en"))
        assertEquals("dreiundzwanzig", numbers.first { i -> i.figure == "23" }.text.meaning("de"))
        assertEquals("triindvajset", numbers.first { i -> i.figure == "23" }.text.meaning("sl"))
        assertEquals("Old Wilf tells them", "wilf", en.first { d -> d.kind == DrillKind.RIDDLE }.teller)
        // what a learner of English hears before answering is never the English itself
        val transform = en.first { d -> d.kind == DrillKind.TRANSFORM }
        for (s in transform.transforms) for (i in s.items) for (b in bases) {
            assertTrue("${s.id}: ${i.to.said("en")} in $b", i.to.meaning(b) != i.to.said("en"))
        }
    }
}
