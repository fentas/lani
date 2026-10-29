package si.lanisce.lani.data

import kotlinx.serialization.json.JsonObject
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
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import si.lanisce.lani.ui.home.HomeLogic
import java.time.LocalDate
import kotlin.random.Random

/**
 * The case trainer (data/CaseTrainer.kt, data/CaseLook.kt, data/CaseBank.kt): the contract with the case lens (ids, order,
 * questions, colours); the bundled bank read and consistent; the grading of a case picked, of the word that decides it
 * and of a form typed; the frames filled from the forms book's forms (k/h, s/z, dva/dve, an adjective's agreement and a
 * person's accusative); the weighting towards the weak cases and the learner's mistakes, the focus, the kinds' shares and
 * order, a child's sessions, the items missed coming back; the mastery kept; the mistakes' pattern ids; what the node is
 * sent; Home's idea.
 */
class CaseTrainerTest {
    private val pair = L10n.pair
    private val today = LocalDate.of(2026, 10, 29)

    @Before fun pair() {
        L10n.pair = LangPair(Lang.SL, Lang.EN)
    }

    @After fun back() {
        L10n.pair = pair
    }

    // --- a bank of our own: every kind, a few rules ---------------------------------------------------------------------

    private fun rule(case: String, page: String, vararg words: String) = CaseRuleLine(case, page, words.toList(), "$page short", "$page in English", "$page auf Deutsch", "$page in italiano")

    private val rules = mapOf(
        "subject" to rule("imenovalnik", "skloni"),
        "object" to rule("tozilnik", "tozilnik"),
        "nikalnica" to rule("rodilnik", "rodilnik-nikalnica", "ni", "nimam", "ne"),
        "brez" to rule("rodilnik", "rodilnik-predlogi", "brez"),
        "iz" to rule("rodilnik", "rodilnik-predlogi", "iz"),
        "k" to rule("dajalnik", "dajalnik", "k", "h"),
        "v-na-kam" to rule("tozilnik", "kam-tozilnik", "v", "na"),
        "v-na-kje" to rule("mestnik", "kje-mestnik-orodnik", "v", "na"),
        "pri" to rule("mestnik", "kje-mestnik-orodnik", "pri"),
        "o" to rule("mestnik", "mestnik", "o"),
        "z-s" to rule("orodnik", "orodnik", "z", "s"),
        "stevilo" to rule("rodilnik", "stevila-samostalniki", "pet"),
    )

    private fun which(id: String, sl: String, word: Int, lemma: String, case: String, why: String, trigger: Int?, number: String = "sg", level: String = "A1", role: String? = null, also: List<Int> = emptyList(), gender: String? = null, pos: String = "noun") =
        CaseItem(id, "which", level, sl, "en", "de", "it", word, lemma, pos, gender, case, number, why, trigger, also, role)

    private val items = listOf(
        which("nimam-casa", "Danes nimam časa.", 2, "čas", "rodilnik", "nikalnica", 1),
        which("brez-mleka", "Kavo pijem brez mleka.", 3, "mleko", "rodilnik", "brez", 2, level = "A2"),
        which("iz-gorice", "Sem iz Gorice.", 2, "Gorica", "rodilnik", "iz", 1, level = "A2", pos = "name"),
        which("luka-pase", "Luka pase ovce.", 0, "Luka", "imenovalnik", "subject", null, role = "subject", pos = "name"),
        which("pase-ovce", "Luka pase ovce.", 2, "ovca", "tozilnik", "object", null, number = "pl", role = "object", also = listOf(1)),
        which("k-babici", "Grem k babici.", 2, "babica", "dajalnik", "k", 1, level = "A2"),
        which("v-kuhinji", "Babica je v kuhinji.", 3, "kuhinja", "mestnik", "v-na-kje", 2, also = listOf(1)),
        which("na-posto", "Grem na pošto.", 2, "pošta", "tozilnik", "v-na-kam", 1, also = listOf(0)),
        which("o-medvedu", "Govorimo o medvedu.", 2, "medved", "mestnik", "o", 1),
        which("s-kladivom", "Tone kuje s kladivom.", 3, "kladivo", "orodnik", "z-s", 2, level = "A2"),
        which("pet-ovc", "V hlevu je pet ovc.", 4, "ovca", "rodilnik", "stevilo", 3, number = "pl"),
        which("v-veliki-hisi", "Babica živi v veliki hiši.", 3, "velik", "mestnik", "v-na-kje", 2, also = listOf(1), gender = "f", pos = "adj"),
        which("b1-dolg", "Ko je stari Janez prišel iz gozda, je bil zelo utrujen.", 6, "gozd", "rodilnik", "iz", 5, level = "B1"),
        CaseItem("gre-na-trznico", "place", "A1", "Micka gre na tržnico.", "Micka is going to the market.", "de", "it", 3, "tržnica", "noun", null, "tozilnik", "sg", "v-na-kam", 1,
            question = "kje-kam", gap = listOf(2, 3), options = listOf("na tržnico", "na tržnici"), answer = 0, cue = "tržnica", scene = "na-trznici"),
        CaseItem("je-na-trznici", "place", "A1", "Micka je na tržnici.", "Micka is at the market.", "de", "it", 3, "tržnica", "noun", null, "mestnik", "sg", "v-na-kje", 1,
            question = "kje-kam", gap = listOf(2, 3), options = listOf("na tržnico", "na tržnici"), answer = 1, cue = "tržnica", scene = "na-trznici"),
        CaseItem("v-ali-na", "place", "A1", "Grem na pošto.", "en", "de", "it", 2, "pošta", "noun", null, "tozilnik", "sg", "v-na-kam", null,
            question = "v-na", gap = listOf(1, 1), options = listOf("v", "na"), answer = 1),
    )

    private val frames = listOf(
        CaseFrame("danes-ni", "rodilnik", "sg", "nikalnica", "A1", "Danes ni {x}.", "There's no … today.", "de …", "it …", adj = true),
        CaseFrame("grem-k", "dajalnik", "sg", "k", "A2", "Grem {k} {x}.", "I'm going to …", "de …", "it …", adj = true),
        CaseFrame("pridem-z", "orodnik", "sg", "z-s", "A2", "Pridem {s} {x}.", "I'll come with …", "de …", "it …", adj = true),
        CaseFrame("vidim", "tozilnik", "sg", "object", "A1", "Vidim {x}.", "I can see …", "de …", "it …", adj = true),
        CaseFrame("vidim-dva", "tozilnik", "du", "object", "A1", "Vidim {two} {x}.", "I can see two …", "de …", "it …", needs = listOf("dvojina")),
        CaseFrame("vidim-pl", "tozilnik", "pl", "object", "A1", "Vidim {x}.", "I can see … (several)", "de …", "it …", adj = true, needs = listOf("mnozina")),
        CaseFrame("to-je", "imenovalnik", "sg", "subject", "A1", "To je {x}.", "This is …", "de …", "it …", adj = true),
    )

