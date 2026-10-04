package si.lanisce.lani.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.culture.Cultures
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/** "📖 Preberi · Read": a chest reading's lines in the learner's pair, the translation toggle, the questions and answers. */
class ReadingLogicTest {
    private val primorska = Cultures.load("primorska").readings
    private val friuli = Cultures.load("friuli").readings

    @Test fun `a recipe reads as its intro, servings, ingredients and numbered steps`() {
        val lines = ReadingLogic.lines(primorska.getValue("frtalja"), Lang.SL, Lang.EN)
        assertEquals(ReadLine.Kind.INTRO, lines[0].kind)
        assertEquals("Za 4 osebe" to "Serves 4", lines[1].target to lines[1].base)
        val ingredients = lines.filter { it.kind == ReadLine.Kind.INGREDIENT }
        assertEquals("8 jajc" to "8 eggs", ingredients[0].target to ingredients[0].base)
        assertEquals("4 žlice moke" to "4 tablespoons flour", ingredients[1].target to ingredients[1].base)
        assertEquals("pest svežih zelišč (melisa, meta, peteršilj, drobnjak)", ingredients[3].target)
        assertEquals("2 žlici oljčnega olja", ingredients[4].target) // the dual
        assertEquals("sol in poper po okusu" to "salt and pepper to taste", ingredients[5].target to ingredients[5].base)
        val steps = lines.filter { it.kind == ReadLine.Kind.STEP }
        assertEquals((1..9).toList(), steps.map { it.number })
        assertEquals("Jajca razbij v skledo." to "Crack the eggs into a bowl.", steps[0].target to steps[0].base)
        assertTrue(lines.none { it.kind == ReadLine.Kind.LINE })
        assertFalse(lines[1].spoken)
        assertTrue(steps.all { it.spoken })
    }

    @Test fun `the potica's ingredients come in parts, the dough, the filling, to brush it`() {
        val lines = ReadingLogic.lines(primorska.getValue("potica"), Lang.SL, Lang.EN)
        assertEquals(listOf("Testo · The dough", "Nadev · The filling", "Za premaz · To brush it"), lines.filter { it.kind == ReadLine.Kind.PART }.map { "${it.target} · ${it.base}" })
        assertEquals(ReadLine.Kind.INGREDIENT, lines[lines.indexOfFirst { it.kind == ReadLine.Kind.PART } + 1].kind)
        assertTrue(lines.any { it.target == "ščepec soli" && it.base == "a pinch of salt" })
        assertTrue(lines.any { it.target == "2 rumenjaka" })
    }

    @Test fun `friuli's recipe reads in Italian with Slovene for the second learner`() {
        val lines = ReadingLogic.lines(friuli.getValue("frico"), Lang.IT, Lang.SL)
        val first = lines.first { it.kind == ReadLine.Kind.INGREDIENT }
        assertEquals("500 g di patate" to "500 g krompirja", first.target to first.base)
        assertTrue(lines.any { it.target == "un bicchiere d'acqua" && it.base == "kozarec vode" })
        assertEquals("Sbuccia le patate e tagliale a fettine sottili.", lines.first { it.kind == ReadLine.Kind.STEP }.target)
    }

    @Test fun `a proverb shows with what it means`() {
        val lines = ReadingLogic.lines(primorska.getValue("knjiga-pregovorov"), Lang.SL, Lang.EN).filter { it.kind == ReadLine.Kind.LINE }
        val first = lines.first()
        assertEquals("Brez muje se še čevelj ne obuje.", first.target)
        assertEquals("Brez truda ni uspeha." to "Nothing comes without effort.", first.meansTarget to first.meansBase)
        assertNull(first.number)
        assertTrue(lines.all { it.meansTarget != null })
    }

    @Test fun `the translation shows, hides to read first, and opens line by line`() {
        val v = ReadingView()
        assertTrue(v.shows(0) && v.shows(5))
        val hidden = v.toggle()
        assertFalse(hidden.shows(0))
        val one = hidden.reveal(3)
        assertTrue(one.shows(3))
        assertFalse(one.shows(4))
        assertEquals(one, one.reveal(3))
        // shown again, and hidden again: every line hidden, none still open
        val again = one.toggle().toggle()
        assertFalse(again.shows(3))
        assertEquals(v, v.reveal(2)) // all shown: nothing to open
    }

