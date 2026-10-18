package si.lanisce.lani.data

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
import si.lanisce.lani.data.ReviewPlanner.Variant
import si.lanisce.lani.game.CourseProgress
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.RuleRecord
import si.lanisce.lani.game.UnitProgress
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.time.LocalDate
import kotlin.random.Random

/**
 * Forms cards (data/FormsCard.kt): a word's whole table from the forms the node kept (the dictionary's: a noun, a verb with
 * its future joined, an adjective with its cases, the irregular ones, several forms of a slot), what's tested (the pages
 * introduced: met in the book, or taught by a unit whose intro is done; a slot a gap after its unit), the parts a review
 * asks (whole rows, in turn, a failed one again, a part newly introduced first), the choices (never one that is right, the
 * right one there), a gap's grading and the kind of a wrong one, the card's quality and what it counts on the grammar book,
 * which words are asked for and how many a day, how the cards join the review.
 */
class FormsCardTest {
    private val pair = L10n.pair

    @Before fun pair() {
        L10n.pair = LangPair(Lang.SL, Lang.EN)
    }

    @After fun back() {
        L10n.pair = pair
    }

    private val today = LocalDate.of(2026, 10, 18)
    private fun s(key: String, vararg forms: String, page: String, needs: List<String> = emptyList(), also: List<String> = emptyList()) =
        FormsCardSlot(key, forms.toList(), also, page, needs)

    // as the node makes them (companion/bridge/src/forms-cards.ts), from Wiktionary's forms and the forms book
    private val hisa = FormsCardData(
        "hiša", "noun", gender = "f", gloss = listOf("house"), meaning = "house", emoji = "🏠", table = "wiktionary",
        slots = listOf(
            s("nom.sg", "hiša", page = "imenovalnik"), s("gen.sg", "hiše", page = "rodilnik"), s("dat.sg", "hiši", page = "dajalnik"),
            s("acc.sg", "hišo", page = "tozilnik"), s("loc.sg", "hiši", page = "mestnik"), s("ins.sg", "hišo", page = "orodnik"),
            s("nom.pl", "hiše", page = "mnozina"), s("gen.pl", "hiš", page = "rodilnik", needs = listOf("mnozina")),
            s("dat.pl", "hišam", page = "dajalnik", needs = listOf("mnozina")), s("acc.pl", "hiše", page = "tozilnik", needs = listOf("mnozina")),
            s("loc.pl", "hišah", page = "mestnik", needs = listOf("mnozina")), s("ins.pl", "hišami", page = "orodnik", needs = listOf("mnozina")),
            s("nom.du", "hiši", page = "dvojina"), s("gen.du", "hiš", page = "dvojina", needs = listOf("rodilnik")),
            s("dat.du", "hišama", page = "dvojina", needs = listOf("dajalnik")), s("acc.du", "hiši", page = "dvojina", needs = listOf("tozilnik")),
            s("loc.du", "hišah", page = "dvojina", needs = listOf("mestnik")), s("ins.du", "hišama", page = "dvojina", needs = listOf("orodnik")),
        ),
    )

    private fun persons(head: String, page: String, forms: List<String>, alsos: Map<String, List<String>> = emptyMap()): List<FormsCardSlot> {
        val keys = listOf("1sg", "2sg", "3sg", "1pl", "2pl", "3pl", "1du", "2du", "3du")
        return keys.zip(forms).map { (p, f) ->
            val du = p.endsWith("du")
            FormsCardSlot("$head.$p", listOf(f), alsos["$head.$p"].orEmpty(), if (du) "dvojina" else page, if (du) listOf(page) else emptyList())
        }
    }

    private fun past(page: String = "pretekli-cas", m: String, f: String, n: String, mpl: String, fpl: String, mdu: String, fdu: String) = listOf(
        s("past.m.sg", m, page = page), s("past.f.sg", f, page = page), s("past.n.sg", n, page = page),
        s("past.m.pl", mpl, page = page), s("past.f.pl", fpl, page = page),
        s("past.m.du", mdu, page = "dvojina", needs = listOf(page)), s("past.f.du", fdu, page = "dvojina", needs = listOf(page)),
    )

    private fun imperative(sg: String, pl: String, firstPl: String, firstDu: String, du: String) = listOf(
        s("imp.2sg", sg, page = "velelnik"), s("imp.2pl", pl, page = "velelnik"), s("imp.1pl", firstPl, page = "velelnik"),
        s("imp.1du", firstDu, page = "dvojina", needs = listOf("velelnik")), s("imp.2du", du, page = "dvojina", needs = listOf("velelnik")),
    )

