package si.lanisce.lani.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import java.io.File

class TalkTest {
    private val raw = """
        {
          "schema": "lani.scenario/v0", "id": "v-pekarni", "title": "V pekarni · At the bakery", "emoji": "🥖",
          "level": "A1", "setting": "Early morning in a bakery.", "role": "Prodajalka Nina, the baker. Says vi.",
          "goals": ["Greet the baker", "Ask for bread", "Say goodbye"],
          "opener_sl": "Dobro jutro! Izvolite, kaj bo?", "opener_en": "Good morning! What can I get you?",
          "vocabulary_hints": [{ "sl": "En kruh, prosim.", "en": "A loaf of bread, please." }],
          "source": "curated", "future_field": true
        }
    """.trimIndent()
    private val scenario = parseScenario(raw)

    private fun reply(data: String?, text: String = "x") =
        RoleplayReply.parse(text, data?.let { json.parseToJsonElement(it).jsonObject })

    @Test fun `parses a scenario and names the character`() {
        assertEquals("v-pekarni", scenario.id)
        assertEquals(3, scenario.goals.size)
        assertEquals("Dobro jutro! Izvolite, kaj bo?", scenario.openerSl)
        assertEquals("En kruh, prosim.", scenario.vocabularyHints.single().sl)
        assertEquals("Prodajalka Nina", scenario.character)
        assertEquals("Dr. Kovač", scenario.copy(role = "Dr. Kovač, a calm family doctor.").character)
    }

    @Test fun `parses a list with defaults`() {
        val list = parseScenarios("""[{"id":"x","title":"T","setting":"s","role":"R","goals":["a","b","c"],"opener_sl":"Živjo!","opener_en":"Hi!"}]""")
        assertEquals("🗣️", list.single().emoji)
        assertEquals("A1", list.single().level)
        assertTrue(list.single().vocabularyHints.isEmpty())
    }

    @Test fun `every curated scenario parses`() {
        val dir = listOf(File("../../scenarios"), File("../scenarios"), File("companion/scenarios")).first { it.isDirectory }
        val files = dir.listFiles { f -> f.extension == "json" }.orEmpty()
        assertEquals(18, files.size)
        for (f in files) {
            val s = parseScenario(f.readText())
            assertEquals(f.nameWithoutExtension, s.id)
            assertTrue(s.goals.size in 3..5)
            assertFalse(s.character.isBlank())
        }
        // Jan's six at A1, and six talks each at B1 and B2 (docs/plans/05-built/talks-sl.md)
        val levels = files.map { parseScenario(it.readText()).level }.groupingBy { it }.eachCount()
        assertEquals(mapOf("A1" to 6, "B1" to 6, "B2" to 6), levels)
    }

    @Test fun `Jan's B1 and B2 talks read in the pair, for a grown-up, with their packs`() {
        val dir = listOf(File("../../scenarios"), File("../scenarios"), File("companion/scenarios")).first { it.isDirectory }
        val above = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
            .filter { parseScenario(it.readText()).level in setOf("B1", "B2") }
        assertEquals(12, above.size)
        inPair(si.lanisce.lani.l10n.Lang.SL, si.lanisce.lani.l10n.Lang.EN) {
            for (f in above) {
                val s = parseScenario(f.readText())
                assertEquals("sl", s.language)
                assertEquals("adult", s.audience)
                assertTrue(s.packs.isNotEmpty())
                // "Pri županji · At the mayor's office": Slovene, then English, for the title, the goals and who
                assertTrue(s.title, " · " in s.title)
                assertTrue(s.goals.toString(), s.goals.all { " · " in it })
                assertTrue(s.who.orEmpty(), " · " in s.who.orEmpty())
            }
            val boots = parseScenario(File(dir, "reklamacija.json").readText())
            assertEquals("Reklamacija v trgovini · A complaint at the shop", boots.title)
            assertEquals("Povej, kaj je narobe s čevlji · Say what is wrong with the boots", boots.goals.first())
            assertEquals("Prodajalec Gregor", boots.character)
            assertEquals("B1", boots.level)
            assertEquals("Vinar Marko", parseScenario(File(dir, "marko-na-razpotju.json").readText()).character)
        }
        val mayor = inPair(si.lanisce.lani.l10n.Lang.SL, si.lanisce.lani.l10n.Lang.DE) { parseScenario(File(dir, "pri-zupanji.json").readText()) }
        assertEquals("Pri županji · Bei der Bürgermeisterin", mayor.title)
        assertEquals("Županja · Die Bürgermeisterin", mayor.who)
        assertTrue(mayor.setting, mayor.setting.startsWith("Seit die Bushaltestelle"))
        assertEquals("Ich fürchte, dass früher oder später ein Unfall passiert.", mayor.vocabularyHints.first { it.sl.startsWith("Bojim se") }.en)
        assertEquals("Županja Majda", mayor.character)
        assertEquals("B2", mayor.level)
    }

