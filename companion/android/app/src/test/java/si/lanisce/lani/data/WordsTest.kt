package si.lanisce.lani.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.l10n.Lang

class WordsTest {
    private val gozdu = """
        {"word":"gozdu","entries":[
          {"lemma":"gozd","pos":"noun","gloss":["forest","woods"],"grammar":"locative singular","gender":"m","source":"pack",
           "item_id":"vocab_word_gozd","known":false,"example":{"sl":"V gozdu je tiho.","en":"It's quiet in the forest."},"emoji":"🌲"},
          {"lemma":"gozdar","pos":"noun","gloss":[],"grammar":null,"gender":null,"source":"wiktionary","item_id":null,"known":true,
           "example":null,"emoji":null,"new_field":1}
        ],"attribution":"Wiktionary (CC BY-SA 4.0) via kaikki.org"}
    """.trimIndent()

    @Test fun `a gloss in another language than the base is told, a machine-written one flagged`() {
        val l = Words.parse(
            """{"word":"sono","entries":[
              {"lemma":"essere","pos":"verb","gloss":["to be"],"gloss_lang":"en","source":"wiktionary"},
              {"lemma":"ombra","pos":"noun","gloss":["senca"],"gloss_lang":"sl","gloss_via":"translations","gloss_en":["shadow"],"source":"wiktionary"},
              {"lemma":"fare","pos":"verb","gloss":["delati"],"gloss_lang":"sl","gloss_via":"claude","source":"wiktionary"},
              {"lemma":"casa","pos":"noun","gloss":["hiša"],"source":"pack"}]}""",
        )
        val (essere, ombra, fare, casa) = l.entries
        assertEquals(Lang.EN, Words.foreignGloss(essere, Lang.SL)) // the second learner reads Slovene: "po angleško: to be"
        assertNull(Words.foreignGloss(essere, Lang.EN)) // Jan reads English: nothing to say
        assertNull(Words.foreignGloss(ombra, Lang.SL))
        assertEquals("translations", ombra.glossVia)
        assertEquals("claude", fare.glossVia)
        assertNull(Words.foreignGloss(casa, Lang.SL)) // a node before languages says nothing
    }

    @Test fun `a lookup's entries parse, nulls and unknown fields included`() {
        val l = Words.parse(gozdu)
        assertEquals("gozdu", l.word)
        assertEquals(2, l.entries.size)
        val e = l.entries[0]
        assertEquals("gozd", e.lemma)
        assertEquals("noun", e.pos)
        assertEquals(listOf("forest", "woods"), e.gloss)
        assertEquals("locative singular", e.grammar)
        assertEquals("m", e.gender)
        assertEquals("vocab_word_gozd", e.itemId)
        assertFalse(e.known)
        assertEquals(WordExample("V gozdu je tiho.", "It's quiet in the forest."), e.example)
        assertEquals("🌲", e.emoji)
        assertTrue(l.entries[1].known)
        assertNull(l.entries[1].itemId)
        assertEquals("Wiktionary (CC BY-SA 4.0) via kaikki.org", l.attribution)
    }

    @Test fun `a word the dictionary doesn't know has no entries`() {
        val l = Words.parse("""{"word":"xyz","entries":[],"attribution":null}""")
        assertTrue(l.entries.isEmpty())
        assertNull(l.attribution)
    }

    @Test fun `the same lemma as another part of speech is another entry`() =
        assertTrue(WordEntry("pot", "noun").key != WordEntry("pot", "verb").key)

    @Test fun `the lookup url carries the word, its line and translation, encoded`() {
        val u = Words.url("http://10.0.2.2:8791/", "gozdu", "V gozdu je tiho.", "It's quiet in the forest.")
        assertEquals("/lookup", u.encodedPath)
        assertEquals("gozdu", u.queryParameter("w"))
        assertEquals("V gozdu je tiho.", u.queryParameter("line"))
        assertEquals("It's quiet in the forest.", u.queryParameter("en"))
        assertEquals("čaj", Words.url("http://node", "čaj", null, null).queryParameter("w"))
    }