    private val delati = FormsCardData(
        "delati", "verb", gloss = listOf("to do", "to work"), meaning = "to work", table = "wiktionary",
        slots = persons("pres", "glagoli-sedanjik", listOf("delam", "delaš", "dela", "delamo", "delate", "delajo", "delava", "delata", "delata")) +
            persons(
                "fut", "prihodnjik",
                listOf("bom delal", "boš delal", "bo delal", "bomo delali", "boste delali", "bodo delali", "bova delala", "bosta delala", "bosta delala"),
                mapOf("fut.1sg" to listOf("bom delala"), "fut.3pl" to listOf("bojo delali"), "fut.1pl" to listOf("bomo delale")),
            ).map { it.copy(composed = true) } +
            past(m = "delal", f = "delala", n = "delalo", mpl = "delali", fpl = "delale", mdu = "delala", fdu = "delali") +
            imperative("delaj", "delajte", "delajmo", "delajva", "delajta"),
    )

    private val biti = FormsCardData(
        "biti", "verb", gloss = listOf("to be"), table = "checked",
        slots = persons("pres", "biti", listOf("sem", "si", "je", "smo", "ste", "so", "sva", "sta", "sta")) +
            persons("neg", "zanikanje", listOf("nisem", "nisi", "ni", "nismo", "niste", "niso", "nisva", "nista", "nista")).map {
                if (it.page == "zanikanje") it.copy(needs = listOf("biti")) else it.copy(needs = listOf("zanikanje"))
            } +
            persons("fut", "prihodnjik", listOf("bom", "boš", "bo", "bomo", "boste", "bodo", "bova", "bosta", "bosta"), mapOf("fut.3pl" to listOf("bojo"))) +
            past(m = "bil", f = "bila", n = "bilo", mpl = "bili", fpl = "bile", mdu = "bila", fdu = "bili") +
            imperative("bodi", "bodite", "bodimo", "bodiva", "bodita"),
    )

    private val iti = FormsCardData(
        "iti", "verb", gloss = listOf("to go"), table = "checked",
        slots = persons("pres", "imeti-iti", listOf("grem", "greš", "gre", "gremo", "greste", "gredo", "greva", "gresta", "gresta"), mapOf("pres.3pl" to listOf("grejo"))) +
            past(m = "šel", f = "šla", n = "šlo", mpl = "šli", fpl = "šle", mdu = "šla", fdu = "šli"),
    )

    private val clovek = FormsCardData(
        "človek", "noun", gender = "m", gloss = listOf("human", "person"), table = "wiktionary",
        slots = listOf(
            s("nom.sg", "človek", page = "imenovalnik"), s("gen.sg", "človeka", page = "rodilnik"), s("acc.sg", "človeka", page = "tozilnik"),
            s("loc.sg", "človeku", page = "mestnik"), s("nom.pl", "ljudje", page = "mnozina"), s("acc.pl", "ljudi", page = "tozilnik", needs = listOf("mnozina")),
            s("loc.pl", "ljudeh", page = "mestnik", needs = listOf("mnozina")), s("nom.du", "človeka", page = "dvojina"),
            s("loc.du", "človekih", "ljudeh", page = "dvojina", needs = listOf("mestnik")),
        ),
    )

