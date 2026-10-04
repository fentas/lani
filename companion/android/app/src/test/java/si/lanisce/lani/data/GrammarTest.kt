package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.game.TentMove
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.io.File

/**
 * The grammar book's pages (companion/README.md, "Grammar book"): the bundled Slovene ones, each base language's text,
 * what names a page (exercises, the tent challenge, cards and mistakes by their tags), and what "🎯 Vadi" plays.
 */
class GrammarTest {
    /** Who reads each language's pages: the bases its title, rule and examples are in (English always). */
    private val BASES = mapOf("sl" to listOf("en", "de", "it"), "it" to listOf("sl", "en", "de"), "de" to listOf("sl", "en", "it"), "en" to listOf("sl", "it", "de"))
    private val pages = Grammar.bundled("sl")
    private fun page(id: String) = checkNotNull(pages.firstOrNull { it.id == id }) { "no page $id" }

    // --- the bundled pages -------------------------------------------------------------------------------------------

    @Test fun `the app bundles the 44 Slovene pages, in the book's order, and none of a language without them`() {
        assertEquals(44, pages.size)
        // the files' order, as the bridge reads them: rodilnik-predlogi.json before rodilnik.json
        assertEquals(pages.map { it.id }.sortedBy { "$it.json" }, pages.map { it.id })
        assertTrue(pages.all { it.language == "sl" && it.source == Grammar.CURATED && it.machineWritten && !it.byTutor })
        assertEquals(emptyList<GrammarPage>(), Grammar.bundled("xx"))
    }

    @Test fun `Italian, German and English have 16 pages each, and the Italian tent's page is there`() {
        for (lang in listOf("it", "de", "en")) assertEquals(lang, 16, Grammar.bundled(lang).size)
        val dove = Grammar.bundled("it").first { it.id == "dove-preposizioni" }
        // the second learner's view: Italian, explained in Slovene
        assertEquals("${dove.target} · ${dove.titleIn("sl")}", dove.titleShown(LangPair(Lang.IT, Lang.SL)))
        assertTrue(dove.ruleIn("sl").isNotBlank() && dove.ruleIn("sl") != dove.ruleIn("en"))
        assertTrue(dove.examples.any { it.target("it").contains("vicino allo stagno") })
    }

    @Test fun `every page a culture pack or a scene names is a page of its language's book`() {
        fun names(e: kotlinx.serialization.json.JsonElement): List<String> = when (e) {
            is kotlinx.serialization.json.JsonObject -> e.flatMap { (k, v) -> if (k == "grammar" && v is kotlinx.serialization.json.JsonPrimitive && v.isString) listOf(v.content) else names(v) }
            is kotlinx.serialization.json.JsonArray -> e.flatMap(::names)
            else -> emptyList()
        }
        val named = mutableMapOf<String, Int>()
        for (c in File("../../cultures").listFiles { f -> File(f, "culture.json").isFile }!!.sortedBy { it.name }) {
            val lang = (json.parseToJsonElement(File(c, "culture.json").readText()) as kotlinx.serialization.json.JsonObject)["language"].toString().trim('"')
            val ids = Grammar.bundled(lang).map { it.id }.toSet()
            val files = listOf(File(c, "quests.json"), File(c, "surprises.json")) + File(c, "scenes").listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            for (f in files) for (g in names(json.parseToJsonElement(f.readText()))) {
                assertTrue("${c.name}/${f.name}: $g", g in ids)
                named[c.name] = (named[c.name] ?: 0) + 1
            }
        }
        val sl = pages.map { it.id }.toSet()
        for (f in File("../../scenes").listFiles { f -> f.name.endsWith(".json") }!!) for (g in names(json.parseToJsonElement(f.readText()))) {
            assertTrue("scenes/${f.name}: $g", g in sl)
            named["scenes"] = (named["scenes"] ?: 0) + 1
        }
        // every village's requests, questions and scenes lead into its book; the Slovene scenes most of all
        for (c in listOf("primorska", "friuli", "kaernten", "lakeland")) assertTrue("$c: $named", (named[c] ?: 0) >= 20)
        assertTrue("$named", (named["scenes"] ?: 0) >= 200)
    }

    @Test fun `every bundled book's pages are of its language, read in each of its bases, and see only its own`() {
        for ((lang, bases) in BASES) {
            val book = Grammar.bundled(lang)
            val ids = book.map { it.id }.toSet()
            assertEquals(File("../../grammar/$lang").listFiles { f -> f.name.endsWith(".json") }.orEmpty().size, book.size)
            for (p in book) {
                assertTrue("${p.id}: $lang", p.language == lang && p.source == Grammar.CURATED && p.machineWritten)
                for (b in bases) {
                    assertTrue("${p.id} title $b", p.title.containsKey(b))
                    assertTrue("${p.id} rule $b", p.rule.containsKey(b))
                    assertTrue("${p.id} examples $b", p.examples.all { it.texts.containsKey(b) && it.target(lang).isNotBlank() })
                    assertTrue("${p.id} table $b", p.table?.let { t -> (t.head + t.rows.flatten()).all { it.isWords || it.text!!.containsKey(b) } } ?: true)
                }
                assertTrue("${p.id} sees ${p.see}", p.see.all { it in ids })
            }
        }
    }

