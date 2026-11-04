package si.lanisce.lani.game

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.app.CachedForms
import si.lanisce.lani.data.FormLine
import si.lanisce.lani.data.FormSlot
import si.lanisce.lani.data.LemmaForms
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.WordFormsWire
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import kotlin.random.Random

/** Italian's, German's and English's forms (game/OtherForms; the bridge's paradigms.ts): tables, frames, what is asked. */
class OtherFormsTest {
    private var pair: LangPair = L10n.pair

    @Before
    fun before() {
        pair = L10n.pair
        L10n.pair = LangPair(Lang.IT, Lang.DE)
        OtherForms.child = false
    }

    @After
    fun after() {
        L10n.pair = pair
        OtherForms.child = false
    }

    private fun slot(key: String, vararg forms: String, page: String, needs: List<String> = emptyList(), lines: List<FormLine> = emptyList()) =
        FormSlot(key, forms.toList(), page = page, needs = needs, lines = lines)

    private val p6 = listOf("1sg", "2sg", "3sg", "1pl", "2pl", "3pl")

    private val mangiare = LemmaForms(
        "mangiare", "verb", listOf("to eat"), language = "it", aux = listOf("avere"),
        slots = p6.zip(listOf("mangio", "mangi", "mangia", "mangiamo", "mangiate", "mangiano")).map { (p, f) -> slot("pres.$p", f, page = "presente-regolare") } +
            listOf(slot("pp", "mangiato", page = "passato-prossimo")) +
            p6.zip(listOf("mangiavo", "mangiavi", "mangiava", "mangiavamo", "mangiavate", "mangiavano")).map { (p, f) -> slot("impf.$p", f, page = "imperfetto") } +
            listOf(slot("imp.2sg", "mangia", page = "imperativo"), slot("ger", "mangiando", page = "stare-gerundio")),
    )
    private val andare = LemmaForms(
        "andare", "verb", listOf("to go"), language = "it", aux = listOf("essere"),
        slots = listOf(slot("pres.1sg", "vado", page = "verbi-irregolari"), slot("pp", "andato", page = "passato-prossimo")),
    )
    private val haus = LemmaForms(
        "Haus", "noun", listOf("house"), gender = "n", language = "de",
        slots = listOf(
            slot("nom.sg", "Haus", page = "artikel-genus"), slot("gen.sg", "Hauses", page = "genitiv"), slot("dat.sg", "Haus", page = "dativ"),
            slot("acc.sg", "Haus", page = "akkusativ"), slot("nom.pl", "Häuser", page = "plural"), slot("gen.pl", "Häuser", page = "genitiv", needs = listOf("plural")),
            slot("dat.pl", "Häusern", page = "dativ", needs = listOf("plural")), slot("acc.pl", "Häuser", page = "akkusativ", needs = listOf("plural")),
        ),
    )
    private val go = LemmaForms(
        "go", "verb", listOf("to move through space"), language = "en",
        slots = listOf(slot("pres.3sg", "goes", page = "present-simple"), slot("past", "went", page = "past-simple"), slot("pp", "gone", page = "present-perfect"), slot("ing", "going", page = "present-continuous")),
    )
    private val open: (FormSlot) -> List<String> = { emptyList() }

    @Test
    fun `a card's front gives its word and what it is, its article or its to aside`() {
        fun card(front: String) = ReviewCard("c", front, "x")
        assertEquals("casa" to "noun", WordFormsWire.lemmaOf(card("la casa"), "it") to WordFormsWire.posOf(card("la casa"), "it"))
        assertEquals("acqua", WordFormsWire.lemmaOf(card("l'acqua"), "it"))
        assertEquals("Wald" to "noun", WordFormsWire.lemmaOf(card("der Wald"), "de") to WordFormsWire.posOf(card("der Wald"), "de"))
        assertEquals("run" to "verb", WordFormsWire.lemmaOf(card("to run"), "en") to WordFormsWire.posOf(card("to run"), "en"))
        assertEquals("mangiare", WordFormsWire.lemmaOf(card("mangiare"), "it"))
        // Slovene's as ever: "to je" is a phrase, not a verb
        assertNull(WordFormsWire.lemmaOf(card("to je")))
        assertNull(WordFormsWire.posOf(card("hiša")))
    }

    @Test
    fun `an Italian verb's table has its tenses by person and the passato prossimo with its auxiliary`() {
        val t = WordForms.table(mangiare, open)
        assertEquals(listOf("sg", "pl"), t.first().columns)
        assertEquals("io mangio", t.first().rows[0].second[0]!!.text)
        assertEquals("noi mangiamo", t.first().rows[0].second[1]!!.text)
        val perfect = t[1]
        assertEquals("io ho mangiato", perfect.rows[0].second[0]!!.text)
        assertEquals("loro hanno mangiato", perfect.rows[2].second[1]!!.text)
        assertTrue(perfect.rows.flatMap { it.second }.all { it!!.key == "pp" })
        assertTrue(t.any { s -> s.rows.flatMap { it.second }.any { it?.text == "(tu) mangia!" } })
        assertTrue(t.any { s -> s.rows.flatMap { it.second }.any { it?.text == "sto mangiando" } })
        // with essere the participle agrees
        val a = WordForms.table(andare, open)
        assertEquals("io sono andato/a", a[1].rows[0].second[0]!!.text)
        assertEquals("noi siamo andati/e", a[1].rows[0].second[1]!!.text)
    }