    private val dober: FormsCardData = run {
        val adj = "pridevniki-ujemanje"
        fun case(c: String, page: String, m: String, f: String, n: String, pl: Triple<String, String, String>) = listOfNotNull(
            m.takeIf { it.isNotEmpty() }?.let { s("adj.$c.m.sg", it, page = adj, needs = listOf(page)) },
            s("adj.$c.f.sg", f, page = adj, needs = listOf(page)), s("adj.$c.n.sg", n, page = adj, needs = listOf(page)),
            s("adj.$c.m.pl", pl.first, page = adj, needs = listOf(page, "mnozina")), s("adj.$c.f.pl", pl.second, page = adj, needs = listOf(page, "mnozina")),
            s("adj.$c.n.pl", pl.third, page = adj, needs = listOf(page, "mnozina")),
        )
        FormsCardData(
            "dober", "adj", gloss = listOf("good"), table = "wiktionary",
            slots = listOf(
                s("adj.m.sg", "dober", page = adj), s("adj.f.sg", "dobra", page = adj), s("adj.n.sg", "dobro", page = adj),
                s("adj.m.pl", "dobri", page = adj, needs = listOf("mnozina")), s("adj.f.pl", "dobre", page = adj, needs = listOf("mnozina")),
                s("adj.n.pl", "dobra", page = adj, needs = listOf("mnozina")),
                s("adj.m.du", "dobra", page = "dvojina", needs = listOf(adj)), s("adj.f.du", "dobri", page = "dvojina", needs = listOf(adj)),
            ) + case("gen", "rodilnik", "dobrega", "dobre", "dobrega", Triple("dobrih", "dobrih", "dobrih")) +
                case("dat", "dajalnik", "dobremu", "dobri", "dobremu", Triple("dobrim", "dobrim", "dobrim")) +
                // the accusative masculine singular: "genitive" or the nominative in Wiktionary's table, by animacy: left out
                case("acc", "tozilnik", "", "dobro", "dobro", Triple("dobre", "dobre", "dobra")) +
                case("loc", "mestnik", "dobrem", "dobri", "dobrem", Triple("dobrih", "dobrih", "dobrih")) +
                case("ins", "orodnik", "dobrim", "dobro", "dobrim", Triple("dobrimi", "dobrimi", "dobrimi")),
        )
    }

    private val all = listOf(hisa, delati, biti, iti, clovek, dober)

    /** Every page the fixtures name: all introduced. */
    private val everything = all.flatMap { d -> d.slots.flatMap { listOf(it.page) + it.needs } }.toSet()

    private fun card(id: String, d: FormsCardData, reps: Int = 0, q: Int = 3, reviews: Int = 0, created: LocalDate? = null) =
        ReviewCard(id, d.lemma, d.slots.take(3).joinToString(" · ") { it.shown }, FormsCard.KIND, reps, q, created = created, forms = d, reviews = reviews)

    private fun exercise(d: FormsCardData, introduced: Set<String> = everything, c: ReviewCard = card("forms_x", d), typed: Boolean = false, seed: Int = 1) =
        FormsCard.exercise(c, d, introduced, typed, today, Random(seed), label = { it })!!

    private fun norm(s: String) = Grading.normalize(s, Lang.SL)
    private fun cells(ex: Exercise.FormsTable) = ex.sections.flatMap { s -> s.rows.flatMap { it.cells } }.filterNotNull()

    // --- the table ---------------------------------------------------------------------------------------------------

    @Test fun `a noun's table - its six cases by number, the word itself given, every form the dictionary's`() {
        val plans = FormsCard.layout(hisa)
        assertEquals(1, plans.size)
        val p = plans.single()
        assertEquals(listOf("sg", "du", "pl"), p.columns)
        assertEquals(listOf("1. kdo? kaj?", "2. koga? česa?", "3. komu? čemu?", "4. koga? kaj?", "5. pri kom? pri čem?", "6. s kom? s čim?"), p.rows.map { it.first })
        assertEquals(listOf("acc.sg", "acc.du", "acc.pl"), p.rows[3].second.map { it?.key })
        val ex = exercise(hisa)
        val nom = cells(ex).first { it.key == "nom.sg" }
        assertEquals(FormsCellState.GIVEN, nom.state)
        assertEquals("hiša", nom.form)
        assertTrue(ex.gaps.none { it.key == "nom.sg" })
        assertEquals("hišah", cells(ex).first { it.key == "loc.pl" }.form)
        // irregular as the dictionary has it: človek, ljudje; a slot's every form right
        val c = exercise(clovek, seed = 3)
        assertEquals("ljudje", cells(c).first { it.key == "nom.pl" }.form)
        assertEquals(listOf("človekih", "ljudeh"), clovek.slot("loc.du")!!.accept)
    }

