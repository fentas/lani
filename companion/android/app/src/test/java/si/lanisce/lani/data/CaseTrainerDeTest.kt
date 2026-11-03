package si.lanisce.lani.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.Case
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.cases.ck
import si.lanisce.lani.ui.cases.label
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/**
 * German's case trainer and lens in the app (docs/plans/05-built/case-de.md): its case set (four, the course's order, its
 * questions, Slovene's colours, unnumbered), its bundled bank read (the sentence under de, the meaning in the base), its
 * forms the same as the bridge's (bridge/test/fixtures/german-forms.json), "Dekliniere!" filled from them (the article,
 * the adjective, the noun: dem Kollegen, des Hundes or des Hunds, den Kindern; dem Bub said and right), its grading in
 * German, a session planned with the right kinds; and Slovene's set as it was.
 */
class CaseTrainerDeTest {
    private val pair = L10n.pair
    private val today = LocalDate.of(2026, 11, 3)

    @Before fun pair() {
        L10n.pair = LangPair(Lang.DE, Lang.SL)
    }

    @After fun back() {
        L10n.pair = pair
    }

    private val companion = listOf(File("../.."), File(".."), File("companion")).first { File(it, "bridge/test/fixtures").isDirectory }
    private val forms: GermanForms = GermanForms.parse(File(companion, "grammar/de/data/case-triggers.json").readText())!!
    private val bank: CaseBank by lazy { CaseBanks.parse(File(companion, "cases/de/bank.json").readText())!! }

    @Test
    fun `German's set is four cases in the course's order, with its questions, unnumbered, Slovene's colours`() {
        val set = CaseSets.current
        assertEquals("de", set.language)
        assertEquals(listOf(Case.NOM, Case.ACC, Case.DAT, Case.GEN), set.cases)
        assertEquals(listOf("nominativ", "akkusativ", "dativ", "genitiv"), set.cases.map { it.id })
        assertEquals(listOf("wer? was?", "wen? was?", "wem?", "wessen?"), set.cases.map { it.ask })
        assertFalse(set.numbered)
        assertEquals("Dativ", Case.DAT.named)
        assertEquals(Case.DAT, Case.ofId("dativ"))
        // Slovene's ids still read (a bank of another language's never mixes, but the lookup is any language's)
        assertEquals(Case.GEN, Case.ofId("rodilnik"))
        assertEquals(0xFFD69A2D, Case.DAT.lightArgb)
        assertFalse(Case.DAT.label().startsWith("3."))
        assertEquals("cases.de.tile", ck("cases.tile"))
    }

    @Test
    fun `Slovene's set is as it was`() {
        L10n.pair = LangPair(Lang.SL, Lang.EN)
        val set = CaseSets.current
        assertEquals(Case.entries, set.cases)
        assertEquals(listOf("imenovalnik", "rodilnik", "dajalnik", "tozilnik", "mestnik", "orodnik"), Case.entries.map { it.id })
        assertEquals("koga? česa?", Case.GEN.ask)
        assertEquals("pri kom? pri čem? (o kom? o čem?)", Case.LOC.asks)
        assertEquals("4. tožilnik", Case.ACC.named)
        assertEquals("cases.tile", ck("cases.tile"))
    }

    @Test
    fun `the bundled German bank reads: its sentences under de, meanings in the base, every item's case German's`() {
        assertEquals("de", bank.language)
        assertTrue(bank.items.size >= 250)
        val it0 = bank.items.first { it.id == "mit-dem-traktor" }
        assertEquals("Franz fährt mit dem Traktor aufs Feld.", it0.text)
        assertEquals("Traktor", CaseBanks.bare(it0.tokens[it0.word]))
        assertEquals(listOf(3, 4), it0.phrase)
        assertEquals("Franz se pelje s traktorjem na njivo.", it0.meaning("sl"))
        assertTrue(bank.items.all { Case.ofId(it.case) in CaseSets.DE.cases })
        assertTrue(bank.frames.all { it.text.contains("{x}") && it.det != null })
    }