    @Test fun `every Italian scenario parses, its texts in the child's pair`() {
        val dir = listOf(File("../../scenarios/it"), File("../scenarios/it"), File("companion/scenarios/it")).first { it.isDirectory }
        val files = dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        assertTrue(files.size >= 8)
        val before = si.lanisce.lani.l10n.L10n.pair
        si.lanisce.lani.l10n.L10n.pair = si.lanisce.lani.l10n.LangPair(si.lanisce.lani.l10n.Lang.IT, si.lanisce.lani.l10n.Lang.DE)
        try {
            for (f in files) {
                val s = parseScenario(f.readText())
                assertEquals(f.nameWithoutExtension, s.id)
                assertEquals("it", s.language)
                assertEquals("child", s.audience)
                assertTrue(s.goals.size in 3..5)
                assertTrue(s.packs.isNotEmpty())
                assertFalse(s.character.isBlank())
                // "Al mercato · Auf dem Markt": the Italian, then the German, for the title, the goals and who
                assertTrue(s.title, " · " in s.title)
                assertTrue(s.goals.toString(), s.goals.all { " · " in it })
                assertTrue(s.who.orEmpty(), " · " in s.who.orEmpty())
            }
            val mercato = parseScenario(File(dir, "al-mercato.json").readText())
            assertEquals("Al mercato · Auf dem Markt", mercato.title)
            assertEquals("La fruttivendola · Die Obstverkäuferin", mercato.who)
            assertTrue(mercato.setting, mercato.setting.startsWith("Samstagmorgen"))
            assertTrue(mercato.openerEn.startsWith("Guten Morgen!"))
            assertEquals("Was kostet das?", mercato.vocabularyHints.first { it.sl == "Quanto costa?" }.en)
            assertEquals("Signora Gianna", mercato.character)
        } finally {
            si.lanisce.lani.l10n.L10n.pair = before
        }
    }

    /** [block] with the app in [target] · [base], as a learner of that pair has it. */
    private fun <T> inPair(target: si.lanisce.lani.l10n.Lang, base: si.lanisce.lani.l10n.Lang, block: () -> T): T {
        val before = si.lanisce.lani.l10n.L10n.pair
        si.lanisce.lani.l10n.L10n.pair = si.lanisce.lani.l10n.LangPair(target, base)
        try {
            return block()
        } finally {
            si.lanisce.lani.l10n.L10n.pair = before
        }
    }

    private fun scenariosOf(language: String): List<File> {
        val dir = listOf(File("../../scenarios/$language"), File("../scenarios/$language"), File("companion/scenarios/$language")).first { it.isDirectory }
        return dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
    }