    @Test fun `a verb's table - present by person, the future joined of bom and the l-form, the past by gender, the imperative`() {
        val plans = FormsCard.layout(delati)
        assertEquals(listOf("wordForms.present", "wordForms.future", "wordForms.past", "wordForms.imperative"), plans.map { it.title })
        val present = plans.first()
        assertEquals(listOf("jaz", "midva", "mi"), present.rows[0].second.map { it?.before })
        assertEquals(listOf("pres.3sg", "pres.3du", "pres.3pl"), present.rows[2].second.map { it?.key })
        val past = plans[2]
        assertEquals(listOf("on je", "onadva sta", "oni so"), past.rows[0].second.map { it?.before })
        assertEquals(listOf("past.n.sg", null, null), past.rows[2].second.map { it?.key })
        val imp = plans[3]
        assertEquals(listOf(null, "imp.1du", "imp.1pl"), imp.rows[1].second.map { it?.key })
        // the future: biti's future and the l-form, every gender's l-form right where the person may be either
        val fut = delati.slot("fut.1sg")!!
        assertTrue(fut.composed)
        assertEquals(listOf("bom delal", "bom delala"), fut.accept)
        assertEquals(listOf("bodo delali", "bojo delali"), delati.slot("fut.3pl")!!.accept)
        // biti: its own future (bom) and its negative present (nisem)
        assertEquals(listOf("wordForms.present", "wordForms.negative", "wordForms.future", "wordForms.past", "wordForms.imperative"), FormsCard.layout(biti).map { it.title })
        assertEquals("nisem", biti.slot("neg.1sg")!!.shown)
        // iti: grem, šel, as the forms book has them; only the parts it has
        assertEquals(listOf("wordForms.present", "wordForms.past"), FormsCard.layout(iti).map { it.title })
        assertEquals(listOf("gredo", "grejo"), iti.slot("pres.3pl")!!.accept)
        assertEquals("šel", iti.slot("past.m.sg")!!.shown)
    }

    @Test fun `an adjective's table - the nominative by gender and number, then its cases singular and plural, no accusative masculine where the dictionary has none`() {
        val plans = FormsCard.layout(dober)
        assertEquals(listOf("wordForms.case1", "wordForms.singular", "wordForms.plural"), plans.map { it.title })
        assertEquals(listOf("adj.m.sg", "adj.m.du", "adj.m.pl"), plans[0].rows[0].second.map { it?.key })
        assertEquals(listOf("adj.n.sg", null, "adj.n.pl"), plans[0].rows[2].second.map { it?.key })
        assertEquals(listOf("m", "f", "n"), plans[1].columns)
        val acc = plans[1].rows.first { it.first.startsWith("4.") }
        assertEquals(listOf(null, "adj.acc.f.sg", "adj.acc.n.sg"), acc.second.map { it?.key })
        val ex = exercise(dober)
        assertEquals(FormsCellState.GIVEN, cells(ex).first { it.key == "adj.m.sg" }.state)
        assertEquals("dobrega", cells(ex).first { it.key == "adj.gen.m.sg" }.form)
    }

    @Test fun `a word missing what a table needs gets no card - too few forms, an unknown part of speech, a slot without its page`() {
        assertNotNull(FormsCard.parse(json.encodeToJsonElement(FormsCardData.serializer(), hisa)))
        val few = hisa.copy(slots = hisa.slots.take(3))
        assertNull(FormsCard.parse(json.encodeToJsonElement(FormsCardData.serializer(), few)))
        assertNull(FormsCard.parse(json.encodeToJsonElement(FormsCardData.serializer(), hisa.copy(pos = "adv"))))
        assertNull(FormsCard.parse(json.encodeToJsonElement(FormsCardData.serializer(), hisa.copy(slots = hisa.slots.map { it.copy(page = "") }))))
        assertNull(FormsCard.parse(json.parseToJsonElement("""{"lemma": "x"}""")))
        assertNull(FormsCard.parse(null))
    }

    // --- what's introduced -------------------------------------------------------------------------------------------

    private val course = Course(
        "sl", listOf(CourseLevel("A1", listOf(UnitRef("velelnik", emptyMap()), UnitRef("tozilnik", emptyMap())))),
        mapOf(
            "velelnik" to CourseUnit("velelnik", "sl", "A1", "👉", mapOf("en" to "The imperative"), grammar = listOf("velelnik", "ti-vi")),
            "tozilnik" to CourseUnit("tozilnik", "sl", "A1", "🎯", mapOf("en" to "The accusative"), grammar = listOf("tozilnik", "mnozina")),
        ),
    )

    @Test fun `introduced - a page met in the grammar book, or taught by a unit whose intro is done (finished or skipped)`() {
        assertEquals(emptySet<String>(), FormsCard.introduced(null, course))
        val met = GameState(seed = 1, grammar = mapOf("sl/glagoli-sedanjik" to RuleRecord("2026-10-01"), "it/essere-avere" to RuleRecord("2026-10-01")))
        assertEquals(setOf("glagoli-sedanjik"), FormsCard.introduced(met, course))
        val begun = met.copy(course = CourseProgress(units = mapOf("sl/velelnik" to UnitProgress(intro = 2))))
        assertEquals(setOf("glagoli-sedanjik"), FormsCard.introduced(begun, course))
        val done = met.copy(course = CourseProgress(units = mapOf("sl/velelnik" to UnitProgress(intro = 5, introDone = true))))
        assertEquals(setOf("glagoli-sedanjik", "velelnik", "ti-vi"), FormsCard.introduced(done, course))
        // another language's course teaches nothing here
        assertEquals(setOf("glagoli-sedanjik"), FormsCard.introduced(done, course.copy(language = "it")))
    }