    private fun adjective(lemma: String, stem: String, soft: Boolean, possessive: Boolean = false): CaseAdjective {
        val ends = mapOf(
            "m.sg" to listOf("", "ega", "emu", "", "em", "im"), "f.sg" to listOf("a", "e", "i", "o", "i", "o"),
            "n.sg" to listOf(if (soft) "e" else "o", "ega", "emu", if (soft) "e" else "o", "em", "im"),
            "m.pl" to listOf("i", "ih", "im", "e", "ih", "imi"), "f.pl" to listOf("e", "ih", "im", "e", "ih", "imi"), "n.pl" to listOf("a", "ih", "im", "a", "ih", "imi"),
        )
        val cases = listOf("nom", "gen", "dat", "acc", "loc", "ins")
        val forms = buildMap {
            for ((gn, e) in ends) for ((i, c) in cases.withIndex()) {
                val (g, n) = gn.split('.')
                put("$c.$g.$n", if (gn == "m.sg" && (c == "nom" || c == "acc")) lemma else stem + e[i])
            }
            put("acc.m.sg.anim", stem + "ega")
        }
        return CaseAdjective(lemma, if (possessive) "possessive" else "adj", "A1", "meaning of $lemma", "de", "it", forms)
    }

    private val adjectives = listOf(adjective("moj", "moj", soft = true, possessive = true), adjective("lep", "lep", soft = false))

    private val bank = CaseBank(rules = rules, items = items, frames = frames, adjectives = adjectives)

    private fun noun(lemma: String, gender: String, vararg forms: Pair<String, String>, spoken: Map<String, List<String>> = emptyMap()) =
        CaseNoun(lemma, gender, "meaning of $lemma", forms.groupBy({ it.first }, { it.second }), spoken)

    private val miza = noun("miza", "f", "nom.sg" to "miza", "gen.sg" to "mize", "dat.sg" to "mizi", "acc.sg" to "mizo", "loc.sg" to "mizi", "ins.sg" to "mizo", "acc.du" to "mizi", "acc.pl" to "mize")
    private val brat = noun("brat", "m", "nom.sg" to "brat", "gen.sg" to "brata", "dat.sg" to "brati", "dat.sg" to "bratu", "acc.sg" to "brata", "loc.sg" to "bratu", "ins.sg" to "bratom", "acc.du" to "brata", "acc.pl" to "brate")
    private val kmet = noun("kmet", "m", "nom.sg" to "kmet", "gen.sg" to "kmeta", "dat.sg" to "kmetu", "acc.sg" to "kmeta", "loc.sg" to "kmetu", "ins.sg" to "kmetom")
    private val stol = noun("stol", "m", "nom.sg" to "stol", "gen.sg" to "stola", "dat.sg" to "stolu", "acc.sg" to "stol", "loc.sg" to "stolu", "ins.sg" to "stolom", "acc.pl" to "stole")
    private val okno = noun("okno", "n", "nom.sg" to "okno", "gen.sg" to "okna", "dat.sg" to "oknu", "acc.sg" to "okno", "loc.sg" to "oknu", "ins.sg" to "oknom")
    private val sestra = noun("sestra", "f", "nom.sg" to "sestra", "gen.sg" to "sestre", "dat.sg" to "sestri", "acc.sg" to "sestro", "loc.sg" to "sestri", "ins.sg" to "sestro", "gen.pl" to "sestra", "gen.pl" to "sester")
    private val nouns = listOf(miza, brat, kmet, stol, okno, sestra)

    private val levels = mapOf(
        "skloni" to "A1", "imenovalnik" to "A1", "tozilnik" to "A1", "mestnik" to "A1", "kam-tozilnik" to "A1", "kje-mestnik-orodnik" to "A1",
        "rodilnik-nikalnica" to "A1", "stevila-samostalniki" to "A1", "mnozina" to "A1", "dvojina" to "A1", "pridevniki-ujemanje" to "A1",
        "rodilnik" to "A2", "rodilnik-predlogi" to "A2", "dajalnik" to "A2", "orodnik" to "A2", "dvojina-skloni" to "B1",
    )

    private fun pool(level: String = "A2", child: Boolean = false, introduced: Set<String> = emptySet(), nouns: List<CaseNoun> = this.nouns, scenes: Set<String> = setOf("na-trznici")) =
        CasePool(bank, nouns, introduced, level, child, "en", { levels[it] }, scenes)

    private fun exerciseOf(id: String, p: CasePool = pool()) = CaseTrainer.item(p, items.first { it.id == id })!!

    // --- the contract -------------------------------------------------------------------------------------------------

    @Test fun `the six cases keep the contract's ids order questions and colours`() {
        assertEquals(listOf("imenovalnik", "rodilnik", "dajalnik", "tozilnik", "mestnik", "orodnik"), Kase.entries.map { it.id })
        assertEquals(listOf(1, 2, 3, 4, 5, 6), Kase.entries.map { it.n })
        assertEquals(listOf("kdo? kaj?", "koga? česa?", "komu? čemu?", "koga? kaj?", "pri kom? pri čem?", "s kom? s čim?"), Kase.entries.map { it.question })
        assertEquals("pri kom? pri čem? (o kom? o čem?)", Kase.MESTNIK.questions)
        assertEquals(listOf(0xFF4A7FC1, 0xFFC2503C, 0xFFD69A2D, 0xFF3E9B5F, 0xFF8A5CB8, 0xFF2C9AA0), Kase.entries.map { it.lightArgb })
        assertEquals("tožilnik", Kase.TOZILNIK.sl)
        assertEquals(Kase.RODILNIK, Kase.ofSlot("gen.pl"))
        assertEquals(listOf("rodilnik-predlogi", "rodilnik-nikalnica", "rodilnik-kolicina", "kam-tozilnik", "kje-mestnik-orodnik", "dajalnik", "orodnik", "tozilnik", "mestnik", "skloni"), CaseLook.RULES)
    }

    @Test fun `a case's dark colour is lighter with the same hue`() {
        fun hsl(argb: Long): Pair<Double, Double> {
            val r = ((argb shr 16) and 0xFF) / 255.0
            val g = ((argb shr 8) and 0xFF) / 255.0
            val b = (argb and 0xFF) / 255.0
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val d = max - min
            val h = when (max) { r -> 60 * (((g - b) / d).mod(6.0)); g -> 60 * ((b - r) / d + 2); else -> 60 * ((r - g) / d + 4) }
            return h to (max + min) / 2
        }
        for (k in Kase.entries) {
            val (h1, l1) = hsl(k.lightArgb)
            val (h2, l2) = hsl(k.darkArgb)
            assertTrue("${k.id}: ${k.darkArgb.toString(16)} lighter", l2 > l1 + 0.1)
            assertTrue("${k.id}: hue $h1 → $h2", kotlin.math.abs(h1 - h2) < 4.0)
        }
    }