    // --- what names a page -------------------------------------------------------------------------------------------

    @Test fun `a scene's turn and wrong choice keep their grammar page, read in any pair`() {
        // Babica Micka's kitchen, its first turn and that turn's first wrong choice naming a page each (the file's own
        // pages on its other choices stay as they are)
        val raw = File("../../scenes/kuhinja.json").readText()
            .replaceFirst("\"why\": {", "\"grammar\": \"naslonke\", \"why\": {")
            .replaceFirst("{ \"choices\": [", "{ \"grammar\": \"tozilnik\", \"choices\": [")
        val o = json.parseToJsonElement(raw) as kotlinx.serialization.json.JsonObject
        for (pair in listOf(LangPair.DEFAULT, LangPair(Lang.SL, Lang.DE), LangPair(Lang.SL, Lang.IT))) {
            val scene = json.decodeFromJsonElement(si.lanisce.lani.game.scene.SceneSpec.serializer(), si.lanisce.lani.game.scene.inPair(o, pair))
            val turn = scene.dialogs.first().lines.first { it.choices.isNotEmpty() }
            assertEquals("tozilnik", turn.grammar)
            assertEquals(listOf(null, "naslonke"), turn.choices.take(2).map { it.grammar })
        }
    }

    @Test fun `every page has its title, rule and examples in English, German and Italian, and a table`() {
        for (p in pages) for (l in listOf("en", "de", "it")) {
            assertTrue("${p.id} title $l", p.title.containsKey(l))
            assertTrue("${p.id} rule $l", p.rule.containsKey(l))
            assertTrue("${p.id} examples $l", p.examples.isNotEmpty() && p.examples.all { it.texts.containsKey(l) && it.target("sl").isNotBlank() })
        }
        assertTrue(pages.all { it.table != null && it.table!!.rows.all { r -> r.size == it.table!!.head.size } })
    }

    @Test fun `a page reads in the learner's base, the rule, the title, a table's cells, an example's translation and note`() {
        val kje = page("kje-mestnik-orodnik")
        assertEquals("Kje? Predlogi kraja", kje.target)
        assertEquals("Kje? Predlogi kraja · Where? Prepositions of place", kje.titleShown(LangPair.DEFAULT))
        assertEquals("Kje? Predlogi kraja · Wo? Präpositionen des Ortes", kje.titleShown(LangPair(Lang.SL, Lang.DE)))
        assertTrue(kje.ruleIn("en").startsWith("To say where something is"))
        assertTrue(kje.ruleIn("de").startsWith("Wenn man sagt, wo etwas ist"))
        assertTrue(kje.ruleIn("it").startsWith("Per dire dove si trova qualcosa"))
        // a base the page lacks: English
        assertEquals(kje.ruleIn("en"), kje.ruleIn("fr"))
        val table = checkNotNull(kje.table)
        assertEquals(listOf("preposition", "case", "example"), table.head.map { it.shown("en") })
        assertEquals(listOf("Präposition", "Fall", "Beispiel"), table.head.map { it.shown("de") })
        val ob = table.rows.first { it[0].words == "ob" }
        assertEquals("locative (mestnik)", ob[1].shown("en"))
        assertEquals("locativo (mestnik)", ob[1].shown("it"))
        assertTrue(ob[2].isWords && ob[2].shown("de") == "ob ribniku")
        val first = kje.examples.first()
        assertEquals("Šotor bo stal ob ribniku.", first.target("sl"))
        assertEquals("Das Zelt wird am Teich stehen.", first.meaning("de"))
        val spruce = kje.examples.first { it.target("sl").startsWith("Pod veliko smreko") }
        assertTrue(spruce.noteIn("it")!!.contains("strumentale"))
    }

    @Test fun `a page sees only pages there are, and links only curated modules`() {
        val ids = pages.map { it.id }.toSet()
        for (p in pages) assertTrue("${p.id} sees ${p.see}", p.see.all { it in ids && it != p.id })
        val modules = File("../../modules").listFiles { f -> f.name.endsWith(".json") }!!.map { it.nameWithoutExtension }.toSet()
        for (p in pages) assertTrue("${p.id} links ${p.modules}", p.modules.all { it in modules })
    }

    // --- what names a page -------------------------------------------------------------------------------------------

