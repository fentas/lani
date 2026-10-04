package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import si.lanisce.lani.game.scene.Numbers

/**
 * "🔍 Slovnica stavka · The sentence's grammar" (companion/SCENES.md): each word of a line with its reading, narrowed by
 * the sentence, and why that form with its page, on curated lines with the readings the lookup gives their words.
 */
class SentenceGrammarTest {
    /** Every page: the rows link what the rules name. */
    private val any = { _: String -> true }

    private fun r(lemma: String, pos: String, grammar: String? = null) = WordReading(lemma, pos, grammar)

    private fun rows(sentence: String, vararg readings: WordReading?) = SentenceGrammar.analyze(sentence, readings.toList(), any)

    @Test fun `a noun after a number, its genitive plural, after the number (5 and up), on the numbers page`() {
        val rows = rows("Deset jajc, prosim.", r("deset", "num"), r("jajce", "noun", "genitive dual/plural"), r("prositi", "verb", "first-person singular present"))
        assertEquals(listOf("Deset", "jajc", "prosim"), rows.map { it.word })
        assertNull(rows[0].why) // the number itself: only its form
        val eggs = rows[1]
        assertEquals("jajce", eggs.lemma)
        assertEquals("genitive plural", eggs.reading)
        assertEquals(Trigger.Count("Deset", 0, Numbers.MANY), eggs.why?.trigger)
        assertEquals(Case.GEN, eggs.why?.case)
        assertEquals("stevila-samostalniki", eggs.page)
        assertEquals("Koga? Česa? → 2. rodilnik (genitive) · after «Deset»: five and up, the genitive plural", SentenceGrammar.why(eggs.why!!))
        // a verb: who does it
        assertEquals("jaz", rows[2].why?.person)
        assertEquals("glagoli-sedanjik", rows[2].page)
        assertEquals("who: jaz", SentenceGrammar.why(rows[2].why!!))
    }

    @Test fun `a noun after v, where to, the accusative, even in its dictionary form`() {
        val rows = rows(
            "Seveda, pridi v šotor!",
            r("seveda", "adv"), r("priti", "verb", "second-person singular imperative"), r("v", "prep"), r("šotor", "noun"),
        )
        val tent = rows[3]
        assertEquals("accusative singular", tent.reading)
        assertEquals(CaseLine("Kam?", "hint.qWhereTo", Case.ACC), tent.why?.line)
        assertEquals("kam-tozilnik", tent.page)
        assertEquals("Kam? (where to? German: wohin?) → 4. tožilnik (accusative) · after «v»", SentenceGrammar.why(tent.why!!))
        assertEquals("velelnik", rows[1].page)
        assertNull(rows[2].why) // the preposition
        // where: the locative
        val forest = rows("Bil sem v gozdu.", r("biti", "verb", "masculine singular l-participle"), r("biti", "verb", "first-person singular present"), r("v", "prep"), r("gozd", "noun", "dative/locative singular"))
        assertEquals("locative singular", forest[3].reading)
        assertEquals("Kje?", forest[3].why?.line?.question)
        assertEquals("kje-mestnik-orodnik", forest[3].page)
        assertEquals("pretekli-cas", forest[0].page) // the l-form with sem: the past
    }

    @Test fun `a short pronoun to whom, and a verb that takes the dative, like German helfen`() {
        val cold = rows("Malo mi je mrzlo.", r("malo", "adv"), r("jaz", "pron", "dative singular"), r("biti", "verb", "third-person singular present"), r("mrzel", "adj"))
        assertEquals(Case.DAT, cold[1].why?.case)
        assertEquals(Trigger.Partner("mrzlo", 3, Case.DAT, "mrzlo"), cold[1].why?.trigger)
        assertEquals("dajalnik", cold[1].page)
        val help = rows("Pomagam babici.", r("pomagati", "verb", "first-person singular present"), r("babica", "noun", "dative/locative singular"))
        assertEquals("dative singular", help[1].reading)
        assertEquals("Komu? Čemu? → 3. dajalnik (dative) · with «Pomagam» (like German helfen + dative)", SentenceGrammar.why(help[1].why!!))
        // k: the dative
        val grandma = rows("Grem k babici.", r("iti", "verb", "first-person singular present"), r("k", "prep"), r("babica", "noun", "dative/locative singular"))
        assertEquals("dative singular", grandma[2].reading)
        assertEquals("dajalnik", grandma[2].page)
        assertEquals("imeti-iti", grandma[0].page)
    }

    @Test fun `a no before the object, the genitive, z and the instrumental`() {
        val bread = rows("Ne, nimam kruha.", r("ne", "particle"), r("imeti", "verb", "first-person singular negative present"), r("kruh", "noun", "genitive singular"))
        assertEquals(Trigger.Negation("nimam", 1), bread[2].why?.trigger)
        assertEquals("rodilnik-nikalnica", bread[2].page)
        assertEquals("Koga? Česa? → 2. rodilnik (genitive) · after the no «nimam»", SentenceGrammar.why(bread[2].why!!))
        val milk = rows("Kava z mlekom.", r("kava", "noun", "nominative singular"), r("z", "prep"), r("mleko", "noun", "instrumental singular"))
        assertEquals("S kom? S čim?", milk[2].why?.line?.question)
        assertEquals("orodnik", milk[2].page)
        assertNull(milk[0].why) // the nominative: only its form
    }

    @Test fun `without the dictionary, the rules that need no reading, and only pages the book has`() {
        val offline = SentenceGrammar.analyze("Iz Nemčije. Dvanajst krav.", emptyList(), any)
        assertEquals(CaseLine("Od kod?", "hint.qWhereFrom", Case.GEN), offline[1].why?.line)
        assertEquals("rodilnik-predlogi", offline[1].page)
        assertEquals(Numbers.MANY, (offline[3].why?.trigger as Trigger.Count).category)
        // a preposition of two cases needs the form: no reason without it
        assertNull(SentenceGrammar.analyze("Grem v šolo.", emptyList(), any)[2].why)
        // a page the book lacks isn't linked
        assertNull(SentenceGrammar.analyze("Iz Nemčije.", emptyList()) { false }[1].page)
    }

    @Test fun `a reading narrows by what the sentence decides`() {
        assertEquals("locative singular", WordReadings.narrow("dative/locative singular", listOf(Case.LOC, Case.ACC), null))
        assertEquals("nominative/accusative plural", WordReadings.narrow("nominative/accusative/vocative dual/plural; genitive singular", null, Numbers.FEW))
        assertEquals("nominative/accusative dual", WordReadings.narrow("nominative/accusative dual; dative/locative singular", null, Numbers.TWO))
        assertEquals("genitive plural", WordReadings.narrow("genitive dual/plural", null, Numbers.MANY))
        // nothing fits: as it was
        assertEquals("instrumental singular", WordReadings.narrow("instrumental singular", listOf(Case.GEN), null))
        // a verb's reading has no case: it stays
        assertEquals("first-person singular present", WordReadings.narrow("first-person singular present", listOf(Case.GEN), null))
        assertEquals(Case.GEN, WordReadings.case("genitive singular"))
        assertNull(WordReadings.case("nominative/accusative plural; genitive singular"))
        assertEquals("feminine", WordReadings.gender("genitive feminine singular"))
    }
}