    // --- the bundled bank ----------------------------------------------------------------------------------------------

    @Test fun `the bundled bank is read and every item is consistent`() {
        val b = CaseBanks.bundled("sl")
        assertNotNull(b)
        b!!
        assertTrue("machine-written, flagged", b.machineWritten && !b.reviewed && b.review.isNotBlank())
        assertTrue("about 300 items: ${b.items.size}", b.items.size >= 300)
        for (it in b.items) {
            val toks = it.tokens
            assertTrue("${it.id}: word in the sentence", it.word in toks.indices)
            val r = b.rule(it.why)
            assertNotNull("${it.id}: rule ${it.why}", r)
            assertEquals("${it.id}: the rule's case", r!!.case, it.case)
            assertNotNull("${it.id}: case", Kase.of(it.case))
            it.trigger?.let { t -> assertTrue("${it.id}: trigger in the sentence", t in toks.indices && t != it.word) }
            if (it.kind == "which" && it.trigger == null) assertTrue("${it.id}: a role", it.role in setOf("subject", "object"))
            if (it.kind == "place") {
                assertEquals("${it.id}: gap", 2, it.gap.size)
                val gapped = toks.subList(it.gap[0], it.gap[1] + 1).joinToString(" ") { t -> CaseBanks.bare(t) }
                assertEquals("${it.id}: the answer is the gap", gapped.lowercase(), it.options[it.answer].lowercase())
            }
            assertTrue("${it.id}: translations", it.en.isNotBlank() && it.de.isNotBlank() && it.it.isNotBlank())
        }
        // every case and number, every level
        for (k in Kase.entries) assertTrue("${k.id} asked", b.items.count { it.case == k.id } >= 20)
        for (n in listOf("sg", "pl", "du")) assertTrue("number $n", b.items.any { it.number == n })
        for (l in listOf("A1", "A2", "B1")) assertTrue("level $l", b.items.any { it.level == l })
        assertTrue("frames for every case", Kase.entries.all { k -> b.frames.any { it.case == k.id } })
        assertTrue("adjectives and possessives", b.adjectives.any { it.possessive } && b.adjectives.any { !it.possessive })
        // a session can be planned from it for an A2 learner with the bank's own nouns
        val p = CasePool(b, emptyList(), emptySet(), "A2", false, "en", { levels[it] })
        val plan = CaseTrainer.plan(p, null, emptyList(), { 0.0 }, today, Random(7))
        assertEquals(CaseTrainer.RUN, plan.size)
        assertEquals(setOf(CaseKind.WHICH, CaseKind.PLACE, CaseKind.DECLINE), plan.map { it.task.kind }.toSet())
    }

    @Test fun `tokens split at spaces and the punctuation comes off a word`() {
        assertEquals(listOf("Babica", "je", "v", "šoli."), CaseBanks.tokens("  Babica je  v šoli. "))
        assertEquals("šoli", CaseBanks.bare("šoli."))
        assertEquals("Dober", CaseBanks.bare("„Dober"))
        assertEquals("", CaseBanks.bare("—"))
    }

    // --- grading ---------------------------------------------------------------------------------------------------------

    @Test fun `a case picked is right only when it is the item's`() {
        val ex = exerciseOf("brez-mleka")
        assertTrue(CaseTrainer.pickRight(ex, Kase.RODILNIK))
        assertFalse(CaseTrainer.pickRight(ex, Kase.TOZILNIK))
    }

    @Test fun `the deciding word is its trigger or a word beside it`() {
        val brez = exerciseOf("brez-mleka")
        assertTrue(CaseTrainer.whyRight(brez, 2, null)) // brez
        assertFalse(CaseTrainer.whyRight(brez, 1, null)) // pijem
        assertFalse("a role where a word decides", CaseTrainer.whyRight(brez, null, "object"))
        val kam = exerciseOf("na-posto")
        assertTrue("v or na", CaseTrainer.whyRight(kam, 1, null))
        assertTrue("and the verb of motion", CaseTrainer.whyRight(kam, 0, null))
    }

    @Test fun `nothing decides a subject or an object and its verb is right for an object`() {
        val subject = exerciseOf("luka-pase")
        assertTrue(CaseTrainer.whyRight(subject, null, "subject"))
        assertFalse(CaseTrainer.whyRight(subject, null, "object"))
        assertFalse("a word tapped for a subject", CaseTrainer.whyRight(subject, 1, null))
        val obj = exerciseOf("pase-ovce")
        assertTrue(CaseTrainer.whyRight(obj, null, "object"))
        assertTrue("its verb", CaseTrainer.whyRight(obj, 1, null))
        assertFalse(CaseTrainer.whyRight(obj, null, "subject"))
    }

    @Test fun `the verdict is the case's and almost when only the deciding word was wrong`() {
        assertEquals(Verdict.CORRECT, CaseTrainer.verdict(true, true))
        assertEquals(Verdict.ALMOST, CaseTrainer.verdict(true, false))
        assertEquals(Verdict.WRONG, CaseTrainer.verdict(false, true))
        assertEquals(Verdict.WRONG, CaseTrainer.verdict(false, false))
        assertEquals("no why asked", Verdict.CORRECT, CaseTrainer.verdict(true, null))
    }

    @Test fun `a place item hides its gap with the noun in brackets and shuffles its options`() {
        val ex = CaseTrainer.item(pool(), items.first { it.id == "gre-na-trznico" }, seed = 3)!!
        assertEquals("Micka gre ___ (tržnica).", ex.prompt)
        assertEquals(setOf("na tržnico", "na tržnici"), ex.options.toSet())
        assertEquals("na tržnico", ex.options[ex.answer])
        assertEquals(listOf(1), ex.taps)
        assertEquals("gre", ex.wordAt(1))
        assertEquals("na-trznici", ex.scene)
        assertNull("no picture where the village hasn't the scene", CaseTrainer.item(pool(scenes = emptySet()), items.first { it.id == "gre-na-trznico" })!!.scene)
        val vna = exerciseOf("v-ali-na")
        assertEquals("Grem ___ pošto.", vna.prompt)
        assertFalse("v or na asks nothing more", vna.askWhy)
    }

