package si.lanisce.lani.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import si.lanisce.lani.data.Exercise
import si.lanisce.lani.data.FormLine
import si.lanisce.lani.data.FormPartner
import si.lanisce.lani.data.FormSlot
import si.lanisce.lani.data.Grading
import si.lanisce.lani.data.LemmaForms
import si.lanisce.lani.data.ReviewCard
import si.lanisce.lani.data.ReviewPlanner
import si.lanisce.lani.data.ReviewPlanner.Variant
import si.lanisce.lani.data.WordEntry
import si.lanisce.lani.data.WordFormsWire
import si.lanisce.lani.data.Words
import kotlin.random.Random

class WordFormsTest {
    private fun slot(key: String, vararg forms: String, page: String, needs: List<String> = emptyList(), lines: List<FormLine> = emptyList()) =
        FormSlot(key, forms.toList(), page = page, needs = needs, lines = lines)

    /** sesti as GET /forms gives it (Wiktionary's table), without lines. */
    private val sesti = LemmaForms(
        "sesti", "verb", listOf("to sit down"), aspect = "pf", partner = FormPartner("sedeti", "impf", "position", listOf("to sit")),
        slots = listOf(
            slot("pres.1sg", "sedem", page = "glagoli-sedanjik"),
            slot("pres.2sg", "sedeš", page = "glagoli-sedanjik"),
            slot("pres.3sg", "sede", page = "glagoli-sedanjik"),
            slot("pres.1pl", "sedemo", page = "glagoli-sedanjik"),
            slot("pres.2pl", "sedete", page = "glagoli-sedanjik"),
            slot("pres.3pl", "sedejo", page = "glagoli-sedanjik"),
            slot("pres.1du", "sedeva", page = "dvojina", needs = listOf("glagoli-sedanjik")),
            slot("past.m.sg", "sedel", page = "pretekli-cas"),
            slot("past.f.sg", "sedla", page = "pretekli-cas"),
            slot("imp.2sg", "sedi", page = "velelnik"),
            slot("imp.2pl", "sedite", page = "velelnik"),
        ),
    )

    private val hisa = LemmaForms(
        "hiša", "noun", listOf("house"), gender = "f",
        slots = listOf(
            slot("nom.sg", "hiša", page = "imenovalnik"),
            slot("gen.sg", "hiše", page = "rodilnik"),
            slot("dat.sg", "hiši", page = "dajalnik"),
            slot("acc.sg", "hišo", page = "tozilnik"),
            slot("loc.sg", "hiši", page = "mestnik"),
            slot("ins.sg", "hišo", page = "orodnik"),
            slot("nom.pl", "hiše", page = "mnozina"),
            slot("loc.pl", "hišah", page = "mestnik", needs = listOf("mnozina")),
            slot("ins.pl", "hišami", page = "orodnik", needs = listOf("mnozina")),
            slot("gen.du", "hiš", page = "dvojina", needs = listOf("rodilnik")),
        ),
    )

    /** An A1 learner: the A2 pages (rodilnik, dajalnik, orodnik) not introduced, the dual not met. */
    private val a1: (String) -> Mastery? = { id -> if (id in setOf("rodilnik", "dajalnik", "orodnik")) Mastery.NOT_YET else Mastery.NEW }
    private val locksA1: (FormSlot) -> List<String> = { s -> WordForms.locks(s, a1) { false } }
    private val openA1: (FormSlot) -> Boolean = { locksA1(it).isEmpty() }
    private val all: (FormSlot) -> Boolean = { true }

    private val known = ReviewCard("vocab_word_sesti", "sesti", "to sit down", repetitions = 6, lastQuality = 4)

    @Test fun `a form is reached when its pages are introduced, the dual once its page was met`() {
        assertEquals(emptyList<String>(), locksA1(hisa.slot("loc.sg")!!))
        assertEquals(listOf("orodnik"), locksA1(hisa.slot("ins.sg")!!))
        assertEquals(listOf("orodnik"), locksA1(hisa.slot("ins.pl")!!))
        // the dual: its page and the case's, and met
        assertEquals(listOf("rodilnik", "dvojina"), locksA1(hisa.slot("gen.du")!!))
        assertEquals(listOf("dvojina"), WordForms.locks(sesti.slot("pres.1du")!!, a1) { false })
        assertEquals(emptyList<String>(), WordForms.locks(sesti.slot("pres.1du")!!, a1) { it == "dvojina" })
        // a page the book lacks keeps it locked
        assertEquals(listOf("velelnik"), WordForms.locks(sesti.slot("imp.2sg")!!, { id -> if (id == "velelnik") null else Mastery.NEW }) { true })
    }