    @Test fun `the questions have options in an order of their own, and the first pick counts`() {
        val r = primorska.getValue("frtalja")
        val qs = ReadingLogic.questions(r, Lang.SL, Lang.EN)
        assertEquals(2, qs.size)
        assertEquals("Koliko jajc potrebuješ?" to "How many eggs do you need?", qs[0].ask to qs[0].askBase)
        assertEquals("8", qs[0].answer)
        assertEquals(setOf("8", "4", "2"), qs[0].options.toSet())
        assertEquals(qs, ReadingLogic.questions(r, Lang.SL, Lang.EN)) // the same order every time
        // across the readings the answer isn't always first
        val all = (primorska.values + friuli.values).flatMap { ReadingLogic.questions(it, Lang.SL, Lang.EN) }
        assertTrue(all.any { it.options.first() != it.answer })
        assertTrue(all.all { it.answer in it.options && it.options.size == it.options.toSet().size })

        var a = ReadAnswers()
        assertFalse(a.complete(qs))
        a = a.pick(0, "4").pick(0, "8") // the first pick counts
        assertEquals(false, a.right(qs, 0))
        assertNull(a.right(qs, 1))
        assertFalse(a.complete(qs))
        a = a.pick(1, qs[1].answer)
        assertTrue(a.complete(qs))
        assertEquals(listOf(Verdict.WRONG, Verdict.CORRECT), a.verdicts(qs))
        assertEquals(1, a.score(qs))
        assertNotEquals(qs[0].explain, qs[0].explainBase)
    }

    @Test fun `each kind has its label`() {
        assertEquals(
            listOf("reading.kindRecipe", "reading.kindProverbs", "reading.kindLetter", "reading.kindPage", "reading.kindStory", "reading.kindArticle"),
            si.lanisce.lani.game.Readings.KINDS.map { ReadingLogic.kindKey(it) },
        )
    }

    /** A page of the reading corner: a question of every type, and two new words. */
    private val page = kotlinx.serialization.json.Json.decodeFromString(
        si.lanisce.lani.game.culture.ReadingFile.serializer(),
        """
        {"id": "jutro", "kind": "page", "level": "A1", "title": {"sl": "Jutro", "en": "Morning"},
         "lines": [{"text": {"sl": "Zjutraj Micka odpre okno.", "en": "In the morning Micka opens the window."}},
                   {"text": {"sl": "Sonce že sije.", "en": "The sun is already shining."}}],
         "questions": [
           {"type": "main_idea", "ask": {"sl": "O čem govori?", "en": "What is it about?"}, "options": ["O jutru", "O zimi", "O šoli"], "explain": {"sl": "…", "en": "…"}},
           {"type": "word", "word": "sije", "ask": {"sl": "Kaj pomeni »sije«?", "en": "What does »sije« mean?"}, "options": ["Sveti", "Dežuje", "Spi"], "explain": {"sl": "…", "en": "…"}},
           {"type": "true_false", "ask": {"sl": "Micka zapre okno.", "en": "Micka closes the window."}, "answer": false, "explain": {"sl": "…", "en": "…"}},
           {"type": "true_false", "ask": {"sl": "Sonce sije.", "en": "The sun shines."}, "answer": true, "explain": {"sl": "…", "en": "…"}}
         ],
         "words": [{"word": "odpreti", "form": "odpre", "pos": "verb", "means": {"en": "to open"}}, {"word": "sonce", "means": {"en": "the sun"}}]}
        """.trimIndent(),
    )

    @Test fun `a true-false statement offers true and false, in that order`() {
        val qs = ReadingLogic.questions(page, Lang.SL, Lang.EN, yes = "Drži", no = "Ne drži")
        assertEquals(listOf("main_idea", "word", "true_false", "true_false"), qs.map { it.type })
        assertEquals(listOf("Drži", "Ne drži"), qs[2].options)
        assertEquals("Ne drži", qs[2].answer)
        assertEquals("Drži", qs[3].answer)
        val a = ReadAnswers().pick(2, "Ne drži").pick(3, "Ne drži")
        assertEquals(true to false, a.right(qs, 2) to a.right(qs, 3))
        // the others: 3 options in an order of their own, the answer the file's first
        assertEquals("O jutru", qs[0].answer)
        assertEquals(setOf("O jutru", "O zimi", "O šoli"), qs[0].options.toSet())
        assertEquals(
            listOf("reading.typeMainIdea", "reading.typeWord", "reading.typeTrueFalse", null),
            listOf("main_idea", "word", "true_false", null).map(ReadingLogic::typeKey),
        )
    }