    @Test
    fun `German's forms are the bridge's (its parity fixture)`() {
        val fx = Json.parseToJsonElement(File(companion, "bridge/test/fixtures/german-forms.json").readText()).jsonObject
        for (n in fx["nouns"]!!.jsonArray.map { it.jsonObject }) {
            val lemma = n["lemma"]!!.jsonPrimitive.content
            val gender = n["gender"]!!.jsonPrimitive.content
            val plural = n["plural"]?.jsonPrimitive?.content
            for ((slot, want) in n["slots"]!!.jsonObject) {
                val (c, num) = slot.split('.')
                val got = forms.noun(lemma, gender, plural, c, num)
                val w = want.jsonObject
                assertEquals("$lemma $slot", w["forms"]!!.jsonArray.map { it.jsonPrimitive.content }, got.forms)
                assertEquals("$lemma $slot also", w["also"]!!.jsonArray.map { it.jsonPrimitive.content }, got.also)
                assertEquals("$lemma $slot sure", w["sure"]!!.jsonPrimitive.content.toBoolean(), got.sure)
            }
        }
        for (a in fx["adjectives"]!!.jsonArray.map { it.jsonObject }) {
            val (c, g, num) = a["slot"]!!.jsonPrimitive.content.split('.')
            val det = a["det"]?.takeIf { it != JsonNull }?.jsonPrimitive?.content
            assertEquals(a.toString(), a["form"]!!.jsonPrimitive.content, forms.adjectiveForm(a["lemma"]!!.jsonPrimitive.content, det, c, g, num))
        }
        for (d in fx["determiners"]!!.jsonArray.map { it.jsonObject }) {
            val (c, g, num) = d["slot"]!!.jsonPrimitive.content.split('.')
            val want = d["form"]?.takeIf { it != JsonNull }?.jsonPrimitive?.content
            assertEquals(d.toString(), want, forms.determinerForm(d["lemma"]!!.jsonPrimitive.content, c, g, num))
        }
    }

    private fun pool(nouns: List<CaseNoun> = emptyList(), level: String = "B2", child: Boolean = false) =
        CasePool(bank, nouns, setOf("faelle", "akkusativ", "dativ", "genitiv", "plural", "adjektiv-der", "wechselpraepositionen", "praepositionen-dativ", "praepositionen-akkusativ", "praepositionen-genitiv"), level, child, "sl", { null })

    private fun frame(id: String) = bank.frames.first { it.id == id }

    @Test
    fun `Dekliniere fills a frame with the article, the adjective and the noun in its case`() {
        val opa = CaseTrainer.nounDe("Opa", "m", "Opas", "dedek", forms = forms)!!
        val ex = CaseTrainer.declineDe(pool(listOf(opa)), frame("helfe-dat"), opa, null, forms)!!
        assertEquals("meinem Opa", ex.shown)
        assertTrue(ex.prompt, ex.prompt.startsWith("Heute helfe ich ___.") && ex.prompt.endsWith("(mein Opa)"))
        assertEquals("de", ex.lang)
        val hund = CaseTrainer.nounDe("Hund", "m", "Hunde", "pes", forms = forms)!!
        val gross = bank.adjectives.first { it.lemma == "groß" }
        assertEquals("den großen Hund", CaseTrainer.declineDe(pool(listOf(hund)), frame("sehe-akk"), hund, gross, forms)!!.shown)
        // the genitive's two endings both right, the -es shown
        val gen = CaseTrainer.declineDe(pool(listOf(hund)), frame("wegen-gen"), hund, null, forms)!!
        assertEquals("des Hundes", gen.shown)
        assertEquals(listOf("des Hundes", "des Hunds"), gen.accept)
        // the plural's dative -n; a frame at the sentence's start capitalized
        val kind = CaseTrainer.nounDe("Kind", "n", "Kinder", "otrok", forms = forms)!!
        assertEquals("den Kindern", CaseTrainer.declineDe(pool(listOf(kind)), frame("mit-pl"), kind, null, forms)!!.shown)
        assertEquals("Das schöne Kind", CaseTrainer.declineDe(pool(listOf(kind)), frame("ist-schoen"), kind, bank.adjectives.first { it.lemma == "schön" }, forms)!!.shown)
        // a strong adjective with no article: mit frischem Brot
        val brot = CaseTrainer.nounDe("Brot", "n", "Brote", "kruh", forms = forms)!!
        assertEquals("frischem Brot", CaseTrainer.declineDe(pool(listOf(brot)), frame("mit-stark"), brot, bank.adjectives.first { it.lemma == "frisch" }, forms)!!.shown)
    }

    @Test
    fun `the n-Deklination's -n is the answer, the bare form said and right, labelled`() {
        val bub = CaseTrainer.nounDe("Bub", "m", null, "fant", forms = forms)!!
        val ex = CaseTrainer.declineDe(pool(listOf(bub)), frame("helfe-dat"), bub, null, forms)!!
        assertEquals("meinem Buben", ex.shown)
        assertEquals(listOf("meinem Bub"), ex.spoken)
        val said = CaseTrainer.gradeDecline(ex, "meinem Bub")
        assertEquals(Verdict.CORRECT, said.verdict)
        assertTrue(said.spoken)
        assertEquals(Verdict.CORRECT, CaseTrainer.gradeDecline(ex, "Heute helfe ich meinem Buben.").verdict)
        assertEquals(Verdict.WRONG, CaseTrainer.gradeDecline(ex, "der Bub").verdict)
    }