    @Test fun `every German and English scenario parses for a grown-up, its texts in the learner's pair`() {
        for ((language, pair) in listOf("de" to (si.lanisce.lani.l10n.Lang.DE to si.lanisce.lani.l10n.Lang.SL), "en" to (si.lanisce.lani.l10n.Lang.EN to si.lanisce.lani.l10n.Lang.IT))) {
            val files = scenariosOf(language)
            assertTrue(language, files.size >= 8)
            inPair(pair.first, pair.second) {
                for (f in files) {
                    val s = parseScenario(f.readText())
                    assertEquals(f.nameWithoutExtension, s.id)
                    assertEquals(language, s.language)
                    assertEquals("adult", s.audience)
                    assertTrue(s.goals.size in 3..5)
                    assertTrue(s.packs.isNotEmpty())
                    assertFalse(s.character.isBlank())
                    // "Beim Bäcker · Pri peku": the village's language, then the learner's, for the title, the goals and who
                    assertTrue(s.title, " · " in s.title)
                    assertTrue(s.goals.toString(), s.goals.all { " · " in it })
                    assertTrue(s.who.orEmpty(), " · " in s.who.orEmpty())
                }
            }
        }
        val baker = inPair(si.lanisce.lani.l10n.Lang.DE, si.lanisce.lani.l10n.Lang.SL) { parseScenario(scenariosOf("de").first { it.name == "beim-baecker.json" }.readText()) }
        assertEquals("Beim Bäcker · Pri peku", baker.title)
        assertEquals("Der Bäcker · Pek", baker.who)
        assertEquals("Grüße den Bäcker · Pozdravi peka", baker.goals.first())
        assertTrue(baker.setting, baker.setting.startsWith("Zgodaj zjutraj"))
        assertTrue(baker.openerSl.startsWith("Grüß Gott!"))
        assertEquals("Koliko stane?", baker.vocabularyHints.first { it.sl == "Was kostet das?" }.en)
        assertEquals("Bäcker Peter", baker.character)
        val shop = inPair(si.lanisce.lani.l10n.Lang.EN, si.lanisce.lani.l10n.Lang.IT) { parseScenario(scenariosOf("en").first { it.name == "at-the-farm-shop.json" }.readText()) }
        assertEquals("At the farm shop · Allo spaccio della fattoria", shop.title)
        assertTrue(shop.setting, shop.setting.startsWith("Sabato mattina"))
        assertEquals("Quanto costa?", shop.vocabularyHints.first { it.sl == "How much is that?" }.en)
        assertEquals("Mrs Atkinson", shop.character)
    }