    @Test fun `a typed form is graded as any typed answer`() {
        val ex = CaseTrainer.decline(pool(), frames.first { it.id == "danes-ni" }, miza, null)!!
        assertEquals(Verdict.CORRECT, CaseTrainer.gradeDecline(ex, "mize").verdict)
        assertEquals(Verdict.CORRECT, CaseTrainer.gradeDecline(ex, "Mize.").verdict)
        assertEquals("the whole sentence", Verdict.CORRECT, CaseTrainer.gradeDecline(ex, "Danes ni mize.").verdict)
        assertEquals("a wrong ending", Verdict.WRONG, CaseTrainer.gradeDecline(ex, "mizo").verdict)
        assertEquals("the nominative", Verdict.WRONG, CaseTrainer.gradeDecline(ex, "miza").verdict)
        val k = CaseTrainer.decline(pool(), frames.first { it.id == "grem-k" }, sestra, null)!!
        assertEquals("with its preposition", Verdict.CORRECT, CaseTrainer.gradeDecline(k, "k sestri").verdict)
        val z = CaseTrainer.decline(pool(), frames.first { it.id == "pridem-z" }, sestra, null)!!
        val noCaron = CaseTrainer.gradeDecline(z, "sestro")
        assertEquals(Verdict.CORRECT, noCaron.verdict)
        val d = CaseTrainer.decline(pool(), frames.first { it.id == "vidim" }, noun("čaša", "f", "nom.sg" to "čaša", "acc.sg" to "čašo"), null)!!
        val typed = CaseTrainer.gradeDecline(d, "caso")
        assertEquals("č, š, ž forgotten", Verdict.ALMOST, typed.verdict)
        assertEquals(Grading.Hint.DIACRITICS, typed.hint)
    }

    @Test fun `a spoken form is right and labelled never wrong`() {
        val n = noun("sestra", "f", "nom.sg" to "sestra", "acc.du" to "sestri", spoken = mapOf("acc.du" to listOf("sestre")))
        val ex = CaseTrainer.decline(pool(), frames.first { it.id == "vidim-dva" }, n, null)!!
        val g = CaseTrainer.gradeDecline(ex, "sestre")
        assertEquals(Verdict.CORRECT, g.verdict)
        assertTrue(g.spoken)
        assertEquals("sestri", ex.shown)
    }

    // --- frames from the forms book ------------------------------------------------------------------------------------

    @Test fun `a frame takes the learner's noun in its case and says what it means`() {
        val ex = CaseTrainer.decline(pool(), frames.first { it.id == "danes-ni" }, miza, null)!!
        assertEquals("Danes ni ___. (miza)", ex.prompt)
        assertEquals("Danes ni mize.", ex.sentence)
        assertEquals(listOf("mize"), ex.accept)
        assertEquals("rodilnik", ex.case)
        assertEquals("rodilnik-nikalnica", ex.rule)
        assertTrue(ex.hint!!.startsWith("There's no … today."))
        assertTrue(ex.hint!!.contains("miza = meaning of miza"))
    }

    @Test fun `k becomes h and s becomes z as the word after them wants`() {
        assertEquals("Grem h ___. (kmet)", CaseTrainer.decline(pool(), frames.first { it.id == "grem-k" }, kmet, null)!!.prompt)
        assertEquals("Grem k ___. (miza)", CaseTrainer.decline(pool(), frames.first { it.id == "grem-k" }, miza, null)!!.prompt)
        assertEquals("Pridem s ___. (sestra)", CaseTrainer.decline(pool(), frames.first { it.id == "pridem-z" }, sestra, null)!!.prompt)
        assertEquals("Pridem z ___. (brat)", CaseTrainer.decline(pool(), frames.first { it.id == "pridem-z" }, brat, null)!!.prompt)
        // with a possessive the first word decides: k mojemu, z mojo
        assertEquals("Grem k ___. (moj kmet)", CaseTrainer.decline(pool(), frames.first { it.id == "grem-k" }, kmet, adjectives[0])!!.prompt)
    }

    @Test fun `an adjective agrees with its noun and a person's accusative is the genitive`() {
        val moj = adjectives.first { it.lemma == "moj" }
        val vidim = frames.first { it.id == "vidim" }
        assertEquals(listOf("mojega brata"), CaseTrainer.decline(pool(), vidim, brat, moj)!!.accept)
        assertEquals(listOf("moj stol"), CaseTrainer.decline(pool(), vidim, stol, moj)!!.accept)
        assertEquals(listOf("mojo mizo"), CaseTrainer.decline(pool(), vidim, miza, moj)!!.accept)
        val toJe = CaseTrainer.decline(pool(), frames.first { it.id == "to-je" }, okno, moj)!!
        assertEquals("the neuter's moje", listOf("moje okno"), toJe.accept)
        assertEquals("To je ___. (moje okno)", toJe.prompt)
        assertTrue(toJe.possessive)
        assertEquals(listOf("mojemu bratu"), CaseTrainer.decline(pool(), frames.first { it.id == "grem-k" }, brat, moj)!!.accept.take(1))
        assertEquals("lepe mize", CaseTrainer.decline(pool(), frames.first { it.id == "danes-ni" }, miza, adjectives[1])!!.shown)
        assertTrue(brat.animate)
        assertFalse(stol.animate)
    }

    @Test fun `the dual says dva or dve and the plural is marked`() {
        assertEquals("Vidim dva ___. (brat)", CaseTrainer.decline(pool(), frames.first { it.id == "vidim-dva" }, brat, null)!!.prompt)
        assertEquals("Vidim dve ___. (miza)", CaseTrainer.decline(pool(), frames.first { it.id == "vidim-dva" }, miza, null)!!.prompt)
        assertEquals("Vidim ___. (miza, mn.)", CaseTrainer.decline(pool(), frames.first { it.id == "vidim-pl" }, miza, null)!!.prompt)
    }

    @Test fun `the usual form is shown first and every form is right`() {
        assertEquals(listOf("bratu", "brati"), CaseTrainer.shownForms(brat, "dat.sg"))
        assertEquals(listOf("sester", "sestra"), CaseTrainer.shownForms(sestra, "gen.pl"))
        val ex = CaseTrainer.decline(pool(), frames.first { it.id == "grem-k" }, brat, null)!!
        assertEquals("bratu", ex.shown)
        assertEquals(Verdict.CORRECT, CaseTrainer.gradeDecline(ex, "brati").verdict)
    }

    @Test fun `a frame needs its noun's form its pages and its level`() {
        val p = pool(level = "A1")
        assertFalse("A2's dative at A1", CaseTrainer.allowed(p, frames.first { it.id == "grem-k" }))
        assertTrue("introduced, one level up", CaseTrainer.allowed(pool(level = "A1", introduced = setOf("dajalnik")), frames.first { it.id == "grem-k" }))
        assertTrue(CaseTrainer.allowed(p, frames.first { it.id == "danes-ni" }))
        val plan = CaseTrainer.plan(pool(nouns = listOf(kmet)), null, emptyList(), { 0.0 }, today, Random(1), only = CaseKind.DECLINE, size = 20)
        assertTrue("no plural without kmet's plural", plan.none { it.task.number == "pl" })
    }