    @Test
    fun `a noun whose plural is unknown is kept to the singular, one whose genitive isn't sure never asked there`() {
        val opa = CaseTrainer.nounDe("Onkel", "m", null, "stric", forms = forms)!!
        assertFalse("dat.pl" in opa.checked)
        val bus = CaseTrainer.nounDe("Bus", "m", "Busse", "avtobus", forms = forms)!!
        assertFalse("gen.sg" in bus.checked)
        assertFalse(CaseTrainer.declinable(pool(listOf(bus)), frame("wegen-gen"), bus))
        assertTrue(CaseTrainer.declinable(pool(listOf(bus)), frame("helfe-dat"), bus))
    }

    @Test
    fun `a which item frames its noun phrase, its why the trigger, graded by the German case`() {
        val it0 = bank.items.first { it.id == "mit-dem-traktor" }
        val ex = CaseTrainer.item(pool(), it0)!!
        assertEquals(listOf(3, 4), ex.phrase)
        assertTrue(CaseTrainer.pickRight(ex, Case.DAT))
        assertFalse(CaseTrainer.pickRight(ex, Case.ACC))
        assertTrue(CaseTrainer.whyRight(ex, 2, null))
        assertTrue(ex.ruleSl.contains("Dativ"))
        assertEquals("Dativ · mit", CaseTrainer.expectedLine(ex))
    }

    @Test
    fun `a session plans German's kinds, a child's without B1 and short, the focus on wo or wohin its own`() {
        val nouns = listOf("Hund" to "Hunde", "Tisch" to "Tische", "Baum" to "Bäume", "Apfel" to "Äpfel", "Ball" to "Bälle", "Garten" to "Gärten").map { (l, p) -> CaseTrainer.nounDe(l, "m", p, l, forms = forms)!! }
        val p = pool(nouns)
        assertTrue(CaseTrainer.isOpen(p))
        val run = CaseTrainer.plan(p, null, emptyList(), { 0.0 }, today, Random(7))
        assertEquals(CaseTrainer.RUN, run.size)
        assertTrue(run.map { it.task.kind }.toSet().size >= 2)
        assertTrue(run.all { Case.ofId(it.exercise.case) in CaseSets.DE.cases })
        val child = CaseTrainer.plan(pool(nouns, level = "A2", child = true), null, emptyList(), { 0.0 }, today, Random(3))
        assertTrue(child.isNotEmpty() && child.all { it.task.level in setOf("A1", "A2") })
        val wo = CaseTrainer.plan(p, null, emptyList(), { 0.0 }, today, Random(5), focus = CaseTrainer.WO_WOHIN)
        assertTrue(wo.all { it.task.kind == CaseKind.PLACE || it.task.rule == CaseTrainer.WO_WOHIN })
        assertEquals(CaseTrainer.WO_WOHIN, CaseTrainer.focusOfUnit("de", "wo-wohin"))
        assertEquals("dativ", CaseTrainer.focusOfUnit("de", "wem-dativ"))
        assertNull(CaseTrainer.focusOfUnit("de", "hallo"))
        assertEquals(CaseTrainer.AGREEMENT_DE, CaseTrainer.focusOfPage("adjektiv-tabelle", emptyList()))
    }

    @Test
    fun `the storm sorts German phrases into the four cases`() {
        val storm = CaseTrainer.storm(pool(), null, emptyList(), { 0.0 }, today, Random(2))
        assertTrue(storm.size >= 20)
        assertTrue(storm.all { Case.ofId(it.exercise.case) in CaseSets.DE.cases && it.exercise.phrase.isEmpty() })
    }

    @Test
    fun `the lens reads German, and says its own lines`() {
        assertTrue("de" in CaseLens.LANGUAGES)
        assertNotNull(Words.url("http://n/", "Traktor", "Franz fährt mit dem Traktor.", null).queryParameter("lens"))
        val read = CaseRead(i = 3, word = "im", kind = "fused", sure = "decided", rule = "wechselpraepositionen", pick = CaseOptionWire("dat", "sg", "m"), options = listOf(CaseOptionWire("dat", "sg", "m")), trigger = CaseRef(3, "im", 0, 2))
        val row = CaseLens.row(read)!!
        val (target, _) = CaseLens.why(row, read)!!
        assertTrue(target, target.contains("Präposition und Artikel"))
    }
}