    @Test fun `a slot is a gap once its page and the pages it needs are introduced - the imperative's row after its unit, the dual's after the dual`() {
        val before = GameState(seed = 1, grammar = mapOf("sl/glagoli-sedanjik" to RuleRecord("2026-10-01"), "sl/pretekli-cas" to RuleRecord("2026-10-01")))
        val pages = FormsCard.introduced(before, course)
        val tested = FormsCard.testedKeys(delati, pages)
        assertEquals(listOf("pres.1sg", "pres.2sg", "pres.3sg", "pres.1pl", "pres.2pl", "pres.3pl"), tested.filter { it.startsWith("pres.") })
        assertTrue(tested.none { it.startsWith("imp.") || it.endsWith("du") || it.startsWith("fut.") })
        // the whole table shown: the imperative greyed, to read ahead
        val c = card("forms_delati", delati)
        val ex = FormsCard.exercise(c, delati, pages, false, today, Random(1), label = { it })!!
        assertTrue(cells(ex).filter { it.key.startsWith("imp.") }.all { it.state == FormsCellState.PREVIEW })
        assertEquals("delaj", cells(ex).first { it.key == "imp.2sg" }.form)
        // the unit velelnik's intro done: its row becomes gaps (the 2nd person's imp.2sg and imp.2pl; the dual's waits)
        val after = before.copy(course = CourseProgress(units = mapOf("sl/velelnik" to UnitProgress(intro = 5, introDone = true))))
        val now = FormsCard.testedKeys(delati, FormsCard.introduced(after, course))
        assertEquals(listOf("imp.2sg", "imp.2pl", "imp.1pl"), now.filter { it.startsWith("imp.") })
        val withDual = FormsCard.testedKeys(delati, FormsCard.introduced(after, course) + "dvojina")
        assertEquals(listOf("imp.2sg", "imp.2pl", "imp.1pl", "imp.1du", "imp.2du"), withDual.filter { it.startsWith("imp.") })
        // the word's own form never
        assertFalse("nom.sg" in FormsCard.testedKeys(hisa, everything))
        assertNull(FormsCard.exercise(card("forms_hisa", hisa), hisa, setOf("imenovalnik"), false, today, Random(1)))
    }

    // --- the parts -----------------------------------------------------------------------------------------------------

    @Test fun `a part a review - whole rows up to six gaps, in turn, a failed review asks its part again`() {
        val tested = FormsCard.testedKeys(hisa, everything).toSet()
        val rows = FormsCard.rows(hisa, tested)
        assertEquals(6, rows.size)
        assertEquals(listOf("nom.du", "nom.pl"), rows.first()) // the nominative's singular is the word
        val parts = FormsCard.parts(rows)
        assertTrue(parts.all { it.size <= FormsCard.PART_CELLS })
        assertEquals(rows.flatten(), parts.flatten())
        val first = FormsCard.part(card("c", hisa, reviews = 0), hisa, tested, today)
        val second = FormsCard.part(card("c", hisa, reviews = 1, q = 4), hisa, tested, today)
        assertEquals(parts[0] to false, first)
        assertEquals(parts[1] to false, second)
        // the second review failed: its part again
        assertEquals(parts[1], FormsCard.part(card("c", hisa, reviews = 2, q = 2), hisa, tested, today).first)
        assertEquals(parts[0], FormsCard.part(card("c", hisa, reviews = parts.size, q = 4), hisa, tested, today).first)
        // a verb's 30 forms: none asks more than six at once
        val verb = FormsCard.parts(FormsCard.rows(delati, FormsCard.testedKeys(delati, everything).toSet()))
        assertTrue(verb.size >= 5 && verb.all { it.size <= FormsCard.PART_CELLS })
    }