    @Test fun `a blank line or translation is left out of the url`() {
        val u = Words.url("http://node", "hvala", "  ", null)
        assertNull(u.queryParameter("line"))
        assertNull(u.queryParameter("en"))
    }

    @Test fun `the meaning of a new word is its first glosses`() {
        assertEquals("forest, woods", Words.meaning(WordEntry("gozd", gloss = listOf("forest", " woods ", "forest", ""))))
        assertEquals("a, b, c", Words.meaning(WordEntry("x", gloss = listOf("a", "b", "c", "d"))))
    }

    @Test fun `a new word keeps the line it was met in, else the dictionary's example`() {
        val e = Words.parse(gozdu).entries[0]
        assertEquals(WordExample("Grem v gozd.", "I'm going to the forest."), Words.example(" Grem v gozd. ", "I'm going to the forest.", e))
        assertEquals(WordExample("Grem v gozd.", ""), Words.example("Grem v gozd.", null, e))
        assertEquals(e.example, Words.example("", null, e))
    }

    @Test fun `adding a word is a POST to words with its client id`() {
        val w = Writes.addWord(
            sl = "gozd", en = "forest", pos = "noun", itemId = "vocab_word_gozd",
            example = WordExample("V gozdu je tiho.", "It's quiet in the forest."), from = Words.SCENE, id = "w1", now = 5,
        )
        assertEquals("POST", w.method)
        assertEquals("/words", w.path)
        assertEquals("w1", w.clientId)
        val o = json.parseToJsonElement(w.body).jsonObject
        assertEquals("gozd", o["sl"]!!.jsonPrimitive.content)
        assertEquals("forest", o["en"]!!.jsonPrimitive.content)
        assertEquals("noun", o["pos"]!!.jsonPrimitive.content)
        assertEquals("vocab_word_gozd", o["item_id"]!!.jsonPrimitive.content)
        assertEquals("V gozdu je tiho.", o["example_sl"]!!.jsonPrimitive.content)
        assertEquals("It's quiet in the forest.", o["example_en"]!!.jsonPrimitive.content)
        assertEquals("scene", o["from"]!!.jsonPrimitive.content)
        assertEquals("w1", o["client_id"]!!.jsonPrimitive.content)
    }

    @Test fun `a word from a reading says so only to a bridge that takes it, else it is a villager's as before`() {
        val now = Words.parseSources("""{"from": ["scene", "roleplay", "villager", "chat", "reading"]}""")
        assertEquals(Words.READING, Words.source(Words.READING, now))
        // an older bridge doesn't say (GET /words: 404), and would refuse "reading" with a 400
        assertEquals(Words.VILLAGER, Words.source(Words.READING, null))
        assertEquals(Words.VILLAGER, Words.source(Words.READING, Words.parseSources("""{"from": ["scene", "villager"]}""")))
        // the old four go as they are, to any bridge
        for (s in Words.OLD_SOURCES) assertEquals(s, Words.source(s, null))
        assertEquals(emptySet<String>(), Words.parseSources("""{"error": "not found"}"""))
    }

    @Test fun `what isn't known is left out of the body`() {
        val o = json.parseToJsonElement(Writes.addWord("hvala", "thanks", id = "w2").body).jsonObject
        assertEquals(setOf("sl", "en", "client_id"), o.keys)
    }

    @Test fun `asking the tutor carries the word, its line and translation`() {
        val d = Words.askData("gozdu", "V gozdu je tiho.", "It's quiet in the forest.")
        val l = d["lookup"] as JsonObject
        assertEquals("gozdu", l["word"]!!.jsonPrimitive.content)
        assertEquals("V gozdu je tiho.", l["line"]!!.jsonPrimitive.content)
        assertEquals("It's quiet in the forest.", l["en"]!!.jsonPrimitive.content)
        assertFalse("en" in (Words.askData("x", "y", " ")["lookup"] as JsonObject))
    }

