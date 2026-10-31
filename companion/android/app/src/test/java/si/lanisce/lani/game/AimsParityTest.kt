package si.lanisce.lani.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.TargetNext
import java.io.File

/**
 * The learner's aim as the bridge counts it (companion/bridge/src/aim.ts): the cases of
 * bridge/test/fixtures/target-next-age.json, which the bridge's smoke (test/smoke/aim.ts) reads too, so the app and the
 * bridge agree on the words each age needs, the target a step counts with and the raise still waiting, whoever reaches
 * the age (companion/GAME.md "Ages").
 */
class AimsParityTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "bridge/test/fixtures").isDirectory }
    private val fixture = Json.parseToJsonElement(File(companion, "bridge/test/fixtures/target-next-age.json").readText()).jsonObject

    @Test fun `each age's words on the way to each target are the bridge's`() {
        val table = fixture.getValue("age_words").jsonObject
        assertEquals(6, table.size)
        for ((target, ages) in table) {
            val want = ages.jsonObject.mapValues { it.value.jsonPrimitive.int }
            assertEquals(target, want, Catalog.ageWords(target).mapKeys { it.key.name } + ("OGENJ" to Catalog.wordsFor(Age.OGENJ, target)))
        }
    }

    @Test fun `the target a step counts with, the next age's words and the raise waiting are the bridge's`() {
        val cases = fixture.getValue("aims").jsonArray.map { it.jsonObject }
        assertTrue(cases.size >= 10)
        for (c in cases) {
            val why = (c["why"] as JsonPrimitive).content
            val learner = c.getValue("learner") as JsonObject
            val target = TargetNext.target(learner["target_level"])
            val next = TargetNext.parse(learner["target_level_next"])
            val age = Age.valueOf(c.getValue("age").jsonPrimitive.content)
            val aim = Aims.at(target, next, age)
            assertEquals(why, c.getValue("target").jsonPrimitive.content, aim)
            val words = Age.entries.getOrNull(age.ordinal + 1)?.let { Catalog.wordsFor(it, aim) }
            assertEquals(why, (c["words_next_age"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull, words)
            assertEquals(why, (c["waiting"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull, Aims.waiting(next, age)?.level)
        }
    }

    @Test fun `the app's default target is a new learner's`() {
        // the bridge sets it in the profile (aim.ts defaultTarget: B2 below B2); the app reads B2 where the profile names none
        assertEquals(fixture.getValue("defaults").jsonObject.getValue("A1").jsonPrimitive.content, Catalog.DEFAULT_TARGET)
    }
}