    @Test fun `the Lake District's B1 and B2 talks read in every pair, for a grown-up, the cast playing its own`() {
        val files = scenariosOf("en")
        // eight to A2 (scenarios-de-en), six each at B1 and B2 (docs/plans/05-built/talks-en.md)
        assertEquals(mapOf("A1" to 5, "A2" to 3, "B1" to 6, "B2" to 6), files.map { parseScenario(it.readText()).level }.groupingBy { it }.eachCount())
        val above = files.filter { parseScenario(it.readText()).level in setOf("B1", "B2") }
        val en = si.lanisce.lani.l10n.Lang.EN
        for (base in listOf(si.lanisce.lani.l10n.Lang.SL, si.lanisce.lani.l10n.Lang.DE, si.lanisce.lani.l10n.Lang.IT)) inPair(en, base) {
            for (f in above) {
                val s = parseScenario(f.readText())
                assertEquals("en", s.language)
                assertEquals("adult", s.audience)
                assertTrue(s.packs.isNotEmpty())
                // "A statement for the police · Izjava za policijo": English, then the learner's base; no gender pair left
                assertTrue(s.title, " · " in s.title)
                assertTrue(s.goals.toString(), s.goals.all { " · " in it && "{m:" !in it })
                assertTrue(s.who.orEmpty(), " · " in s.who.orEmpty())
                assertFalse(s.setting, "{m:" in s.setting || s.vocabularyHints.any { "{m:" in it.en })
            }
        }
        inPair(en, si.lanisce.lani.l10n.Lang.SL) {
            val police = parseScenario(files.first { it.name == "a-statement-for-the-police.json" }.readText())
            assertEquals("A statement for the police · Izjava za policijo", police.title)
            assertEquals("The police officer · Policist", police.who)
            assertEquals("Say precisely when and where it happened · Natančno povej, kdaj in kje se je zgodilo", police.goals.first())
            assertTrue(police.setting, police.setting.startsWith("Včeraj popoldne"))
            assertEquals("B2", police.level)
            assertEquals("PC Graham", police.character)
        }
        val jacket = inPair(en, si.lanisce.lani.l10n.Lang.DE) { parseScenario(files.first { it.name == "the-leaky-jacket.json" }.readText()) }
        assertEquals("The leaky jacket · Die undichte Jacke", jacket.title)
        assertEquals("Hier ist der Kassenbon.", jacket.vocabularyHints.first { it.sl == "Here's the receipt." }.en)
        assertEquals("B1", jacket.level)
        assertEquals("Ryan", jacket.character)
        val maggie = inPair(en, si.lanisce.lani.l10n.Lang.IT) { parseScenario(files.first { it.name == "maggie-goes-to-london.json" }.readText()) }
        assertEquals("Maggie goes to London · Maggie va a Londra", maggie.title)
        assertTrue(maggie.setting, maggie.setting.startsWith("La più vecchia amica di nonna Maggie"))
        // the cast's own play theirs by name (Cast.talker): their sprite and voice, and the friendship grows
        val cast = mapOf(
            "a-gate-from-the-smithy" to "Mr Dixon", "the-night-the-beck-came-up" to "Auntie Edna", "maggie-goes-to-london" to "Granny Maggie",
            "a-summer-job-at-the-inn" to "Mrs Birkett", "jack-at-a-crossroads" to "Shepherd Jack",
        )
        for ((id, name) in cast) assertEquals(id, name, parseScenario(files.first { it.nameWithoutExtension == id }.readText()).character)
    }

    @Test fun `Jan's scenarios read in a German base too, and as they were in English`() {
        val dir = listOf(File("../../scenarios"), File("../scenarios"), File("companion/scenarios")).first { it.isDirectory }
        val raw = File(dir, "v-pekarni.json").readText()
        val german = inPair(si.lanisce.lani.l10n.Lang.SL, si.lanisce.lani.l10n.Lang.DE) { parseScenario(raw) }
        assertEquals("V pekarni · In der Bäckerei", german.title)
        assertTrue(german.setting, german.setting.startsWith("Früh am Morgen"))
        assertEquals("Guten Morgen! Was darf es sein?", german.openerEn)
        assertEquals("Was kostet das?", german.vocabularyHints.first { it.sl == "Koliko stane?" }.en)
        val english = inPair(si.lanisce.lani.l10n.Lang.SL, si.lanisce.lani.l10n.Lang.EN) { parseScenario(raw) }
        assertEquals("V pekarni · At the bakery", english.title)
        assertEquals("Greet the baker", english.goals.first())
        assertEquals("Good morning! What can I get you?", english.openerEn)
    }

    @Test fun `a served scenario's texts stay as they came, with who it is and its language`() {
        val s = parseScenario("""{"id":"al-mercato","title":"Al mercato · Auf dem Markt","setting":"Auf dem Markt.","role":"Signora Gianna, the seller","who":"La fruttivendola · Die Obstverkäuferin","goals":["Saluta · Begrüße","b","c"],"opener_sl":"Buongiorno!","opener_en":"Guten Morgen!","language":"it","audience":"child","packs":["al-mercato"]}""")
        assertEquals("Al mercato · Auf dem Markt", s.title)
        assertEquals("La fruttivendola · Die Obstverkäuferin", s.who)
        assertEquals(listOf("al-mercato"), s.packs)
        assertNull(scenario.who)
        assertNull(scenario.language)
    }