    @Test fun `every exercise of the curated modules names a bundled page, and keeps it when parsed`() {
        val ids = pages.map { it.id }.toSet()
        val files = File("../../modules").listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
        assertEquals(6, files.size)
        for (f in files) {
            // served as the bridge serves a curated module: as version 1
            val m = parseModule(f.readText().replaceFirst("{", "{\"version\": 1, "))
            assertTrue("${f.name}: ${m.exercises.map { it.grammar }}", m.exercises.all { it.grammar in ids })
        }
    }

    @Test fun `the tent challenge's questions name their pages, heard or read`() {
        for (canSpeak in listOf(true, false)) {
            val ex = TentMove.exercises(3, canSpeak)
            // ob ribniku: the locative; pod veliko smreko, pred šotorom: the instrumental of place; blizu pomola
            assertEquals(listOf("kje-mestnik-orodnik", "orodnik", "rodilnik-predlogi", "orodnik"), ex.map { it.grammar })
        }
    }

    @Test fun `a tag names whole words in a row, as the bridge matches them`() {
        assertTrue(Grammar.tagMatches("accusative", "accusative_feminine_a_to_o"))
        assertTrue(Grammar.tagMatches("grammar_biti", "grammar_biti"))
        assertTrue(Grammar.tagMatches("iz", "vocab_iz_genitive_fem"))
        assertTrue(Grammar.tagMatches("dual", "vocab_biti_dual"))
        assertFalse(Grammar.tagMatches("od", "food"))
        assertFalse(Grammar.tagMatches("genitive", "genitives"))
        assertFalse(Grammar.tagMatches("dual_verb", "dual"))
    }

    /** Jan's kinds of cards and mistakes (mistakes-db, spaced-repetition), as the dashboard has them. */
    private val pool = listOf(
        ReviewCard("vocab_dobra_kava", "dober/dobra/dobro", "adjective endings", kind = "grammar_rule", category = "grammar_gender", repetitions = 3, lastQuality = 4),
        ReviewCard("vocab_iz_genitive_fem", "iz Ljubljane / iz Gorice", "-a → -e after iz", kind = "grammar_rule", category = "grammar_cases", repetitions = 2, lastQuality = 4),
        ReviewCard("vocab_biti_dual", "midva sva · vidva sta", "we two are", kind = "grammar", category = "grammar_biti", repetitions = 1, lastQuality = 2),
        // vocabulary never belongs to a page, whatever its category
        ReviewCard("vocab_ob-ognju_ogenj", "ogenj", "fire", category = "iz"),
    )
    private val mistakes = listOf(
        MistakeNote("accusative_feminine_a_to_o", 1, wrongRun = 1, examples = listOf("Lahko ena kava?" to "Lahko dobim eno kavo?")),
        MistakeNote("dual_verb_forms", 1, rightRun = 2, examples = listOf("Midva je doma" to "Midva sva doma")),
        MistakeNote("clitic_placement_se", 2, wrongRun = 2),
    )

    @Test fun `a page's cards and mistakes are the grammar ones its tags name`() {
        assertEquals(listOf("vocab_dobra_kava"), Grammar.cards(page("pridevniki-ujemanje"), pool).map { it.id })
        assertEquals(listOf("vocab_iz_genitive_fem"), Grammar.cards(page("rodilnik-predlogi"), pool).map { it.id })
        assertEquals(listOf("vocab_biti_dual"), Grammar.cards(page("biti"), pool).map { it.id })
        assertEquals(listOf("vocab_biti_dual"), Grammar.cards(page("dvojina"), pool).map { it.id })
        assertEquals("pridevniki-ujemanje", Grammar.forCard(pool[0], pages)?.id)
        assertNull(Grammar.forCard(pool[3], pages))
        assertEquals(listOf("accusative_feminine_a_to_o"), Grammar.mistakes(page("tozilnik"), mistakes).map { it.id })
        assertEquals(listOf("dual_verb_forms"), Grammar.mistakes(page("dvojina"), mistakes).map { it.id })
        assertEquals(listOf("clitic_placement_se"), Grammar.mistakes(page("naslonke"), mistakes).map { it.id })
        assertEquals(emptyList<MistakeNote>(), Grammar.mistakes(page("vprasalnice"), mistakes))
        assertEquals(listOf("kam-tozilnik", "kje-mestnik-orodnik", "vprasalnice"), Grammar.ofModule("predlogi-kraja", pages).map { it.id })
    }

    // --- what "🎯 Vadi" plays ------------------------------------------------------------------------------------------

    private fun choice(prompt: String, grammar: String?) = Exercise.Choice(prompt, listOf("a", "b"), 0, grammar = grammar)