    @Test fun `a part newly introduced comes first while it is new - fresh rows, chosen even when the card is typed - then the turns again`() {
        val tested = FormsCard.testedKeys(delati, everything).toSet()
        val d = delati.copy(fresh = listOf("imp.2sg", "imp.2pl", "imp.1pl"), unlocked = "2026-10-17")
        val c = card("forms_delati", d, reps = 0, q = 4, reviews = 7)
        val (gaps, fresh) = FormsCard.part(c, d, tested, today)
        assertTrue(fresh)
        assertEquals(setOf("imp.2sg", "imp.2du", "imp.2pl", "imp.1du", "imp.1pl"), gaps.toSet())
        val ex = FormsCard.exercise(c.copy(repetitions = 1), d, everything, typed = true, today = today, random = Random(1), label = { it })!!
        assertTrue(ex.fresh)
        assertTrue(ex.gaps.filter { it.key in d.fresh }.all { it.chosen })
        assertTrue(ex.gaps.filter { it.key !in d.fresh }.none { it.chosen })
        // two good reviews since: the turns again; a month after the unlocking too
        assertFalse(FormsCard.part(c.copy(repetitions = 2), d, tested, today).second)
        assertFalse(FormsCard.part(c, d.copy(unlocked = "2026-09-01"), tested, today).second)
        // a fresh part not introduced on this phone (yet): the turns
        assertFalse(FormsCard.part(c, d, tested - d.fresh.toSet(), today).second)
    }

    // --- the choices ---------------------------------------------------------------------------------------------------

    @Test fun `three options a gap - the right one there, two of the word's other forms, never one that is right here`() {
        for (d in all) {
            val tested = FormsCard.testedKeys(d, everything).toSet()
            for (key in tested) {
                val slot = d.slot(key)!!
                val o = FormsCard.options(d, key, tested, Random(key.hashCode()))
                assertTrue("${d.lemma} $key: $o", slot.shown in o)
                assertEquals("${d.lemma} $key: $o", 3, o.size)
                val right = slot.accept.map(::norm).toSet()
                assertEquals("${d.lemma} $key: $o", 1, o.count { norm(it) in right })
                assertEquals("${d.lemma} $key: $o", 3, o.map(::norm).toSet().size)
            }
        }
        // the closest first: the same case in another number, the same number in another case
        val o = FormsCard.options(hisa, "acc.sg", FormsCard.testedKeys(hisa, everything).toSet(), Random(1)).toSet()
        assertEquals(setOf("hišo", "hiši", "hiše"), o)
        // a joined future's options are futures
        val f = FormsCard.options(delati, "fut.1pl", FormsCard.testedKeys(delati, everything).toSet(), Random(1))
        assertTrue(f.toString(), f.all { it.startsWith("bo") })
    }

    // --- an answer -----------------------------------------------------------------------------------------------------

    @Test fun `grading - chosen when it is a form right here, typed as a cloze (č forgotten almost, never the ending), the pronoun typed too`() {
        val ex = exercise(delati, typed = true)
        val g = ex.gaps.first { it.key == "pres.1sg" }.copy()
        assertEquals(Verdict.CORRECT, FormsCard.grade(g, "delam", typed = false).verdict)
        assertEquals(Verdict.WRONG, FormsCard.grade(g, "dela", typed = false).verdict)
        assertEquals(Verdict.CORRECT, FormsCard.grade(g, "jaz delam", typed = true).verdict)
        assertEquals(Verdict.WRONG, FormsCard.grade(g, "delaš", typed = true).verdict)
        val two = FormsGap("pres.2sg", "delaš", listOf("delaš"), emptyList(), chosen = false, page = "glagoli-sedanjik", where = "", before = "ti")
        assertEquals(Verdict.ALMOST, FormsCard.grade(two, "delas", typed = true).verdict)
        // the spoken bojo right as bodo is
        val fut = FormsGap("fut.3pl", "bodo delali", listOf("bodo delali", "bojo delali"), emptyList(), chosen = false, page = "prihodnjik", where = "", before = "oni")
        assertEquals(Verdict.CORRECT, FormsCard.grade(fut, "bojo delali", typed = true).verdict)
    }