    @Test fun `a familiar card asks a form at most every third good review`() {
        val fresh = known.copy(repetitions = 2)
        assertFalse(WordForms.due(fresh, null)) // the meaning isn't known yet
        assertFalse(WordForms.due(known.copy(repetitions = 5, lastQuality = 2), null)) // failed last time
        // the first one spread over the cards: one of three repetition counts in a row
        val first = (3..5).filter { WordForms.due(known.copy(repetitions = it), null) }
        assertEquals(1, first.size)
        val r = WordForms.answered(null, "pres.1sg", true, 4, "2026-10-01")
        assertFalse(WordForms.due(known.copy(repetitions = 5), r))
        assertFalse(WordForms.due(known.copy(repetitions = 6), r))
        assertTrue(WordForms.due(known.copy(repetitions = 7), r))
        // a failed review since reset it: when familiar again
        assertTrue(WordForms.due(known.copy(repetitions = 3), WordForms.answered(null, "pres.1sg", true, 9, "2026-10-01")))
    }

    @Test fun `the form asked is one got wrong last, then one not asked yet in order, then the oldest`() {
        assertEquals("pres.1sg", WordForms.pick(sesti, null, all)?.key)
        var r = WordForms.answered(null, "pres.1sg", true, 3, "d")
        assertEquals("pres.2sg", WordForms.pick(sesti, r, all)?.key)
        r = WordForms.answered(r, "pres.2sg", false, 6, "d")
        r = WordForms.answered(r, "pres.3sg", true, 9, "d")
        assertEquals("pres.2sg", WordForms.pick(sesti, r, all)?.key) // wrong last time: again
        // all asked right: the one asked longest ago
        var all2: FormsRecord? = null
        for (s in sesti.slots) all2 = WordForms.answered(all2, s.key, true, 3, "d")
        assertEquals(sesti.slots.first().key, WordForms.pick(sesti, all2, all)?.key)
        // the A1 learner: hiša's genitive and instrumental not yet, nor its nominative (the word itself)
        assertEquals("acc.sg", WordForms.pick(hisa, null, openA1)?.key)
        assertNull(WordForms.pick(hisa.copy(slots = hisa.slots.filter { it.key in setOf("nom.sg", "gen.sg") }), null, openA1))
    }

    @Test fun `a frame by person, chosen among the word's own forms`() {
        val q = WordForms.question(sesti, sesti.slot("pres.1sg")!!, null, "to sit down", "en", all, Random(1))!!
        assertEquals("Jaz ____. (sesti)", q.gap.shown)
        assertEquals("sedem", q.answer)
        assertEquals("Jaz sedem.", q.gap.sentence)
        assertEquals("glagoli-sedanjik", q.page)
        assertEquals(4, q.options.size)
        assertTrue(q.options.toString(), "sedem" in q.options && q.options.all { o -> sesti.slots.any { o in it.forms } })
        assertEquals(q.options.size, q.options.distinct().size)
        assertTrue(q.instruction, "sesti — to sit down" in q.instruction)
        // the past and the imperative
        assertEquals("Jaz (♂) sem ____. (sesti)", WordForms.question(sesti, sesti.slot("past.m.sg")!!, null, "to sit down", "en", all)!!.gap.shown)
        assertEquals("Ona je sedla.", WordForms.question(sesti, sesti.slot("past.f.sg")!!, null, "to sit down", "en", all)!!.gap.sentence)
        assertEquals("(vi) ____! (sesti)", WordForms.question(sesti, sesti.slot("imp.2pl")!!, null, "to sit down", "en", all)!!.gap.shown)
    }

    @Test fun `a noun by the six questions, the preposition following the word`() {
        val loc = WordForms.question(hisa, hisa.slot("loc.sg")!!, null, "house", "en", all)!!
        assertEquals("pri ____ (hiša)", loc.gap.shown)
        assertEquals("pri hiši", loc.gap.sentence)
        assertTrue(loc.instruction, "5. sklon · locative: pri kom? pri čem?" in loc.instruction)
        // hiši is also the dative: never an option beside itself
        assertFalse(loc.options.toString(), loc.options.count { it == "hiši" } > 1)
        assertEquals("s ____ (hiša)", WordForms.question(hisa, hisa.slot("ins.sg")!!, null, "house", "en", all)!!.gap.shown)
        assertEquals("s ____ (hiša, mn.)", WordForms.question(hisa, hisa.slot("ins.pl")!!, null, "house", "en", all)!!.gap.shown)
        assertEquals("s", WordForms.sz("kruhom"))
        assertEquals("z", WordForms.sz("mlekom"))
        assertEquals("z", WordForms.sz("Ano"))
        assertEquals("h", WordForms.kh("kmetu"))
        assertEquals("k", WordForms.kh("hiši"))
        assertEquals("brez dveh ____ (hiša)", WordForms.question(hisa, hisa.slot("gen.du")!!, null, "house", "en", all)!!.gap.shown)
        assertTrue(WordForms.label("loc.pl").endsWith("množina · plural"))
    }

