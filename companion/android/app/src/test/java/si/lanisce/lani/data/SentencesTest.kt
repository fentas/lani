package si.lanisce.lani.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "🔍 Slovnica stavka · The sentence's grammar": the tutor's explanation kept for a line (GET /sentence), asking the tutor
 * about a line (`about_sentence`), and the event that says the tutor explained one.
 */
class SentencesTest {
    @Test fun `the kept explanation is asked for the line, in the visit's language and the pair's base`() {
        val u = Sentences.url("http://node:8790/", "Dvanajst krav se pase.", language = "it", meaningIn = "sl")
        assertEquals("/sentence", u.encodedPath)
        assertEquals("Dvanajst krav se pase.", u.queryParameter("s"))
        assertEquals("it", u.queryParameter("language"))
        assertEquals("sl", u.queryParameter("base"))
        // at home: the village's language, the base the node knows
        val home = Sentences.url("http://node:8790", "Ne, nimam kruha.")
        assertNull(home.queryParameter("language"))
        assertNull(home.queryParameter("base"))
        assertEquals(Sentences.MAX, Sentences.url("http://n", "a".repeat(500)).queryParameter("s")?.length)
    }

    @Test fun `the node's answer, with a note or none`() {
        assertEquals("**krav**: the genitive plural.", Sentences.note("""{"sentence":"x","note":{"text":"**krav**: the genitive plural.","gloss_lang":"en","at":"2026-09-28"}}"""))
        assertNull(Sentences.note("""{"sentence":"x","note":null}"""))
        assertNull(Sentences.note("""{"sentence":"x","note":{"text":"  "}}"""))
    }

    @Test fun `a line is the same line whatever its case and punctuation, as the node keeps it`() {
        assertTrue(Sentences.same("Dvanajst krav se pase!", "dvanajst  krav se pase."))
        assertFalse(Sentences.same("Dvanajst krav se pase.", "Dvajset krav se pase."))
        assertEquals("ne nimam kruha", Sentences.key("Ne, nimam kruha."))
    }

    @Test fun `asking the tutor sends the line, where it was said, and its words as the app read them`() {
        val data = Sentences.askData(
            "Deset jajc, prosim.", "Ten eggs, please.", "sl", "na-trznici", "Micka na tržnici", "jajca", "Babica Micka", listOf("stevila-samostalniki"),
            listOf(SentenceWord("jajc", "jajce", "genitive plural", "after «Deset»: five and up", "stevila-samostalniki"), SentenceWord("prosim", null, null, null, null)),
        )
        val about = data["about_sentence"] as JsonObject
        assertEquals("Deset jajc, prosim.", about["sentence"]?.jsonPrimitive?.content)
        assertEquals("Ten eggs, please.", about["en"]?.jsonPrimitive?.content)
        assertEquals("na-trznici", about["scene"]?.jsonPrimitive?.content)
        assertEquals("stevila-samostalniki", (about["pages"] as JsonArray).single().jsonPrimitive.content)
        val words = about["words"] as JsonArray
        assertEquals("jajce", (words[0] as JsonObject)["lemma"]?.jsonPrimitive?.content)
        assertEquals(setOf("form"), (words[1] as JsonObject).keys)
        assertTrue(Sentences.askText("Deset jajc, prosim.").endsWith("\n«Deset jajc, prosim.»"))
    }

    @Test fun `the tutor explained a line, the event says which`() {
        assertEquals(BridgeEvent.SentenceExplained("Deset jajc, prosim."), BridgeEvent.parse("""{"type":"sentence_explained","sentence":"Deset jajc, prosim."}"""))
        assertNull(BridgeEvent.parse("""{"type":"sentence_explained"}"""))
    }
}