    @Test fun `a reading's new words come with their meaning and the line they stand in`() {
        val words = ReadingLogic.words(page, Lang.SL, Lang.EN)
        assertEquals(listOf("odpreti", "sonce"), words.map { it.word })
        assertEquals("odpre" to "to open", words[0].form to words[0].means)
        assertEquals("Zjutraj Micka odpre okno." to "In the morning Micka opens the window.", words[0].line to words[0].lineBase)
        assertEquals("Sonce že sije.", words[1].line) // its form is the word, any case
        // Micka's recipe: its words in an ingredient and in a step
        val frtalja = ReadingLogic.words(primorska.getValue("frtalja"), Lang.SL, Lang.EN)
        assertEquals(listOf("jajce", "zelišče", "ponev", "obrniti"), frtalja.map { it.word })
        assertEquals("8 jajc" to "8 eggs", frtalja[0].line to frtalja[0].lineBase)
        assertEquals("V veliki ponvi segrej olje." to "a frying pan", frtalja[2].line to frtalja[2].means)
    }

    // --- read first, then answer --------------------------------------------------------------------------------------

    @Test fun `read first, then the questions one at a time, the text shown again counted while a question is open`() {
        var run = ReadRun()
        assertFalse(run.asking)
        // looking before starting is reading, not looking back
        assertEquals(0, run.peek(open = true).looks)
        run = run.start()
        assertTrue(run.asking)
        assertEquals(0 to false, run.at to run.peeking)
        run = run.peek(open = true)
        assertEquals(true to 1, run.peeking to run.looks)
        // tapped again, it hides; hiding isn't a look
        run = run.peek(open = true)
        assertEquals(false to 1, run.peeking to run.looks)
        // the question answered, the text is there to check, free
        run = run.peek(open = false)
        assertEquals(true to 1, run.peeking to run.looks)
        // the next question hides it again
        run = run.next()
        assertEquals(Triple(1, false, 1), Triple(run.at, run.peeking, run.looks))
        run = run.peek(open = true).peek(open = true).peek(open = true)
        assertEquals(true to 3, run.peeking to run.looks)
    }

    @Test fun `the result says kindly how often the text was looked at, and what it cost`() {
        val was = L10n.pair
        L10n.pair = LangPair.DEFAULT
        try {
            assertNull(looksText(0, 0, pays = true))
            assertEquals("👁 En pogled v besedilo, brez odbitka · You looked at the text once, at no cost", looksText(1, 0, pays = true))
            assertEquals("👁 Dva pogleda v besedilo, brez odbitka · You looked at the text twice, at no cost", looksText(2, 0, pays = true))
            assertEquals("👁 3 pogledi v besedilo: −5 % plačila · You looked at the text 3 times: −5 % of the pay", looksText(3, 5, pays = true))
            assertEquals("👁 5 pogledov v besedilo: −15 % plačila · You looked at the text 5 times: −15 % of the pay", looksText(5, 15, pays = true))
            // a book, a reading read before: nothing to pay, nothing to cost
            assertEquals("👁 Dva pogleda v besedilo · You looked at the text twice", looksText(2, 0, pays = false))
            // what the read phase says about looking back, by level
            assertTrue(peekRuleText("A1", pays = true).contains("Prva dva pogleda sta brezplačna"))
            assertTrue(peekRuleText("A1", pays = true).contains("The first two looks are free"))
            assertTrue(peekRuleText("B1", pays = true).contains("Prvi pogled je brezplačen"))
            assertEquals(
                "Med vprašanji lahko besedilo spet pogledaš. · While answering, you can look at the text again.",
                peekRuleText("A1", pays = false),
            )
            // every pair reads it with its numbers filled
            for (p in Lang.entries.flatMap { t -> Lang.entries.filter { it != t }.map { LangPair(t, it) } }) {
                L10n.pair = p
                for (n in 1..6) for (text in listOfNotNull(looksText(n, 10, true), looksText(n, 0, true), looksText(n, 0, false))) {
                    assertFalse("$p $n: $text", text.contains('{') || text.contains('#'))
                }
                assertFalse(peekRuleText("A1", true).contains('#'))
            }
        } finally {
            L10n.pair = was
        }
    }
}