    @Test fun `on a visit, asking the tutor names the town's language`() {
        val l = Words.askData("casa", "La casa è grande.", "Hiša je velika.", "it")["lookup"] as JsonObject
        assertEquals("it", l["language"]!!.jsonPrimitive.content)
        assertFalse("language" in (Words.askData("x", "y", null)["lookup"] as JsonObject))
    }

    @Test fun `a meaning in the base, and the English fallback, parse and are told apart`() {
        val l = Words.parse(
            """{"word":"hiše","entries":[
              {"lemma":"hiša","pos":"noun","gloss":["Haus"],"gloss_lang":"de","gloss_via":"direct","gloss_en":["house"],"source":"wiktionary"},
              {"lemma":"na svidenje","pos":"phrase","gloss":["goodbye"],"gloss_lang":"en","fallback":true,"source":"extra"},
              {"lemma":"Tone","pos":"name","gloss":["Tone — the blacksmith"],"gloss_lang":"en","fallback":true,"source":"village"}]}""",
        )
        val (haus, bye, tone) = l.entries
        assertEquals(listOf("house"), haus.glossEn)
        assertFalse(haus.fallback)
        assertNull(Words.foreignGloss(haus, Lang.DE))
        assertFalse(Words.tutorWritesMeaning(haus, Lang.DE))
        assertTrue(bye.fallback)
        assertEquals(Lang.EN, Words.foreignGloss(bye, Lang.DE)) // "auf Englisch: goodbye"
        assertTrue(Words.tutorWritesMeaning(bye, Lang.DE)) // the tutor was asked for a German one
        assertFalse(Words.tutorWritesMeaning(bye, Lang.EN))
        assertFalse(Words.tutorWritesMeaning(tone, Lang.DE)) // a name: nobody writes it
        assertEquals(Lang.EN, Words.foreignGloss(WordEntry("x", fallback = true), Lang.SL)) // marked, whatever gloss_lang says
    }

    @Test fun `on a visit the lookup url names the town's language and the base the meanings are wanted in`() {
        val u = Words.url("http://node", "casa", "La casa.", null, language = "it", meaningIn = "sl")
        assertEquals("it", u.queryParameter("language"))
        assertEquals("sl", u.queryParameter("base"))
        val home = Words.url("http://node", "hiša", null, null, meaningIn = "de")
        assertNull(home.queryParameter("language"))
        assertEquals("de", home.queryParameter("base"))
        assertNull(Words.url("http://node", "hiša", null, null, language = "sl", meaningIn = "sl").queryParameter("base"))
        assertNull(Words.url("http://node", "hiša", null, null).queryParameter("base"))
    }

    @Test fun `the question names the word, then the line`() {
        val t = Words.askText("gozdu", "V gozdu je tiho.")
        assertTrue(t, t.startsWith("📖 «gozdu» — "))
        assertTrue(t, t.endsWith("\n«V gozdu je tiho.»"))
        assertFalse(Words.askText("gozdu", "").contains("\n"))
    }

    @Test fun `word events parse`() {
        assertEquals(
            BridgeEvent.WordsAdded(listOf("vocab_word_gozd", "vocab_x")),
            BridgeEvent.parse("""{"type":"words_added","item_ids":["vocab_word_gozd","vocab_x"]}"""),
        )
        assertEquals(BridgeEvent.WordsAdded(emptyList()), BridgeEvent.parse("""{"type":"words_added"}"""))
        assertEquals(BridgeEvent.LexiconUpdated("gozd"), BridgeEvent.parse("""{"type":"lexicon_updated","word":"gozd"}"""))
        assertNull(BridgeEvent.parse("""{"type":"lexicon_updated"}"""))
        assertNull(DeepLink.of(BridgeEvent.WordsAdded(listOf("a"))))
    }
}