    @Test fun `a mass noun stays in the singular`() {
        val voda = noun("voda", "f", "nom.sg" to "voda", "gen.sg" to "vode", "dat.sg" to "vodi", "acc.sg" to "vodo", "loc.sg" to "vodi", "ins.sg" to "vodo",
            "acc.pl" to "vode", "acc.du" to "vodi")
        val p = CasePool(bank.copy(uncountable = listOf("voda")), listOf(voda), setOf("mnozina", "dvojina"), "A2", false, "en", { levels[it] }, emptySet())
        val plan = CaseTrainer.plan(p, null, emptyList(), { 0.0 }, today, Random(4), only = CaseKind.DECLINE, size = 20)
        assertTrue("some singular", plan.any { it.task.number == "sg" })
        assertTrue("no «Vidim vode» nor «Vidim dve vodi»: ${plan.map { it.exercise.sentence }}", plan.none { it.task.number != "sg" })
    }

    // --- what may be asked, and the weighting ----------------------------------------------------------------------------

    @Test fun `the trainer opens once the cases have begun`() {
        assertFalse("A1, nothing of the cases met", CaseTrainer.isOpen(pool(level = "A1")))
        assertTrue("the unit tozilnik's intro", CaseTrainer.isOpen(pool(level = "A1", introduced = setOf("tozilnik"))))
        assertTrue("A2", CaseTrainer.isOpen(pool(level = "A2")))
        assertFalse("nothing to ask", CaseTrainer.isOpen(CasePool(CaseBank(), emptyList(), setOf("tozilnik"), "A2")))
    }

    @Test fun `an item waits for its page or the learner's level and a child gets no B1 nor long sentences`() {
        assertFalse("rodilnik-predlogi is A2", CaseTrainer.allowed(pool(level = "A1"), items.first { it.id == "brez-mleka" }))
        assertTrue("introduced", CaseTrainer.allowed(pool(level = "A1", introduced = setOf("rodilnik-predlogi")), items.first { it.id == "brez-mleka" }))
        assertTrue(CaseTrainer.allowed(pool(level = "B1"), items.first { it.id == "b1-dolg" }))
        assertFalse("a child: no B1", CaseTrainer.allowed(pool(level = "B1", child = true), items.first { it.id == "b1-dolg" }))
        val child = CaseTrainer.plan(pool(level = "A2", child = true), null, emptyList(), { 0.0 }, today, Random(2))
        assertEquals(CaseTrainer.RUN_CHILD, child.size)
        assertTrue(child.all { it.exercise.tokens.size <= CaseTrainer.CHILD_WORDS || it.task.kind == CaseKind.DECLINE })
    }

    @Test fun `the learner's mistakes weigh as wrong answers that fade`() {
        val fresh = CaseTrainer.weakness(null, "case:rodilnik", 0.0)
        val jan = CaseTrainer.weakness(null, "case:rodilnik", 1.0)
        assertEquals(0.5, fresh, 1e-9)
        assertTrue("nine genitive mistakes: weak at once ($jan)", jan > 0.8)
        val right = CaseProgress(keys = mapOf("case:rodilnik" to "1".repeat(CaseTrainer.WINDOW)))
        assertTrue("twelve right since: no longer", CaseTrainer.weakness(right, "case:rodilnik", 1.0) < 0.1)
        val half = CaseProgress(keys = mapOf("case:rodilnik" to "111111"))
        assertTrue("fading", CaseTrainer.weakness(half, "case:rodilnik", 1.0) < jan)
    }

    @Test fun `items are drawn towards the weak case`() {
        fun share(priors: (String) -> Double, p: CaseProgress?): Double {
            var gen = 0
            var all = 0
            for (seed in 0 until 300) {
                val plan = CaseTrainer.plan(pool(), p, emptyList(), priors, today, Random(seed.toLong()), only = CaseKind.WHICH, size = 3)
                gen += plan.count { it.task.case == Kase.RODILNIK }
                all += plan.size
            }
            return gen.toDouble() / all
        }
        val even = share({ 0.0 }, null)
        val weakGen = share({ if (it == "rodilnik" || it == "rodilnik-predlogi" || it == "rodilnik-nikalnica") 1.0 else 0.0 }, null)
        assertTrue("the genitive's mistakes: more genitive ($even → $weakGen)", weakGen > even + 0.05)
        val strong = CaseProgress(keys = Kase.entries.associate { CaseTrainer.caseKey(it) to "1".repeat(12) } + ("case:rodilnik" to "000000000000"))
        assertTrue("the genitive wrong lately: more genitive", share({ 0.0 }, strong) > even + 0.1)
    }

    @Test fun `the learner's own mistake that fits an item weighs it`() {
        val t = CaseTrainer.taskOf(pool(), items.first { it.id == "na-posto" })!!.copy(gender = "f")
        val facets = CaseTrainer.taskFacets(t, rules["v-na-kam"], "v-na-kam")
        val janAcc = listOf(MistakeNote("accusative_feminine_a_to_o", 8))
        assertEquals(1.0, CaseTrainer.prior(t, facets, janAcc), 1e-9)
        val masc = t.copy(gender = "m")
        assertEquals(0.0, CaseTrainer.prior(masc, CaseTrainer.taskFacets(masc, rules["v-na-kam"], "v-na-kam"), janAcc), 1e-9)
        val w0 = CaseTrainer.weight(t, null, { 0.0 }, 0.0, null, "A2", recent = false, seen = false)
        val w1 = CaseTrainer.weight(t, null, { 0.0 }, 1.0, null, "A2", recent = false, seen = false)
        assertTrue(w1 > w0)
        assertTrue("asked lately: less", CaseTrainer.weight(t, null, { 0.0 }, 0.0, null, "A2", recent = true, seen = false) < w0)
        assertTrue("the lens showed it: a little more", CaseTrainer.weight(t, null, { 0.0 }, 0.0, null, "A2", recent = false, seen = true) > w0)
    }

    @Test fun `the level follows the learner's accuracy towards 60 to 70 percent`() {
        val t = CaseTrainer.taskOf(pool(), items.first { it.id == "nimam-casa" })!! // A1
        val easyHigh = CaseTrainer.weight(t, null, { 0.0 }, 0.0, 0.9, "A2", false, false)
        val easyLow = CaseTrainer.weight(t, null, { 0.0 }, 0.0, 0.4, "A2", false, false)
        assertTrue("an A1 item for an A2 learner: less while they get most right", easyHigh < easyLow)
    }