    @Test
    fun `a German noun's table has its four cases with the article, in the school's order`() {
        val t = WordForms.table(haus, open).single()
        assertEquals(listOf("1. wer? was?", "2. wessen?", "3. wem?", "4. wen? was?"), t.rows.map { it.first })
        assertEquals(listOf("das Haus", "die Häuser"), t.rows[0].second.map { it!!.text })
        assertEquals(listOf("dem Haus", "den Häusern"), t.rows[2].second.map { it!!.text })
        assertEquals("des Hauses", t.rows[1].second[0]!!.text)
    }

    @Test
    fun `an English verb's table is a form a row, its tense the row's label`() {
        L10n.pair = LangPair(Lang.EN, Lang.SL)
        val t = WordForms.table(go, open).single()
        assertEquals(listOf(WordForms.ONE), t.columns)
        assertEquals(listOf("he, she goes", "went", "have gone", "am going"), t.rows.map { it.second.single()!!.text })
    }

    @Test
    fun `a locked form keeps its page, and the card says what it is`() {
        val locks: (FormSlot) -> List<String> = { s -> if (s.page == "imperfetto") listOf("imperfetto") else emptyList() }
        val locked = WordForms.locked(WordForms.table(mangiare, locks))
        assertEquals(setOf("imperfetto"), locked.keys)
        assertTrue(locked.getValue("imperfetto").single().startsWith("imperfetto"))
    }

    @Test
    fun `a German case the same as its nominative is never asked, nor a plural's the same as its nominative plural, nor Italian's passato remoto`() {
        val asked = haus.slots.filter { WordForms.askable(it, haus) }.map { it.key }
        assertEquals(listOf("gen.sg", "nom.pl", "dat.pl"), asked)
        // English: a form the same as the word is not asked ("cut", cut, cut)
        val cut = LemmaForms("cut", "verb", language = "en", slots = listOf(slot("pres.3sg", "cuts", page = "present-simple"), slot("past", "cut", page = "past-simple")))
        assertEquals(listOf("pres.3sg"), cut.slots.filter { WordForms.askable(it, cut) }.map { it.key })
        // Italian's passato remoto is read in the table, never asked (its page: to recognise)
        val remoto = andare.copy(slots = andare.slots + slot("rem.3sg", "andò", page = "passato-remoto"))
        assertFalse(WordForms.askable(remoto.slot("rem.3sg")!!, remoto))
        assertTrue(WordForms.askable(remoto.slot("pres.1sg")!!, remoto))
    }

    @Test
    fun `a form is asked in a frame of its language, chosen among the same tense's forms`() {
        val q = WordForms.question(mangiare, mangiare.slot("pres.1sg")!!, null, "essen", "de", { true }, Random(1))
        assertNotNull(q)
        assertEquals("Io ", q!!.gap.before)
        assertEquals("mangio", q.answer)
        assertTrue(q.options.containsAll(listOf("mangio")) && q.options.size == 4)
        assertTrue(q.options.filter { it != "mangio" }.all { it in setOf("mangi", "mangia", "mangiamo", "mangiate", "mangiano") })
        assertTrue(q.instruction.contains("presente"))
        val pp = WordForms.question(andare, andare.slot("pp")!!, null, "gehen", "de", { true })!!
        assertEquals("Lui è ", pp.gap.before)
        val dat = WordForms.question(haus, haus.slot("dat.pl")!!, null, "Haus", "it", { true })!!
        assertEquals("mit den ", dat.gap.before)
        assertEquals("Häusern", dat.answer)
        val went = WordForms.question(go, go.slot("past")!!, null, "iti", "sl", { true })!!
        assertEquals("Yesterday I ", went.gap.before)
    }

    @Test
    fun `a line of the content comes first, said in its language, meant in the learner's base`() {
        val line = FormLine(sl = "Jem jabolko.", en = "I eat an apple.", de = "Ich esse einen Apfel.", form = "mangio", text = "Io mangio una mela.")
        assertEquals("Io mangio una mela.", line.said)
        assertEquals("Jem jabolko.", line.meantIn("sl"))
        assertEquals("Ich esse einen Apfel.", line.meantIn("de"))
        val withLine = mangiare.copy(slots = mangiare.slots.map { if (it.key == "pres.1sg") it.copy(lines = listOf(line)) else it })
        val q = WordForms.question(withLine, withLine.slot("pres.1sg")!!, null, "essen", "de", { true })!!
        assertEquals("Io ", q.gap.before)
        assertTrue(q.gap.after.startsWith(" una mela."))
        assertTrue(q.instruction.contains("Ich esse einen Apfel."))
        // an English line is its own translation: none shown
        assertEquals("", FormLine(en = "She went home.", form = "went", text = "She went home.").meantIn("de"))
        // Slovene's lines as ever
        assertEquals("I sit down.", FormLine(sl = "Sedem.", en = "I sit down.", form = "sedem").meantIn("de"))
    }

    @Test
    fun `a child's table says what its terms mean`() {
        OtherForms.child = true
        val title = WordForms.table(mangiare, open).first().title
        assertTrue(title, title.startsWith("presente") && title.endsWith("(jetzt)"))
        OtherForms.child = false
        assertFalse(WordForms.table(mangiare, open).first().title.contains("("))
    }

    @Test
    fun `the phone asks again an answer kept before the other languages' tables, not Slovene's`() {
        assertFalse(CachedForms("casa", 0, emptyList()).current)
        assertTrue(CachedForms("hiša", 0, listOf(LemmaForms("hiša", "noun"))).current)
        assertFalse(CachedForms("casa", 0, listOf(mangiare)).current)
        assertTrue(CachedForms("casa", 0, emptyList(), v = WordFormsWire.VERSION).current)
    }
}