    @Test fun `the kind of a wrong form - the case, the number, the person, the tense, the gender`() {
        assertEquals("number_plural", FormsCard.kind(hisa, "loc.pl", "hiši")) // the locative's singular for its plural
        assertEquals("case_accusative", FormsCard.kind(hisa, "acc.sg", "hiše"))
        assertEquals("number_dual", FormsCard.kind(hisa, "gen.du", "hiše"))
        assertEquals("case_locative", FormsCard.kind(hisa, "loc.du", "hišo"))
        assertEquals("case_genitive", FormsCard.kind(hisa, "gen.sg", "hišx"))
        assertEquals("verb_person_3pl", FormsCard.kind(delati, "pres.3pl", "dela"))
        assertEquals("number_dual", FormsCard.kind(delati, "pres.1du", "delamo"))
        assertEquals("tense_future", FormsCard.kind(delati, "fut.1sg", "bomo delali"))
        assertEquals("tense_past", FormsCard.kind(delati, "past.f.sg", "delal"))
        assertEquals("tense_imperative", FormsCard.kind(delati, "imp.2pl", "delaj"))
        assertEquals("tense_negative", FormsCard.kind(biti, "neg.1sg", "nisi"))
        assertEquals("adjective_gender", FormsCard.kind(dober, "adj.f.sg", "dobro"))
        assertEquals("number_plural", FormsCard.kind(dober, "adj.m.pl", "dober"))
        assertEquals("adjective_gender", FormsCard.kind(dober, "adj.gen.f.sg", "dobrega"))
        assertEquals("case_dative", FormsCard.kind(dober, "adj.dat.m.sg", "dobrem"))
        for (d in all) for (k in FormsCard.testedKeys(d, everything)) {
            val kind = FormsCard.kind(d, k, "xyz")
            assertTrue("${d.lemma} $k: $kind", kind in FormsCard.KINDS)
        }
    }

    @Test fun `a card's quality from its gaps, what it counts on the grammar book, and what the node hears`() {
        val c = Verdict.CORRECT
        val a = Verdict.ALMOST
        val w = Verdict.WRONG
        assertEquals(4, FormsCard.quality(listOf(c, c, c, c, c, c), typed = false))
        assertEquals(5, FormsCard.quality(listOf(c, c, c), typed = true))
        assertEquals(3, FormsCard.quality(listOf(c, a, c), typed = false))
        assertEquals(3, FormsCard.quality(listOf(c, c, c, c, c, w), typed = false))
        assertEquals(2, FormsCard.quality(listOf(c, c, c, w, w, w), typed = false))
        assertEquals(1, FormsCard.quality(listOf(c, w, w, w, w, w), typed = false))
        assertEquals(1, FormsCard.quality(emptyList(), typed = false))

        val ex = exercise(hisa)
        val marks = ex.gaps.mapIndexed { i, g ->
            if (i == 0) ItemMark(Verdict.WRONG, "hišo", g.answer) else ItemMark(Verdict.CORRECT, g.answer, g.answer)
        }
        val rules = FormsCard.ruleAnswers(ex, marks)
        assertEquals(ex.gaps.map { it.page }.distinct(), rules.map { it.rule })
        assertEquals(Verdict.WRONG, rules.first { it.rule == ex.gaps[0].page }.verdict)
        assertEquals("hišo", rules.first { it.rule == ex.gaps[0].page }.answer)
        val wire = FormsCard.wire("forms_hisa", ex, marks)
        assertEquals("forms_hisa", wire["item_id"]!!.jsonPrimitive.content)
        val cells = wire["cells"]!!.jsonArray.map { it.jsonObject }
        assertEquals(ex.gaps.map { it.key }, cells.map { it["key"]!!.jsonPrimitive.content })
        assertEquals("false", cells[0]["right"]!!.jsonPrimitive.content)
        assertTrue(cells[0]["kind"]!!.jsonPrimitive.content in FormsCard.KINDS)
        assertTrue(cells.drop(1).none { "kind" in it })
        assertFalse(FormsCard.typed(ex, marks))
    }

    // --- which words, how many ---------------------------------------------------------------------------------------

    private fun entries(pos: String, n: Int) = listOf(LemmaForms("x", pos, slots = (1..n).map { FormSlot("k$it", listOf("f$it"), page = "p") }))