    @Test fun `a focus asks its case or its rule and fills up with its case`() {
        val onGen = CaseTrainer.plan(pool(), null, emptyList(), { 0.0 }, today, Random(4), focus = "rodilnik", size = 6)
        assertTrue(onGen.isNotEmpty())
        assertTrue(onGen.all { it.task.case == Kase.RODILNIK })
        val onRule = CaseTrainer.plan(pool(), null, emptyList(), { 0.0 }, today, Random(4), focus = "kje-mestnik-orodnik", size = 4)
        assertTrue("its rule first", onRule.take(2).all { it.task.rule == "kje-mestnik-orodnik" || it.task.case == Kase.MESTNIK })
        val kjeKam = CaseTrainer.plan(pool(), null, emptyList(), { 0.0 }, today, Random(4), focus = CaseTrainer.KJE_KAM, size = 4)
        assertTrue(kjeKam.all { CaseTrainer.focused(it.task, CaseTrainer.KJE_KAM) })
        val agreement = CaseTrainer.plan(pool(), null, emptyList(), { 0.0 }, today, Random(4), focus = CaseTrainer.AGREEMENT, size = 4)
        assertTrue(agreement.all { it.task.adjective != null })
    }

    @Test fun `a session mixes the kinds and never the same kind twice in a row when it can`() {
        for (seed in 0 until 40) {
            val plan = CaseTrainer.plan(pool(), null, emptyList(), { 0.0 }, today, Random(seed.toLong()))
            assertEquals(CaseTrainer.RUN, plan.size)
            val kinds = plan.map { it.task.kind }
            assertEquals("seed $seed: ${kinds}", 3, kinds.toSet().size)
            val twice = kinds.zipWithNext().count { (a, b) -> a == b }
            assertTrue("seed $seed: $kinds", twice <= 2)
            assertEquals("no item twice", plan.size, plan.map { it.task.key }.toSet().size)
        }
    }

    @Test fun `the kinds' quotas add up and give a kind's share away when it has none`() {
        val q = CaseTrainer.quotas(CaseTrainer.SHARES, 10, mapOf(CaseKind.WHICH to 50, CaseKind.PLACE to 50, CaseKind.DECLINE to 50))
        assertEquals(10, q.values.sum())
        assertEquals(mapOf(CaseKind.WHICH to 5, CaseKind.PLACE to 2, CaseKind.DECLINE to 3), q.toSortedMap())
        val none = CaseTrainer.quotas(mapOf(CaseKind.WHICH to 0.45, CaseKind.PLACE to 0.25), 10, mapOf(CaseKind.WHICH to 50, CaseKind.PLACE to 1))
        assertEquals(mapOf(CaseKind.WHICH to 9, CaseKind.PLACE to 1), none.toSortedMap())
    }

    @Test fun `items missed and due come first spread through the session`() {
        val p = CaseProgress(again = listOf(CaseAgain("brez-mleka", today.toString()), CaseAgain("k-babici", today.minusDays(2).toString(), 2), CaseAgain("d:danes-ni:miza", today.toString()), CaseAgain("o-medvedu", today.plusDays(3).toString(), 3)))
        val plan = CaseTrainer.plan(pool(), p, emptyList(), { 0.0 }, today, Random(5))
        val again = plan.filter { it.task.again }.map { it.task.key }
        assertEquals(setOf("brez-mleka", "k-babici", "d:danes-ni:miza"), again.toSet())
        assertTrue("not due yet", plan.none { it.task.key == "o-medvedu" && it.task.again })
        assertTrue("the first one first", plan.first().task.again)
        // a key of a word the learner no longer has: skipped
        assertNull(CaseTrainer.taskOfKey(pool(nouns = listOf(brat)), "d:danes-ni:miza"))
    }

    // --- mastery ----------------------------------------------------------------------------------------------------------

    private fun task(id: String) = CaseTrainer.taskOf(pool(), items.first { it.id == id })!!

    @Test fun `answers count on their case rule and kind and the deciding word on its rule`() {
        val tasks = listOf(task("brez-mleka"), task("k-babici"), task("nimam-casa"))
        val answers = listOf(
            CaseAnswer(Verdict.CORRECT, true, "2. rodilnik · brez", ""),
            CaseAnswer(Verdict.ALMOST, false, "3. dajalnik · Grem", ""),
            CaseAnswer(Verdict.WRONG, null, "4. tožilnik", ""),
        )
        val r = CaseTrainer.record(null, tasks, answers, today)
        assertEquals("10", r.progress.keys["case:rodilnik"])
        assertEquals("1", r.progress.keys["case:dajalnik"])
        assertEquals("1", r.progress.keys["rule:rodilnik-predlogi"])
        assertEquals("0", r.progress.keys["rule:rodilnik-nikalnica"])
        assertEquals("110", r.progress.keys["kind:which"])
        assertEquals("1", r.progress.why["rodilnik-predlogi"])
        assertEquals("0", r.progress.why["dajalnik"])
        assertNull("not asked", r.progress.why["rodilnik-nikalnica"])
        assertEquals(1, r.progress.runs)
        assertEquals(today.toString(), r.progress.day)
        assertEquals(listOf("nimam-casa"), r.missed)
        assertEquals(listOf("brez-mleka", "k-babici", "nimam-casa"), r.progress.recent)
        assertEquals(2 to 3, CaseTrainer.score(r.progress, "kind:which"))
    }

    @Test fun `a decline with an adjective counts on agreement too`() {
        val t = CaseTask(CaseKind.DECLINE, "d:vidim:brat:moj", Kase.TOZILNIK, "sg", "tozilnik", "A1", adjective = "moj", gender = "m")
        val r = CaseTrainer.record(null, listOf(t), listOf(CaseAnswer(Verdict.WRONG, null, "moj brata", "mojega brata")), today)
        assertEquals("0", r.progress.keys["rule:pridevniki-ujemanje"])
    }

    @Test fun `a missed item comes back the same day then after 1 3 7 and 16 days`() {
        val t = task("brez-mleka")
        var p = CaseTrainer.record(null, listOf(t), listOf(CaseAnswer(Verdict.WRONG, true, "", "")), today).progress
        assertEquals(listOf(CaseAgain("brez-mleka", today.toString(), 0)), p.again)
        var day = today
        for ((box, days) in listOf(1 to 1L, 2 to 3L, 3 to 7L, 4 to 16L)) {
            p = CaseTrainer.record(p, listOf(t), listOf(CaseAnswer(Verdict.CORRECT, true, "", "")), day).progress
            assertEquals(CaseAgain("brez-mleka", day.plusDays(days).toString(), box), p.again.single())
            day = day.plusDays(days)
        }
        p = CaseTrainer.record(p, listOf(t), listOf(CaseAnswer(Verdict.CORRECT, true, "", "")), day).progress
        assertTrue("right at the last box: gone", p.again.isEmpty())
        val almost = CaseTrainer.record(null, listOf(t), listOf(CaseAnswer(Verdict.ALMOST, false, "", "")), today).progress
        assertEquals(CaseAgain("brez-mleka", today.plusDays(1).toString(), 1), almost.again.single())
        val wrongAgain = CaseTrainer.record(p.copy(again = listOf(CaseAgain("brez-mleka", today.toString(), 3))), listOf(t), listOf(CaseAnswer(Verdict.WRONG, false, "", "")), today).progress
        assertEquals("wrong again: back to the first box", 0, wrongAgain.again.single().box)
    }