    @Test fun `parses an in-character reply`() {
        val r = reply("""{"sl":"Izvolite.","en":"Here you are.","correction":"**En kruh**","goals_done":[0,"1"],"end":true}""")
        assertEquals("Izvolite.", r.sl)
        assertEquals("Here you are.", r.en)
        assertEquals("**En kruh**", r.correction)
        assertEquals(listOf(0, 1), r.goalsDone)
        assertTrue(r.end)
        assertFalse(r.hint)
        assertTrue(r.inCharacter)
    }

    @Test fun `a reply without data falls back to its text`() {
        val r = reply(null, text = " Dober dan! ")
        assertEquals("Dober dan!", r.sl)
        assertNull(r.goalsDone)
        assertNull(r.correction)
        assertFalse(r.inCharacter)
        assertNull(reply("""{"sl":"x","correction":"  "}""").correction)
    }

    @Test fun `a conversation runs from opener to debrief`() {
        var st = Talk.start(scenario, now = 0, conversationId = "rp-1")
        assertEquals(1, st.lines.size)
        assertFalse(st.lines.single().fromMe)
        assertEquals(0, st.turn)

        st = Talk.said(st, " Dober dan. ", typed = false, now = 1_000)
        assertTrue(st.waiting)
        assertEquals("Dober dan.", st.lines.last().sl)
        val data = Talk.turnData(st, "Dober dan.", listOf("dober dan", "Dober dan.", " "))
        assertEquals("1", data["turn"]!!.jsonPrimitive.content)
        assertEquals(listOf("dober dan"), (data["alternatives"] as JsonArray).map { it.jsonPrimitive.content })

        st = Talk.replied(st, reply("""{"sl":"Dobro jutro! Kaj bo?","en":"What will it be?","goals_done":[0]}"""))
        assertFalse(st.waiting)
        assertEquals(setOf(0), st.goalsDone)
        assertEquals(3, st.lines.size)

        st = Talk.said(st, "Kruh prosim", typed = true, now = 2_000)
        st = Talk.replied(st, reply("""{"sl":"Izvolite.","correction":"Kruh, **prosim**.","goals_done":[1, 7],"end":true}"""))
        assertEquals("Kruh, **prosim**.", st.lines[3].correction) // on Jan's line, not the character's
        assertNull(st.lines[4].correction)
        assertEquals(setOf(0, 1), st.goalsDone) // out-of-range goal ignored
        assertTrue(st.sceneOver)
        assertEquals(listOf(Verdict.CORRECT, Verdict.ALMOST), Talk.verdicts(st))

        // Goals never untick, even if the tutor forgets one later.
        assertEquals(setOf(0, 1), Talk.replied(Talk.said(st, "Hvala", false, 3_000), reply("""{"sl":"Prosim.","goals_done":[]}""")).goalsDone)

        st = Talk.ending(st)
        val t = Talk.transcript(st, now = 5 * 60_000)
        assertEquals(5, t["duration_minutes"]!!.jsonPrimitive.content.toInt())
        assertEquals(2, t["turns"]!!.jsonPrimitive.content.toInt())
        val lines = (t["transcript"] as JsonArray).map { it as JsonObject }
        assertEquals(listOf("character", "jan", "character", "jan", "character"), lines.map { it["who"]!!.jsonPrimitive.content })
        assertEquals("true", lines[3]["typed"]!!.jsonPrimitive.content)

        // A late in-character line after the end is dropped; the debrief finishes the scene.
        assertSame(st, Talk.replied(st, reply("""{"sl":"Še kaj?"}""")))
        assertFalse(reply("""{"sl":"Bravo!","debrief":true}""").inCharacter)
        st = Talk.replied(st, reply(null, text = "**Bravo!** Practise *prosim*."))
        assertEquals(TalkPhase.DONE, st.phase)
        assertEquals("**Bravo!** Practise *prosim*.", st.debrief)
        assertSame(st, Talk.replied(st, reply(null, text = "again")))
    }