    @Test fun `which words - the learner's learned ones whose forms the phone has, the core verbs first, not those with a card, a few a day`() {
        val pool = listOf(
            ReviewCard("v_hisa", "hiša", "house", repetitions = 4), ReviewCard("v_delati", "delati", "to work", repetitions = 1),
            ReviewCard("v_biti", "biti", "to be", repetitions = 1), ReviewCard("v_lep", "lep", "nice", repetitions = 3),
            ReviewCard("v_hvala", "hvala", "thanks", repetitions = 9), ReviewCard("v_new", "miza", "table"),
            ReviewCard("v_kava", "kava", "coffee", repetitions = 5),
        )
        val learned = setOf("v_hisa", "v_delati", "v_biti", "v_lep", "v_hvala", "v_kava")
        val have = listOf(card("forms_kava", hisa.copy(lemma = "kava")))
        val forms = mapOf("hiša" to entries("noun", 18), "delati" to entries("verb", 21), "biti" to entries("verb", 40), "lep" to entries("adj", 6), "hvala" to emptyList(), "miza" to entries("noun", 18), "kava" to entries("noun", 18))
        val want = FormsCard.candidates(pool, learned, have, { forms[it] })
        assertEquals(listOf("biti", "delati", "hiša", "lep"), want.map { it.first })
        assertEquals("v_biti", want.first().second)
        assertEquals(listOf("delati", "hiša", "lep"), FormsCard.candidates(pool, learned, have, { forms[it] }, refused = setOf("biti")).map { it.first })
        // two new a day of their own accord; one made on demand doesn't count
        val made = listOf(card("a", hisa, created = today), card("b", delati.copy(asked = true), created = today))
        assertEquals(1, FormsCard.room(made, today))
        assertEquals(0, FormsCard.room(made + card("c", biti, created = today), today))
        assertEquals(2, FormsCard.room(made.map { it.copy(created = today.minusDays(1)) }, today))
        assertEquals("forms_kava", FormsCard.cardOf(have, "Kava")?.id)
    }

    // --- the node's item, and the planner ----------------------------------------------------------------------------

    @Test fun `the dashboard - a forms card with its table, out of the pool, three at most among the due cards, one without its table none`() {
        val table = json.encodeToString(FormsCardData.serializer(), hisa)
        val items = (1..5).joinToString(",\n") { i ->
            """"forms_$i": {"type": "forms", "content": "hiša", "answer": "hiše · hiši", "created_date": "2026-10-18", "total_reviews": $i, "forms": $table}"""
        }
        val d = Dashboard.parse("""
            {"databases": {"spaced_repetition": {"items": {
              "vocab_hisa": {"type": "vocabulary", "content": "hiša", "answer": "house", "repetitions": 2, "last_quality": 4},
              $items,
              "forms_bare": {"type": "forms", "content": "pes", "answer": "psa"}
            }}},
             "computed": {"today": "2026-10-18", "due_review_items": ["vocab_hisa", "forms_1", "forms_2", "forms_3", "forms_4", "forms_5", "forms_bare"]}}
        """.trimIndent())
        assertEquals(listOf("vocab_hisa"), d.pool.map { it.id })
        assertEquals(5, d.formsCards.size)
        assertEquals(1 + FormsCard.PER_REVIEW, d.dueCards.size)
        assertEquals(3, d.formsCards.first { it.id == "forms_3" }.reviews)
        assertEquals("🏠", d.formsCards.first().forms?.emoji)
        assertEquals(1, d.wordsTracked) // a forms card is no word
    }

    @Test fun `the planner - a forms card's table, chosen while new, typed once secure, none when nothing of it is asked yet`() {
        val fresh = card("forms_hisa", hisa)
        val secure = card("forms_delati", delati, reps = 2, q = 4)
        val words = listOf(ReviewCard("a", "hvala", "thanks"), ReviewCard("b", "prosim", "please"))
        val build = { c: ReviewCard -> FormsCard.exercise(c, c.forms!!, if (c.id == "forms_hisa") everything else setOf("glagoli-sedanjik"), FormsCard.secure(c), today, Random(1), label = { it }) }
        val tasks = ReviewPlanner.plan(listOf(fresh, secure), words + fresh, canSpeak = true, random = Random(2), formsCard = build)
        assertEquals(listOf(Variant.FORMS_CARD, Variant.FORMS_CARD_TYPE), tasks.map { it.variant })
        assertFalse((tasks[0].exercise as Exercise.FormsTable).typed)
        assertTrue((tasks[1].exercise as Exercise.FormsTable).typed)
        // nothing of it asked yet, or no builder: left out of the review, never a flashcard of its back
        val none = ReviewPlanner.plan(listOf(card("forms_x", hisa)), words, canSpeak = true, formsCard = { c -> FormsCard.exercise(c, c.forms!!, setOf("imenovalnik"), false, today, Random(1)) })
        assertTrue(none.isEmpty())
        assertTrue(ReviewPlanner.plan(listOf(fresh), words, canSpeak = true).isEmpty())
    }
}