    @Test fun `stars follow the last answers capped while they are few`() {
        assertNull(CaseTrainer.stars(null, "case:rodilnik"))
        assertEquals(2, CaseTrainer.stars(CaseProgress(keys = mapOf("k" to "11")), "k"))
        assertEquals(5, CaseTrainer.stars(CaseProgress(keys = mapOf("k" to "1".repeat(12))), "k"))
        assertEquals(3, CaseTrainer.stars(CaseProgress(keys = mapOf("k" to "101101101101")), "k"))
    }

    @Test fun `the lens's seen signal counts lightly never as an answer`() {
        val p = CaseTrainer.seen(CaseTrainer.seen(null, "rodilnik-predlogi", today.minusDays(1)), "rodilnik-predlogi", today)
        assertEquals(CaseSeen(2, today.toString()), p.seen["rodilnik-predlogi"])
        assertTrue(p.keys.isEmpty())
        assertEquals(0, p.runs)
    }

    @Test fun `the weakest case is the one to suggest`() {
        val p = CaseProgress(keys = Kase.entries.associate { CaseTrainer.caseKey(it) to "1".repeat(12) } + ("case:dajalnik" to "000011110000"))
        assertEquals(Kase.DAJALNIK, CaseTrainer.weakest(p, { 0.0 })?.first)
        assertNull("none weak", CaseTrainer.weakest(CaseProgress(keys = Kase.entries.associate { CaseTrainer.caseKey(it) to "1".repeat(12) }), { 0.0 }))
        assertEquals("Jan's genitive at once", Kase.RODILNIK, CaseTrainer.weakest(null, { if (it == "rodilnik") 1.0 else 0.0 })?.first)
    }

    // --- mistakes and the node --------------------------------------------------------------------------------------------

    @Test fun `a mistake's pattern id is read as facets`() {
        assertEquals(setOf("genitive", "iz"), CaseTrainer.patternFacets("genitive_after_iz"))
        assertEquals(setOf("accusative", "feminine"), CaseTrainer.patternFacets("accusative_feminine_a_to_o"))
        assertEquals(setOf("kam", "kje"), CaseTrainer.patternFacets("kam_vs_kje"))
        assertEquals(setOf("v", "na"), CaseTrainer.patternFacets("v_vs_na"))
        assertEquals(setOf("possessive", "neuter"), CaseTrainer.patternFacets("possessive_neuter_moje"))
        assertEquals(setOf("genitive", "negation"), CaseTrainer.patternFacets("genitive_after_negation"))
        assertTrue(CaseTrainer.patternFacets("vocab_hisa").isEmpty())
    }

    @Test fun `a wrong answer goes on the learner's own pattern that fits it, the most specific`() {
        val mistakes = listOf(
            MistakeNote("genitive_forms", 9), MistakeNote("genitive_after_iz", 3), MistakeNote("genitive_after_brez", 1),
            MistakeNote("accusative_feminine_a_to_o", 8), MistakeNote("kam_vs_kje", 7), MistakeNote("v_vs_na", 4),
            MistakeNote("adjective_gender_agreement", 9), MistakeNote("possessive_neuter_moje", 6), MistakeNote("vocab_hisa", 2),
        )
        fun pat(t: CaseTask, key: String) = CaseTrainer.patternFor(t, CaseTrainer.taskFacets(t, rules[key], key), mistakes)?.id
        assertEquals("genitive_after_brez", pat(task("brez-mleka"), "brez"))
        assertEquals("genitive_forms", pat(task("nimam-casa"), "nikalnica"))
        assertEquals("kam_vs_kje", pat(task("na-posto").copy(gender = "m"), "v-na-kam"))
        assertEquals("accusative_feminine_a_to_o", pat(task("na-posto").copy(gender = "f"), "v-na-kam"))
        val neuter = CaseTask(CaseKind.DECLINE, "d:to-je:okno:moj", Kase.IMENOVALNIK, "sg", "skloni", "A1", adjective = "moj", gender = "n")
        assertEquals("possessive_neuter_moje", pat(neuter, "subject"))
        val agreement = CaseTask(CaseKind.DECLINE, "d:vidim:hisa:lep", Kase.TOZILNIK, "sg", "tozilnik", "A1", adjective = "lep", gender = "m")
        assertEquals("adjective_gender_agreement", pat(agreement, "object"))
        assertNull("no fit", pat(task("s-kladivom"), "z-s"))
    }

    @Test fun `the errors for the learner databases are the wrong answers with their patterns`() {
        val p = pool()
        val planned = listOf("brez-mleka", "s-kladivom", "k-babici", "o-medvedu").map { id -> task(id) to exerciseOf(id) }
        val answers = listOf(
            CaseAnswer(Verdict.WRONG, true, "4. tožilnik · brez", "2. rodilnik · brez"),
            CaseAnswer(Verdict.WRONG, false, "2. rodilnik · kuje", "6. orodnik · s"),
            CaseAnswer(Verdict.ALMOST, false, "3. dajalnik · Grem", "3. dajalnik · k"),
            CaseAnswer(Verdict.CORRECT, true, "", ""),
        )
        val e = CaseTrainer.errors(p, planned.map { it.first }, planned.map { it.second }, answers, listOf(MistakeNote("genitive_after_brez", 1)))
        assertEquals(3, e.size)
        fun id(i: Int) = e[i].jsonObject["pattern_id"]!!.jsonPrimitive.content
        assertEquals("genitive_after_brez", id(0))
        assertEquals("cases_instrumental_z_s", id(1))
        assertEquals("the deciding word missed", "cases_trigger_k", id(2))
        assertEquals("minor", e[2].jsonObject["severity"]!!.jsonPrimitive.content)
        assertEquals("grammar", e[0].jsonObject["category"]!!.jsonPrimitive.content)
        assertEquals("cases_form_dative_plural_possessive", CaseTrainer.ownPattern(CaseTask(CaseKind.DECLINE, "d:x:y:moj", Kase.DAJALNIK, "pl", "dajalnik", "A2", adjective = "moj"), "k"))
    }

