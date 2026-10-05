package si.lanisce.lani.l10n

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Family
import java.io.File

/**
 * The learner in the content (companion/SCENES.md, "The learner in the content"): a first name declined, and a text said
 * to a learner ({learner}, its cases, {m:…|f:…}), the same cases the bridge's smoke reads (bridge/test/fixtures/
 * learner-render.json): the app and the bridge say every text alike.
 */
class LearnerTest {
    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "bridge/test/fixtures").isDirectory }
    private val fixture = Json.parseToJsonElement(File(companion, "bridge/test/fixtures/learner-render.json").readText()).jsonObject

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.content

    private fun learnerOf(o: JsonObject) = Learner.of(
        o.str("name"), o.str("gender"),
        (o["forms"] as? JsonObject)?.mapValues { (_, v) -> v.jsonPrimitive.content },
    )

    private val was = Learner.current

    @After fun asBefore() {
        Learner.current = was
    }

    @Test fun `first names decline as the bridge declines them`() {
        for (n in fixture.getValue("names").jsonArray.map { it.jsonObject }) {
            val name = n.str("name")!!
            val want = n.getValue("forms").jsonObject.mapValues { (_, v) -> v.jsonPrimitive.content }
            assertEquals(name, want, Learner.slovene(name, n.str("gender") == "female"))
        }
    }

    @Test fun `a text is said to the learner as the bridge says it`() {
        for (t in fixture.getValue("texts").jsonArray.map { it.jsonObject }) {
            val l = learnerOf(t.getValue("learner").jsonObject)
            assertEquals(t.str("text"), t.str("said"), l.render(t.str("text")!!))
        }
    }

    @Test fun `a JSON document's texts are said to the learner, the name escaped as a JSON string's`() {
        val ana = Learner.of("Ana \"Ančka\"", "female")
        val raw = """{"sl": "Dober dan, {learner}! Si {m:lačen|f:lačna}?", "n": [{"a": "{f:Draga|m:Dragi} {learner:gen}"}], "x": {}}"""
        val o = Json.parseToJsonElement(ana.renderJson(raw)).jsonObject
        assertEquals("Dober dan, Ana \"Ančka\"! Si lačna?", o.str("sl"))
        assertEquals("Draga Ana \"Ančka\"", ((o.getValue("n") as JsonArray)[0] as JsonObject).str("a"))
        val jan = raw.replace("{learner}", "Jan").replace("{learner:gen}", "Jana").replace("{m:lačen|f:lačna}", "lačen").replace("{f:Draga|m:Dragi}", "Dragi")
        assertEquals(jan, Learner.of("Jan").renderJson(raw))
        assertEquals("a text without placeholders is as it was", jan, Learner.of("Ana", "female").renderJson(jan))
    }

    @Test fun `a profile without a name is the village's word for a friend, small inside a sentence`() {
        val anon = Learner.of(null, "female")
        assertTrue(anon.generic)
        assertEquals("Prijateljica! Pridi, prijateljica.", anon.render("{learner}! Pridi, {learner}."))
        assertEquals("Amico, vieni!", Learner.of("{YOUR_NAME}", language = "it").render("{learner}, vieni!"))
        assertTrue("the generic word is no name", anon.names.isEmpty())
    }

    @Test fun `every form of the learner's name, and nothing else's`() {
        val jan = Learner.of("Jan")
        assertTrue(jan.names.containsAll(listOf("Jan", "Jana", "Janu", "Janom", "Janov", "Janova", "Janovega")))
        assertFalse("Janez" in jan.names)
    }

    @Test fun `the app's labels agree with the learner (learnerGender)`() {
        Learner.current = Learner.of("Ana", "female")
        assertEquals("Pripravljena si!", L10n.text(Lang.SL, "villageScreen.readyGrow"))
        assertEquals("Sei pronta!", L10n.text(Lang.IT, "villageScreen.readyGrow"))
        assertEquals("Spoznala si: Micka (Lanišče)", L10n.text(Lang.SL, "guests.youMet", mapOf("who" to "Micka", "town" to "Lanišče")))
        Learner.current = Learner.of("Jan")
        assertEquals("Pripravljen si!", L10n.text(Lang.SL, "villageScreen.readyGrow"))
        assertEquals("Sei pronto!", L10n.text(Lang.IT, "villageScreen.readyGrow"))
    }

    @Test fun `the family's from form declines as before, and further`() {
        assertEquals("Maje", Family.genitive("Maja"))
        assertEquals("Jana", Family.genitive("Jan"))
        assertEquals("Pavla", Family.genitive("Pavel"))
        assertEquals("Tone", Family.genitive("Tone"))
    }
}
