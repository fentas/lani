package si.lanisce.lani.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict

/** Italian's, German's and English's forms cards (data/FormsCard: otherPlans, otherKind; the bridge's forms-cards.ts). */
class FormsCardOtherTest {
    private fun slot(key: String, vararg forms: String, page: String, needs: List<String> = emptyList(), given: Boolean = false) =
        FormsCardSlot(key, forms.toList(), page = page, needs = needs, given = given)

    private val p6 = listOf("1sg", "2sg", "3sg", "1pl", "2pl", "3pl")

    private val haus = FormsCardData(
        "Haus", "noun", gender = "n", language = "de", gloss = listOf("house"),
        slots = listOf(
            slot("nom.sg", "Haus", page = "artikel-genus"), slot("gen.sg", "Hauses", page = "genitiv"), slot("dat.sg", "Haus", page = "dativ", given = true),
            slot("acc.sg", "Haus", page = "akkusativ", given = true), slot("nom.pl", "Häuser", page = "plural"), slot("gen.pl", "Häuser", page = "genitiv", needs = listOf("plural"), given = true),
            slot("dat.pl", "Häusern", page = "dativ", needs = listOf("plural")), slot("acc.pl", "Häuser", page = "akkusativ", needs = listOf("plural"), given = true),
        ),
    )
    private val parlare = FormsCardData(
        "parlare", "verb", language = "it", aux = listOf("avere"), gloss = listOf("to speak"),
        slots = p6.zip(listOf("parlo", "parli", "parla", "parliamo", "parlate", "parlano")).map { (p, f) -> slot("pres.$p", f, page = "presente-regolare") } +
            listOf(slot("pp", "parlato", page = "passato-prossimo")) +
            p6.zip(listOf("parlavo", "parlavi", "parlava", "parlavamo", "parlavate", "parlavano")).map { (p, f) -> slot("impf.$p", f, page = "imperfetto") },
    )

    @Test
    fun `a German noun's card lays its four cases out with their article, a case the same as the word given`() {
        val plan = FormsCard.layout(haus).single()
        assertEquals(listOf("1. wer? was?", "2. wessen?", "3. wem?", "4. wen? was?"), plan.rows.map { it.first })
        assertEquals(listOf("dem", "den"), plan.rows[2].second.map { it!!.before })
        val tested = FormsCard.testedKeys(haus, setOf("artikel-genus", "genitiv", "dativ", "akkusativ", "plural"))
        assertEquals(listOf("gen.sg", "nom.pl", "dat.pl"), tested)
        assertTrue(haus.whole)
    }

    @Test
    fun `an Italian verb's card has its tenses by person and its participle once, after its auxiliary`() {
        val plans = FormsCard.layout(parlare)
        assertEquals(listOf("wordForms.present", "wordForms.perfect", "wordForms.imperfect"), plans.map { it.title })
        assertEquals("io", plans[0].rows[0].second[0]!!.before)
        assertEquals("noi", plans[0].rows[0].second[1]!!.before)
        assertEquals("lui ha", plans[1].rows.single().second.single()!!.before)
    }

    @Test
    fun `a wrong cell's kind is the tense's, the person's or the case's of its language`() {
        assertEquals("tense_imperfect", FormsCard.kind(parlare, "impf.1sg", "parlo"))
        assertEquals("verb_person_1sg", FormsCard.kind(parlare, "pres.1sg", "parla"))
        assertEquals("tense_participle", FormsCard.kind(parlare, "pp", "parlo"))
        assertEquals("number_plural", FormsCard.kind(haus, "dat.pl", "Haus"))
        assertEquals("case_genitive", FormsCard.kind(haus, "gen.sg", "Haus"))
        assertTrue(FormsCard.KINDS.containsAll(listOf("tense_imperfect", "tense_participle", "number_plural", "case_genitive")))
    }

    @Test
    fun `a gap is graded in its card's language`() {
        val g = FormsGap("dat.pl", "Häusern", listOf("Häusern"), listOf("Häusern", "Häuser", "Hauses"), chosen = false, page = "dativ", where = "", before = "den", language = "de")
        assertEquals(Verdict.CORRECT, FormsCard.grade(g, "Häusern", typed = true).verdict)
        assertEquals(Verdict.CORRECT, FormsCard.grade(g, "den Häusern", typed = true).verdict)
        assertEquals(Verdict.WRONG, FormsCard.grade(g, "Häuser", typed = true).verdict)
    }

    @Test
    fun `forms cards are on for Slovene's feature, and for the other languages' own`() {
        assertTrue(FormsCard.has("sl", listOf(FormsCard.FEATURE)))
        assertTrue(FormsCard.has("it", listOf(FormsCard.OTHER_FEATURE)))
        assertFalse(FormsCard.has("it", listOf(FormsCard.FEATURE)))
        assertFalse(FormsCard.has("sl", listOf(FormsCard.OTHER_FEATURE)))
        assertFalse(FormsCard.has("fr", listOf(FormsCard.FEATURE, FormsCard.OTHER_FEATURE)))
    }

    @Test
    fun `a card's word is found without its article, the core verbs of its language first`() {
        val cards = listOf(
            ReviewCard("vocab_a_mangiare", "mangiare", "essen", repetitions = 3),
            ReviewCard("vocab_a_essere", "essere", "sein", repetitions = 1),
            ReviewCard("vocab_a_casa", "la casa", "Haus", repetitions = 5),
        )
        val entries = { _: String -> listOf(si.lanisce.lani.data.LemmaForms("x", "verb", slots = (1..6).map { FormSlot("pres.$it", listOf("x$it"), page = "p") })) }
        val got = FormsCard.candidates(cards, cards.map { it.id }.toSet(), emptyList(), entries, language = "it")
        assertEquals(listOf("essere", "mangiare", "casa"), got.map { it.first })
    }
}