    @Test fun `a real line first, its translation shown, the gap at the start capitalized`() {
        val line = FormLine("Jaz sedem na klop.", "I sit down on the bench.", form = "sedem", from = "scene:park")
        val withLine = sesti.copy(slots = sesti.slots.map { if (it.key == "pres.1sg") it.copy(lines = listOf(line)) else it })
        val q = WordForms.question(withLine, withLine.slot("pres.1sg")!!, null, "to sit down", "en", all)!!
        assertEquals("Jaz ____ na klop. (sesti)", q.gap.shown)
        assertEquals("Jaz sedem na klop.", q.gap.sentence)
        assertTrue(q.instruction, "„I sit down on the bench.“" in q.instruction)
        assertEquals(line, q.line)
        val start = FormLine("Sedite, prosim.", "Sit down, please.", form = "sedite")
        val imp = sesti.copy(slots = sesti.slots.map { if (it.key == "imp.2pl") it.copy(lines = listOf(start)) else it })
        val s = WordForms.question(imp, imp.slot("imp.2pl")!!, null, "to sit down", "en", all)!!
        assertEquals("____, prosim. (sesti)", s.gap.shown)
        assertEquals("Sedite", s.answer)
        assertTrue(s.options.toString(), s.options.all { it.first().isUpperCase() } && "Sedite" in s.options)
        // a line whose form isn't there as a word of its own is no line
        assertNull(WordForms.gapIn("Sedemnajst let.", "sedem"))
    }

    @Test fun `typed once it was right, chosen again after a slip, the answer as a whole sentence`() {
        val s = sesti.slot("pres.2sg")!!
        val right = WordForms.answered(null, "pres.2sg", true, 3, "d")
        assertTrue(WordForms.question(sesti, s, right, "to sit down", "en", all)!!.typed)
        assertFalse(WordForms.question(sesti, s, WordForms.answered(right, "pres.2sg", false, 6, "d"), "to sit down", "en", all)!!.typed)
        val q = WordForms.question(sesti, s, null, "to sit down", "en", all)!!
        assertEquals("Ti sedi.", q.said("sedi"))
        assertEquals("Ti sedeš.", q.said("Ti sedeš."))
        // a slot of two forms from Wiktionary has no frame: unsure which is the standard one
        val two = sesti.copy(slots = listOf(slot("pres.3pl", "sedejo", "sedo", page = "glagoli-sedanjik")))
        assertFalse(WordForms.askable(two.slots.single(), two))
        assertTrue(WordForms.askable(two.slots.single(), two.copy(table = "checked")))
    }

    @Test fun `a review asks forms of about one card in three, a wrong form keeps the word`() {
        val cards = (1..6).map { known.copy(id = "c$it") }
        val q = WordForms.question(sesti, sesti.slot("pres.1sg")!!, null, "to sit down", "en", all)!!
        val tasks = ReviewPlanner.plan(cards, cards, canSpeak = false, forms = { q })
        assertEquals(2, tasks.count { it.variant.form })
        val t = tasks.first { it.variant == Variant.FORM }
        val ex = t.exercise as Exercise.Choice
        assertEquals("glagoli-sedanjik", ex.grammar)
        assertEquals("sedem", ex.options[ex.answer])
        assertEquals(3, ReviewPlanner.quality(Variant.FORM, Grading.Verdict.WRONG))
        assertEquals(4, ReviewPlanner.quality(Variant.FORM, Grading.Verdict.CORRECT))
        assertEquals(5, ReviewPlanner.quality(Variant.FORM_TYPE, Grading.Verdict.CORRECT))
        assertEquals(1, ReviewPlanner.quality(Variant.TYPE, Grading.Verdict.WRONG))
        val typed = ReviewPlanner.formTask(known, q.copy(options = emptyList()))
        val cloze = typed.exercise as Exercise.Cloze
        assertEquals(Variant.FORM_TYPE, typed.variant)
        assertEquals("Jaz ___. (sesti)", cloze.text)
        assertTrue(cloze.accept.toString(), "sedem" in cloze.accept && "Jaz sedem." in cloze.accept)
        assertEquals(Grading.Verdict.CORRECT, Grading.check("Jaz sedem.", cloze.accept).verdict)
        assertEquals(Grading.Verdict.WRONG, Grading.check("sedeš", cloze.accept).verdict)
        // without forms (packs, a building's words, the road) nothing changes
        assertTrue(ReviewPlanner.plan(cards, cards, canSpeak = false).none { it.variant.form })
    }