    @Test fun `practice plays the exercises that name the page first, the challenge's too, then its modules' others, at most 8`() {
        val kje = page("kje-mestnik-orodnik")
        val modules = List(6) { "predlogi-kraja" to choice("kje $it", "kje-mestnik-orodnik") } +
            List(4) { "predlogi-kraja" to choice("kam $it", "kam-tozilnik") } +
            List(3) { "tozilnik-osnove" to choice("tož $it", "tozilnik") }
        val tent = TentMove.exercises(3, canSpeak = true)
        val p = Grammar.practice(kje, modules, tent, pool, seed = 1) as Grammar.Practice.Exercises
        assertEquals(Grammar.PRACTICE_MAX, p.exercises.size)
        // the six module exercises and the tent question that names it (ob ribniku) come before any other, then the
        // linked module's others
        assertTrue(p.exercises.take(7).all { it.grammar == "kje-mestnik-orodnik" })
        assertEquals("kam-tozilnik", p.exercises.last().grammar)
        // few that name it: the linked module's others fill up; another module's never
        val few = Grammar.practice(kje, modules.drop(4), emptyList(), pool, seed = 1) as Grammar.Practice.Exercises
        assertEquals(listOf("kje-mestnik-orodnik", "kje-mestnik-orodnik"), few.exercises.take(2).map { it.grammar })
        assertEquals(6, few.exercises.size)
        assertTrue(few.exercises.none { it.grammar == "tozilnik" })
        // an exercise the app can't grade by itself isn't practice here
        val free = Grammar.practice(page("tozilnik"), listOf("x" to Exercise.Free("Tell me", "rubric", grammar = "tozilnik")), emptyList(), emptyList(), 1)
        assertEquals(Grammar.Practice.Ask, free)
    }

    @Test fun `practice falls back to the learner's grammar cards on the page, then asks the tutor`() {
        val cards = Grammar.practice(page("pridevniki-ujemanje"), emptyList(), emptyList(), pool, seed = 1)
        assertEquals(Grammar.Practice.Cards(listOf(pool[0])), cards)
        assertEquals(Grammar.Practice.Ask, Grammar.practice(page("vprasalnice"), emptyList(), emptyList(), pool, seed = 1))
    }

    // --- the bridge's pages ------------------------------------------------------------------------------------------

    @Test fun `the bridge's pages come first, the tutor's marked, and the bundled ones it lacks stay`() {
        val raw = """[
          {"id": "kje-mestnik-orodnik", "language": "sl", "level": "A1", "emoji": "📍", "title": {"sl": "Kje? Predlogi kraja", "en": "Where?"},
           "rule": {"en": "The rule."}, "more": {"en": "Kje? is a photo, Kam? a film."},
           "examples": [{"sl": "Šotor stoji ob ribniku.", "en": "x"}, {"sl": "Luka sedi pred šotorom.", "en": "Luka sits in front of the tent.", "by": "tutor"}],
           "source": "curated", "extended": true, "newer_field": 1},
          {"id": "s-z", "language": "sl", "level": "A2", "title": {"sl": "S, z", "en": "With"}, "rule": {"en": "z + instrumental"},
           "table": {"head": [{"en": "a"}, {"en": "b"}], "rows": [["z", "z mlekom"], ["too short"]]},
           "examples": [{"sl": "Kavo pijem z mlekom.", "en": "With milk."}], "source": "tutor"},
          {"id": "broken", "title": {"en": "No rule"}}
        ]"""
        val bridge = Grammar.parseList(raw)
        assertEquals(listOf("kje-mestnik-orodnik", "s-z"), bridge.map { it.id })
        val kje = bridge[0]
        assertTrue(kje.extended && !kje.byTutor && kje.examples[1].byTutor && !kje.examples[0].byTutor)
        assertEquals("Kje? is a photo, Kam? a film.", kje.moreIn("de"))
        val sz = bridge[1]
        assertTrue(sz.byTutor)
        assertEquals(1, sz.table!!.rows.size)
        val merged = Grammar.merge(pages, bridge)
        assertEquals(pages.size + 1, merged.size)
        assertEquals(listOf("kje-mestnik-orodnik", "s-z"), merged.take(2).map { it.id })
        assertEquals(1, merged.count { it.id == "kje-mestnik-orodnik" })
        assertEquals(pages, Grammar.merge(pages, null))
    }

    @Test fun `an exercise's grammar page survives the module's parse, and an older module has none`() {
        val m = parseModule("""{"id": "m", "version": 1, "title": "t", "level": "A1", "exercises": [
            {"type": "cloze", "grammar": "tozilnik", "text": "Pijem ___.", "accept": ["kavo"]},
            {"type": "cloze", "text": "Jem ___.", "accept": ["juho"]}]}""")
        assertEquals(listOf("tozilnik", null), m.exercises.map { it.grammar })
        assertNotNull(m.exercises[0] as? Exercise.Cloze)
    }
}
