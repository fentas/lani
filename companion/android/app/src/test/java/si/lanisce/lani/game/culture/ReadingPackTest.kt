package si.lanisce.lani.game.culture

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The readings of a culture pack (readings/<id>.json) as the parser reads them, and what it says about a broken one. */
class ReadingPackTest {
    private val dir = listOf(File("../../cultures"), File("../cultures")).first { it.isDirectory }

    private fun files(id: String): Map<String, String> =
        dir.resolve(id).listFiles { f -> f.name.endsWith(".json") }!!.associate { it.name to it.readText() } +
            dir.resolve(id).resolve("readings").listFiles { f -> f.name.endsWith(".json") }!!.associate { "readings/${it.name}" to it.readText() }

    private fun problems(files: Map<String, String>, id: String = "primorska"): List<String> = try {
        Cultures.parse(id) { files[it] }
        emptyList()
    } catch (e: CultureError) {
        e.problems
    }

    private fun edited(files: Map<String, String>, name: String, edit: (JsonObject) -> JsonElement): Map<String, String> =
        files + (name to edit(Json.parseToJsonElement(files.getValue(name)).jsonObject).toString())

    private fun JsonObject.with(key: String, v: JsonElement) = JsonObject(this + (key to v))
    private fun JsonObject.without(key: String) = JsonObject(this - key)
    private fun text(vararg t: Pair<String, String>) = JsonObject(t.associate { (k, v) -> k to JsonPrimitive(v) })

    @Test fun `the packs' readings parse, and a pack reads only the readings its chest names`() {
        assertEquals(emptyList<String>(), problems(files("primorska")))
        assertEquals(emptyList<String>(), problems(files("friuli"), "friuli"))
        val c = Cultures.parse("primorska") { files("primorska")[it] }
        assertEquals(setOf("frtalja", "potica", "stara-pratika", "knjiga-pregovorov", "ucbenik-dvojina", "zdravljica"), c.readings.keys)
        assertEquals("potica", c.toolReading("micka", 2)?.id)
        assertEquals("frtalja", c.toolReading("micka", 1)?.id)
        assertEquals("zdravljica", c.goodReading("poezije")?.id)
        assertEquals(null, c.goodReading("kruh"))
        // a reading the chest doesn't name isn't read at all (nor checked)
        assertEquals(emptyList<String>(), problems(files("primorska") + ("readings/draft.json" to "{ not json")))
    }

    @Test fun `a reading the chest names must be there`() {
        val chest = edited(files("primorska"), "chest.json") { c ->
            val tools = c.getValue("tools").jsonArray
            c.with("tools", JsonArray(tools.map { t ->
                val o = t.jsonObject
                if (o["giver"] == JsonPrimitive("micka")) o.with("better", o.getValue("better").jsonObject.with("read", JsonPrimitive("strudel"))) else o
            }))
        }
        assertEquals(listOf("primorska/readings/strudel.json: missing"), problems(chest))
    }