    @Test fun `a card's form question comes only for a due familiar card of a known lemma`() {
        val entries = listOf(sesti)
        val due = (3..5).map { known.copy(repetitions = it) }.first { WordForms.due(it, null) }
        assertNotNull(WordForms.forCard(due, entries, null, locksA1, "en"))
        assertNull(WordForms.forCard(due, null, null, locksA1, "en")) // an older bridge: no forms
        assertNull(WordForms.forCard(due.copy(front = "sedem dni"), entries, null, locksA1, "en")) // not one word
        assertNull(WordForms.forCard(known.copy(repetitions = 1), entries, null, locksA1, "en"))
        assertNotNull(WordForms.forCard(known.copy(repetitions = 1), entries, null, locksA1, "en", force = true)) // QA
    }

    @Test fun `the table shows the forms reached and what opens the rest`() {
        val sections = WordForms.table(hisa, locksA1)
        val cases = sections.single()
        assertEquals(6, cases.rows.size)
        val loc = cases.rows[4]
        assertEquals("5. pri kom? pri čem?", loc.first)
        assertEquals("hiši", loc.second[0]?.text)
        assertTrue(loc.second[0]!!.open)
        assertFalse(cases.rows[5].second[0]!!.open) // the instrumental: A2
        val locked = WordForms.locked(sections)
        assertEquals(listOf("6. sklon · instrumental"), locked["orodnik"])
        assertEquals(listOf("2. sklon · genitive", "dvojina · dual"), locked["rodilnik"])
        val verb = WordForms.table(sesti) { emptyList() }
        assertEquals("jaz sedem", verb.first().rows[0].second[0]?.text)
        assertEquals("sedite!", verb.first { it.columns.isEmpty() }.rows.single().second[1]?.text)
    }

    @Test fun `GET forms as the bridge sends it`() {
        val raw = """{"language":"sl","words":[{"word":"sesti","entries":[{"lemma":"sesti","pos":"verb","gloss":["to sit down"],"gender":null,
            "aspect":"pf","partner":{"lemma":"sedeti","aspect":"impf","kind":"position","gloss":["to sit"]},"table":"wiktionary",
            "slots":[{"key":"pres.1sg","forms":["sedem"],"grammar":"first-person singular present","page":"glagoli-sedanjik",
            "lines":[{"sl":"Jaz sedem.","en":"I sit down.","form":"sedem","from":"scene:x","future":1}]}]}]},{"word":"xyz","entries":[]}],
            "attribution":"Wiktionary (CC BY-SA 4.0) via kaikki.org"}"""
        val a = WordFormsWire.parse(raw)
        val e = a.words.first().entries.single()
        assertEquals("sedeti", e.partner?.lemma)
        assertEquals("sedem", e.slot("pres.1sg")?.lines?.single()?.form)
        assertTrue(a.words[1].entries.isEmpty())
        assertEquals("/forms?w=sesti&w=hi%C5%A1a", WordFormsWire.url("http://n", listOf("sesti", "hiša")).let { it.encodedPath + "?" + it.encodedQuery })
        assertEquals("sesti", WordFormsWire.lemmaOf(known))
        assertNull(WordFormsWire.lemmaOf(known.copy(front = "dober/dobra/dobro (dan ♂ / kava ♀)")))
        assertNull(WordFormsWire.lemmaOf(known.copy(kind = "grammar_rule")))
        val stati = listOf(LemmaForms("stati", "verb", listOf("to cost")), LemmaForms("stati", "verb", listOf("to stand")))
        assertEquals("to stand", WordFormsWire.entryFor(stati, "to stand (up)")?.gloss?.single())
    }

    @Test fun `a word card shows an aspect partner under the entry the line means`() {
        val sesti = WordEntry("sesti", "verb", listOf("to sit down"), aspect = "pf", partner = FormPartner("sedeti", "impf", "position"), here = true)
        val sedeti = WordEntry("sedeti", "verb", listOf("to sit"), aspect = "impf", partner = FormPartner("sesti", "pf", "position"))
        val other = WordEntry("sedem", "num", listOf("seven"))
        assertEquals(listOf(sesti to sedeti, other to null), Words.withPartners(listOf(sesti, other, sedeti)))
        assertEquals(listOf(sedeti to sesti), Words.withPartners(listOf(sedeti, sesti)))
        // a partner the lookup doesn't have: the entry alone (its partner line names it)
        assertEquals(listOf(sesti to null), Words.withPartners(listOf(sesti)))
    }
}
