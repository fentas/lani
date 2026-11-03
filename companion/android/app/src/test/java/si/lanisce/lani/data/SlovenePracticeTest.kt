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
 * Slovene's practice above A1 as this app reads it (docs/plans/05-built/practice-sl.md). The curated modules as the bridge
 * serves them (companion/modules, the A2–B2 ones' texts per base as plain strings in the learner's, bridge/src/store.ts):
 * each parses in each of Slovene's bases, none has an exercise the app doesn't know, every exercise names a page of the
 * bundled book, and a run of grammar or conversation at A2, B1 and B2 has its drills. The car's drills reach B2 in each kind.
 */
class SlovenePracticeTest {
    private val pages = Grammar.bundled("sl").associateBy { it.id }

    @Test fun `every module parses in each base, every exercise one the app knows, naming a page of the book`() {
        for (base in listOf("en", "de", "it")) {
            val all = modules(base)
            assertTrue(all.size >= 24)
            for (m in all) for (e in m.exercises) {
                assertFalse("${m.id}: $e", e is Exercise.Unsupported)
                assertTrue("${m.id}: ${e.grammar}", e.grammar in pages)
            }
        }
        assertEquals(listOf(6, 6, 6, 6), listOf("A1", "A2", "B1", "B2").map { l -> modules("en").count { it.level == l } })
    }

    @Test fun `a learner from German reads German, from Italian Italian, and Jan's six stay in English`() {
        val de = modules("de").first { it.id == "dajalnik-komu" }.exercises.first { it is Exercise.Choice } as Exercise.Choice
        assertTrue(de.instruction, de.instruction!!.contains("Wähle"))
        val it = modules("it").first { it.id == "dajalnik-komu" }.exercises.first { it is Exercise.Choice } as Exercise.Choice
        assertTrue(it.instruction, it.instruction!!.contains("Scegli"))
        val old = modules("de").first { it.id == "biti-sedanjik" }.exercises.first { it is Exercise.Choice } as Exercise.Choice
        assertEquals("Izberi pravilno obliko · Pick the right form", old.instruction)
    }

    @Test fun `a run of grammar or conversation has its drills at A2, B1 and B2`() {
        val all = modules("en")
        for (level in listOf("A2", "B1", "B2")) {
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

    @Test fun `the car's drills reach B2 in each kind, a set's level its page's`() {
        val sl = Drills.bundled("sl")
        val levels = mapOf(
            DrillKind.TRANSFORM to sl.first { it.kind == DrillKind.TRANSFORM }.transforms.map { it.level },
            DrillKind.BUILD to sl.first { it.kind == DrillKind.BUILD }.builds.map { it.level },
            DrillKind.RIDDLE to sl.first { it.kind == DrillKind.RIDDLE }.riddles.map { it.level },
            DrillKind.RAPID to sl.first { it.kind == DrillKind.RAPID }.rapid.map { it.level },
        )
        for ((kind, ls) in levels) assertTrue("$kind: ${ls.toSet()}", ls.containsAll(listOf("A1", "A2", "B1", "B2")))
        val sets = sl.first { it.kind == DrillKind.TRANSFORM }.transforms
        assertTrue(sets.all { pages.getValue(it.rule).level == it.level })
    }

    companion object {
        private val dirs = listOf(File("../../modules"), File("../modules"), File("companion/modules"))
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

        /** Slovene's curated module files (companion/modules itself, not another language's directory). */
        fun files(): List<File> = dirs.first { it.isDirectory }.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }

        /** The module in [f] as the bridge serves it to a learner reading [base]: version 1, its texts plain. */
        fun served(f: File, base: String): Module {
            val s = inBase(json.parseToJsonElement(f.readText()), base).jsonObject
            return parseModule(JsonObject(s + ("version" to JsonPrimitive(1))).toString())
        }

        fun modules(base: String): List<Module> = files().map { served(it, base) }
    }
}