    @Test fun `a broken reading says what is wrong, with its file and where`() {
        val good = files("primorska")
        val frtalja = edited(good, "readings/frtalja.json") { r ->
            val steps = r.getValue("steps").jsonArray
            val questions = r.getValue("questions").jsonArray
            r.with("kind", JsonPrimitive("recipe"))
                .with("level", JsonPrimitive("C2"))
                .with("steps", JsonArray(listOf(text("sl" to "Jajca razbij v skledo.")) + steps.drop(1)))
                .with("questions", JsonArray(listOf(questions[0].jsonObject.with("options", JsonArray(listOf(JsonPrimitive("8"), JsonPrimitive("4"))))) + questions.drop(1)))
                .without("servings")
        }
        val p = problems(frtalja)
        assertTrue(p.toString(), "primorska/readings/frtalja.json level: \"C2\", not one of A1, A2, B1, B2" in p)
        assertTrue(p.toString(), "primorska/readings/frtalja.json steps[0]: no \"en\", \"de\", \"it\" text (the title has it)" in p)
        assertTrue(p.toString(), "primorska/readings/frtalja.json questions[0].options: 2, not 3 or 4 (the answer first)" in p)
        assertTrue(p.toString(), "primorska/readings/frtalja.json servings: missing: how much the recipe makes" in p)

        // proverbs without their meaning; a text without the pack's language; a reading named for another
        val proverbs = edited(good, "readings/knjiga-pregovorov.json") { r ->
            val lines = r.getValue("lines").jsonArray
            r.with("id", JsonPrimitive("pregovori"))
                .with("title", text("en" to "A book of proverbs"))
                .with("lines", JsonArray(listOf(lines[0].jsonObject.without("means")) + lines.drop(1)))
        }
        val q = problems(proverbs)
        assertTrue(q.toString(), "primorska/readings/knjiga-pregovorov.json id: \"pregovori\", but the file is knjiga-pregovorov.json" in q)
        assertTrue(q.toString(), "primorska/readings/knjiga-pregovorov.json title: no \"sl\" text (the pack's language)" in q)
        assertTrue(q.toString(), "primorska/readings/knjiga-pregovorov.json lines[0].means: missing: what the proverb means" in q)

        // a page has lines, not steps; a field the format doesn't have
        val page = edited(good, "readings/stara-pratika.json") { r -> r.with("steps", JsonArray(listOf(text("sl" to "Beri.", "en" to "Read.")))) }
        assertTrue(problems(page).toString(), "primorska/readings/stara-pratika.json steps: only a recipe has servings, ingredients and steps" in problems(page))
        val typo = edited(good, "readings/stara-pratika.json") { r -> r.with("titel", JsonPrimitive("x")) }
        assertTrue(problems(typo).toString(), problems(typo).single().let { "primorska/readings/stara-pratika.json" in it && "titel" in it })
    }

    @Test fun `the question types and new words, checked`() {
        val good = files("primorska")
        val page = edited(good, "readings/stara-pratika.json") { r ->
            val questions = r.getValue("questions").jsonArray
            val q0 = questions[0].jsonObject
            r.with("questions", JsonArray(listOf(
                // a true/false statement without its answer, and one with options
                q0.without("options").with("type", JsonPrimitive("true_false")),
                q0.with("type", JsonPrimitive("true_false")).with("answer", JsonPrimitive(true)),
                // a word question whose word isn't in the text; a word on another type
                q0.with("type", JsonPrimitive("word")).with("word", JsonPrimitive("elektrika")),
                q0.with("type", JsonPrimitive("detail")).with("word", JsonPrimitive("pratika")),
                q0.with("type", JsonPrimitive("riddle")),
            ))).with("words", JsonArray(listOf(
                kotlinx.serialization.json.buildJsonObject {
                    put("word", JsonPrimitive("sveča"))
                    put("means", text("en" to "a candle"))
                },
                kotlinx.serialization.json.buildJsonObject {
                    put("word", JsonPrimitive("ded"))
                    put("means", text("en" to "a grandfather", "de" to "ein Großvater", "it" to "un nonno"))
                },
            )))
        }
        val p = problems(page)
        val at = "primorska/readings/stara-pratika.json"
        assertTrue(p.toString(), "$at questions[0].answer: missing: whether the statement holds" in p)
        assertTrue(p.toString(), "$at questions[1].options: a true/false question has none" in p)
        assertTrue(p.toString(), "$at questions[2].word: \"elektrika\" isn't in the text" in p)
        assertTrue(p.toString(), "$at questions[3].word: only a word question names a word" in p)
        assertTrue(p.toString(), "$at questions[4].type: \"riddle\", not one of main_idea, detail, word, true_false, inference" in p)
        // a new word's meaning in the title's other languages, not the pack's; its form in the text ("sveči", not "sveča")
        assertTrue(p.toString(), "$at words[0].means: no \"de\", \"it\" meaning (the title has it)" in p)
        assertTrue(p.toString(), "$at words[0]: \"sveča\" isn't in the text" in p)
        assertTrue(p.toString(), p.none { "words[1]" in it })
        assertEquals(p.toString(), 7, p.size)
    }
}