    @Test fun `a spoken line tells the tutor how it was heard`() {
        val st = Talk.said(Talk.start(scenario, now = 0, conversationId = "rp-1"), "Amico, come stai?", typed = false, now = 1)
        val data = Talk.turnData(st, "Amigo, come stai?", listOf("Amico, come stai?"), takes = 3, unclear = listOf("stai"))
        assertEquals("3", data["takes"]!!.jsonPrimitive.content)
        assertEquals(listOf("Amico, come stai?"), (data["alternatives"] as JsonArray).map { it.jsonPrimitive.content })
        assertEquals(listOf("stai"), (data["unclear"] as JsonArray).map { it.jsonPrimitive.content })
        assertNull(data["typed"])
        val once = Talk.turnData(st, "Ciao!", takes = 1)
        assertNull("one take is no news", once["takes"])
        val typed = Talk.turnData(st, "Ciao!", typed = true, unclear = listOf("x"))
        assertEquals("true", typed["typed"]!!.jsonPrimitive.content)
        assertNull("typed: nothing unclear", typed["unclear"])
    }

    @Test fun `the transcript keeps how many times a line was said`() {
        var st = Talk.start(scenario, now = 0, conversationId = "rp-1")
        st = Talk.said(st, "Al ruscello.", typed = false, now = 1, takes = 3)
        st = Talk.replied(st, reply("""{"sl":"Bene!"}"""))
        st = Talk.said(st, "Ciao!", typed = false, now = 2, takes = 1)
        val lines = (Talk.transcript(st, now = 60_000)["transcript"] as JsonArray).map { it as JsonObject }
        assertEquals("3", lines[1]["takes"]!!.jsonPrimitive.content)
        assertNull("said once: no news", lines[3]["takes"])
    }

    @Test fun `the recognizer gets the character's last two lines, the phrases and the names`() {
        var st = Talk.start(scenario, now = 0, conversationId = "rp-1")
        st = Talk.said(st, "Dober dan.", typed = false, now = 1)
        st = Talk.replied(st, reply("""{"sl":"Kaj bo?","en":"What will it be?"}"""))
        val c = Talk.sttContext(st, "Teo")
        assertEquals(listOf(scenario.openerSl, "Kaj bo?"), c.said)
        assertEquals(scenario.vocabularyHints.map { it.sl }, c.words)
        assertEquals(listOf("Teo", scenario.character), c.names)
        assertEquals(listOf(scenario.character), Talk.sttContext(st, null).names)
    }

    @Test fun `hints don't move the scene on`() {
        var st = Talk.start(scenario, now = 0)
        assertTrue(st.conversationId.startsWith("rp-"))
        val data = Talk.turnData(st, heard = null)
        assertEquals("true", data["hint"]!!.jsonPrimitive.content)
        assertEquals("1", data["turn"]!!.jsonPrimitive.content)
        assertNull(data["heard"])

        st = Talk.askedHint(st)
        assertTrue(st.hintLoading)
        st = Talk.replied(st, reply("""{"hint":true,"sl":"En kruh, prosim.","en":"A loaf, please."}"""))
        assertEquals(TalkHint("En kruh, prosim.", "A loaf, please."), st.hint)
        assertFalse(st.hintLoading)
        assertEquals(1, st.lines.size)
        assertEquals(1, st.hintsUsed)

        // A hint reply that forgot hint:true still counts as the hint.
        st = Talk.replied(Talk.askedHint(st), reply("""{"sl":"Dober dan."}"""))
        assertEquals("Dober dan.", st.hint?.sl)
        assertEquals(1, st.lines.size)

        st = Talk.said(st, "En kruh, prosim.", typed = false, now = 1)
        assertNull(st.hint)
    }
}