    @Test fun `the node is sent each answer the errors and the mastery after`() {
        val p = pool()
        val t = listOf(task("brez-mleka"), task("na-posto"))
        val ex = listOf(exerciseOf("brez-mleka"), exerciseOf("na-posto"))
        val answers = listOf(CaseAnswer(Verdict.CORRECT, true, "2. rodilnik · brez", "2. rodilnik · brez"), CaseAnswer(Verdict.WRONG, true, "5. mestnik · na", "4. tožilnik · na"))
        val rec = CaseTrainer.record(null, t, answers, today)
        val w: JsonObject = CaseTrainer.wire(p, rec, t, ex, answers, emptyList(), 3, "rodilnik")
        assertEquals(3, w["duration_minutes"]!!.jsonPrimitive.content.toInt())
        assertEquals("rodilnik", w["focus"]!!.jsonPrimitive.content)
        val a = w["answers"]!!.jsonArray
        assertEquals(2, a.size)
        assertEquals("which", a[0].jsonObject["kind"]!!.jsonPrimitive.content)
        assertEquals("rodilnik-predlogi", a[0].jsonObject["rule"]!!.jsonPrimitive.content)
        assertEquals("right", a[0].jsonObject["why"]!!.jsonPrimitive.content)
        assertEquals("wrong", a[1].jsonObject["verdict"]!!.jsonPrimitive.content)
        assertEquals(1, w["errors"]!!.jsonArray.size)
        assertEquals("1", w["mastery"]!!.jsonObject["rodilnik"]!!.jsonObject["right"]!!.jsonPrimitive.content)
        assertEquals("true", w["machine_written"]!!.jsonPrimitive.content)
    }

    // --- the storm ------------------------------------------------------------------------------------------------------

    @Test fun `a storm's phrase is the deciding word with the word asked about or a short sentence whole`() {
        assertEquals(listOf("brez", "mleka.") .map(CaseBanks::bare) to 1, CaseTrainer.phrase(items.first { it.id == "brez-mleka" }))
        assertEquals(listOf("v", "veliki", "hiši") to 1, CaseTrainer.phrase(items.first { it.id == "v-veliki-hisi" }))
        assertEquals("a subject: the sentence", items.first { it.id == "luka-pase" }.tokens to 0, CaseTrainer.phrase(items.first { it.id == "luka-pase" }))
        assertEquals("the deciding word lower case at the start", listOf("ne", "vidim", "ovce") to 2, CaseTrainer.phrase(which("ne-vidim", "Ne vidim ovce.", 2, "ovca", "rodilnik", "nikalnica", 0)))
        assertNull("a long sentence with its trigger far away", CaseTrainer.phrase(which("dolgo", "Danes zjutraj pa res ni bilo nikjer ne kruha ne mleka.", 10, "mleko", "rodilnik", "nikalnica", 4)))
        assertNull("a place item is no phrase", CaseTrainer.phrase(items.first { it.id == "gre-na-trznico" }))
    }

    @Test fun `a storm asks phrases only nothing more and never one case three times in a row`() {
        val s = CaseTrainer.storm(pool(), null, emptyList(), { 0.0 }, today, Random(3))
        assertTrue(s.isNotEmpty())
        assertTrue(s.all { it.task.kind == CaseKind.WHICH && !it.exercise.askWhy && it.exercise.tokens.size <= 6 })
        assertEquals("each phrase once", s.size, s.map { it.exercise.tokens.joinToString(" ") }.toSet().size)
        val cases = s.map { it.task.case }
        assertTrue("$cases", cases.windowed(3).count { w -> w.toSet().size == 1 } <= 2)
        val brez = s.first { it.task.key == "brez-mleka" }.exercise
        assertEquals("mleka", brez.tokens[brez.word])
    }

    @Test fun `a storm's medal and its longest streak`() {
        val c = Verdict.CORRECT
        val w = Verdict.WRONG
        assertEquals(CaseTrainer.Medal.GOLD, CaseTrainer.medal(List(15) { c } + listOf(w)))
        assertEquals("many right, too many wrong", CaseTrainer.Medal.SILVER, CaseTrainer.medal(List(15) { c } + List(5) { w }))
        assertEquals(CaseTrainer.Medal.BRONZE, CaseTrainer.medal(List(5) { c }))
        assertEquals(4, CaseTrainer.streak(listOf(c, c, w, c, c, c, c, w, c)))
        assertEquals(0, CaseTrainer.streak(emptyList()))
    }

    // --- focuses, Home ----------------------------------------------------------------------------------------------------

    @Test fun `the school's case units and the book's pages have their focus`() {
        assertEquals("rodilnik-nikalnica", CaseTrainer.focusOfUnit("nikalnica-rodilnik"))
        assertEquals(CaseTrainer.KJE_KAM, CaseTrainer.focusOfUnit("kje-kam-od-kod"))
        assertEquals(CaseTrainer.AGREEMENT, CaseTrainer.focusOfUnit("pridevniki-skloni"))
        assertNull("every case", CaseTrainer.focusOfUnit("skloni"))
        val pages = rules.values.map { it.page }
        assertEquals("rodilnik", CaseTrainer.focusOfPage("rodilnik", pages))
        assertEquals("rodilnik-predlogi", CaseTrainer.focusOfPage("rodilnik-predlogi", pages))
        assertNull(CaseTrainer.focusOfPage("skloni-x", pages))
        val plural = CaseTask(CaseKind.WHICH, "x", Kase.MESTNIK, "pl", "mestnik", "A2")
        assertTrue(CaseTrainer.focused(plural, CaseTrainer.PLURAL_CASES))
        assertFalse(CaseTrainer.focused(plural, CaseTrainer.DUAL_CASES))
    }

    @Test fun `Home offers the tutor's assignment then what comes back then the weakest case`() {
        val label = { id: String -> "label of $id" }
        val assigned = CaseAssigned("rodilnik-predlogi", "iz and brez", "2026-10-29")
        val a = HomeLogic.casesIdea(true, CaseProgress(day = today.toString()), 2, Kase.DAJALNIK, label, assigned, today)!!
        assertTrue("even when played today", a.assigned)
        assertEquals("rodilnik-predlogi", a.focus)
        assertNull("played today", HomeLogic.casesIdea(true, CaseProgress(day = today.toString()), 2, Kase.DAJALNIK, label, null, today))
        val due = HomeLogic.casesIdea(true, null, 2, Kase.DAJALNIK, label, null, today)!!
        assertEquals(2, due.due)
        assertNull("a mixed session: what's due comes first", due.focus)
        val weak = HomeLogic.casesIdea(true, null, 0, Kase.DAJALNIK, label, null, today)!!
        assertEquals("dajalnik", weak.focus)
        assertEquals("label of dajalnik", weak.label)
        assertNull("nothing weak", HomeLogic.casesIdea(true, null, 0, null, label, null, today))
        assertNull("closed", HomeLogic.casesIdea(false, null, 2, Kase.DAJALNIK, label, assigned, today))
    }
}
