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
 * Italian's practice as data, read as this app reads it. The modules as the bridge serves them (companion/modules/it, its
 * texts per base as plain strings, bridge/src/store.ts): each parses, none has an exercise the app doesn't know, and a run of
 * grammar or conversation at each level has its drills, so it no longer says "no grammar drills yet" (Content.pick). The car's
 * drills bundled (companion/drills/it): one of each kind, the numbers counted in Italian.
 */
class ItalianPracticeTest {
    private val dir = listOf(File("../../modules/it"), File("../modules/it"), File("companion/modules/it")).first { it.isDirectory }
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
        for (base in listOf("de", "sl", "en")) {
            val all = modules(base)
            assertTrue(all.size >= 15)
            for (m in all) for (e in m.exercises) {
                assertFalse("${m.id}: $e", e is Exercise.Unsupported)
                assertTrue("${m.id}: $e", !e.grammar.isNullOrBlank())
            }
        }
    }

    @Test fun `a learner from German reads German`() {
        val m = modules("de").first { it.id == "articoli" }
        val c = m.exercises.first { it is Exercise.Choice } as Exercise.Choice
        assertTrue(c.instruction, c.instruction!!.contains("·"))
        assertFalse(c.instruction, c.instruction!!.contains("Pick"))
    }

    @Test fun `a run of grammar or conversation has its drills at each level, no note that there are none`() {
        val all = modules("de")
        for (level in listOf("A1", "A2", "B1")) {
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

    @Test fun `the four drills are bundled, Italian, the numbers counted in Italian`() {
        val it = Drills.bundled("it")
        assertEquals(setOf(DrillKind.TRANSFORM, DrillKind.RAPID, DrillKind.BUILD, DrillKind.RIDDLE), it.map { d -> d.kind }.toSet())
        assertTrue(it.all { d -> d.language == "it" })
        val numbers = it.first { d -> d.kind == DrillKind.RAPID }.rapid.flatMap { s -> s.items("it") }.filter { i -> i.figure != null }
        assertEquals("ventitré", numbers.first { i -> i.figure == "23" }.text.said("it"))
        assertEquals("dreiundzwanzig", numbers.first { i -> i.figure == "23" }.text.meaning("de"))
        assertEquals("Nonno Bepi tells them", "bepi", it.first { d -> d.kind == DrillKind.RIDDLE }.teller)
    }
}
