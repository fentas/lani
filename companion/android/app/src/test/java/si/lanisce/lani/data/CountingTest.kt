package si.lanisce.lani.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.data.ReviewPlanner.Variant
import si.lanisce.lani.game.CourseProgress
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.UnitProgress
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair
import java.time.LocalDate
import kotlin.random.Random

/**
 * Counting cards (data/Counting.kt): a card's steps from the forms the node kept (the dictionary's: m, f, n, the irregular
 * ones, several forms of a slot), the choices (never one that is right, the right numeral in the right one), a step's
 * grading and the kind of a wrong one, the card's quality and what it counts on the grammar book, which nouns are asked
 * for and how many a day, how they join the review, and when counting is on.
 */
class CountingTest {
    private val pair = L10n.pair

    @Before fun pair() {
        L10n.pair = LangPair(Lang.SL, Lang.EN)
    }

    @After fun back() {
        L10n.pair = pair
    }

    private fun forms(lemma: String, gender: String, lines: List<String>, slots: Map<String, List<String>>, others: List<String> = emptyList()) =
        CountingForms(lemma, gender, slots, lines, others)

    // as the node makes them (companion/bridge/src/counting.ts), from Wiktionary's forms
    private val kava = forms("kava", "f", listOf("ena kava", "dve kavi", "tri kave", "pet kav"),
        mapOf("nom.sg" to listOf("kava"), "nom.du" to listOf("kavi"), "nom.pl" to listOf("kave"), "gen.pl" to listOf("kav")), listOf("kavo", "kavam", "kavah", "kavami"))
    private val sin = forms("sin", "m", listOf("en sin", "dva sinova", "trije sinovi", "pet sinov"),
        mapOf("nom.sg" to listOf("sin"), "nom.du" to listOf("sinova"), "nom.pl" to listOf("sinovi"), "gen.pl" to listOf("sinov")), listOf("sina", "sinu"))
    private val jajce = forms("jajce", "n", listOf("eno jajce", "dve jajci", "tri jajca", "pet jajc"),
        mapOf("nom.sg" to listOf("jajce"), "nom.du" to listOf("jajci"), "nom.pl" to listOf("jajca"), "gen.pl" to listOf("jajc")), listOf("jajcu", "jajcem"))
    private val oce = forms("oče", "m", listOf("en oče", "dva očeta", "trije očeti", "pet očetov"),
        mapOf("nom.sg" to listOf("oče"), "nom.du" to listOf("očeta"), "nom.pl" to listOf("očeti", "očetje"), "gen.pl" to listOf("očetov")), listOf("očetu"))
    private val dan = forms("dan", "m", listOf("en dan", "dva dneva", "trije dnevi", "pet dni"),
        mapOf("nom.sg" to listOf("dan"), "nom.du" to listOf("dneva"), "nom.pl" to listOf("dnevi"), "gen.pl" to listOf("dnevov", "dni")), listOf("dne", "dnevu"))
    private val pesem = forms("pesem", "f", listOf("ena pesem", "dve pesmi", "tri pesmi", "pet pesmi"),
        mapOf("nom.sg" to listOf("pesem"), "nom.du" to listOf("pesmi"), "nom.pl" to listOf("pesmi"), "gen.pl" to listOf("pesmi")), listOf("pesmijo", "pesmim", "pesmih", "pesmimi"))
    // Wiktionary's dual -e (ovce) and genitive plural ovac: right, never shown, never a wrong choice
    private val ovca = forms("ovca", "f", listOf("ena ovca", "dve ovci", "tri ovce", "pet ovc"),
        mapOf("nom.sg" to listOf("ovca"), "nom.du" to listOf("ovce", "ovci"), "nom.pl" to listOf("ovce"), "gen.pl" to listOf("ovac", "ovc")), listOf("ovco", "ovcam"))
    private val noc = forms("noč", "f", listOf("ena noč", "dve noči", "tri noči", "pet noči"),
        mapOf("nom.sg" to listOf("noč"), "nom.du" to listOf("noči"), "nom.pl" to listOf("noči"), "gen.pl" to listOf("noči")), listOf("nočjo", "nočem", "nočeh", "nočmi"))
    private val all = listOf(kava, sin, jajce, oce, dan, pesem, ovca, noc)

    private fun card(id: String, f: CountingForms, reps: Int = 0, q: Int = 3, created: LocalDate? = null) =
        ReviewCard(id, f.lemma, f.lines.joinToString(" · "), Counting.KIND, reps, q, created = created, counting = f)

    private fun norm(s: String) = Grading.normalize(s, Lang.SL)

    // --- the card: its steps and choices ------------------------------------------------------------------------------

    @Test fun `four steps 1, 2, 3, 5 - the numeral of the noun's gender for 1, the whole phrase after, every dictionary form right`() {
        val ex = Counting.exercise(kava, typed = false, why = null, random = Random(1))
        assertEquals(listOf(1, 2, 3, 5), ex.steps.map { it.n })
        assertEquals(listOf("ena", "dve kavi", "tri kave", "pet kav"), ex.steps.map { it.answer })
        assertEquals("ena kava, dve kavi, tri kave, pet kav", ex.said)
        assertEquals(listOf(Counting.PAGE, Counting.GENDER_PAGE), ex.pages)
        assertEquals(Counting.PAGE, ex.grammar)
        // the irregular ones as the dictionary has them, each of a slot's forms right
        assertEquals(listOf("trije očeti", "trije očetje"), Counting.accept(oce, 2))
        assertEquals(listOf("pet dni", "pet dnevov"), Counting.accept(dan, 3))
        assertEquals(listOf("dve ovci", "dve ovce"), Counting.accept(ovca, 1))
        // step 1: the numeral alone, or with its noun
        assertEquals(listOf("en", "en oče"), Counting.accept(oce, 0))
    }

    @Test fun `the numerals agree - en dva trije, ena dve tri, eno dve tri - in every right line and right option`() {
        for (f in all) {
            val ex = Counting.exercise(f, typed = false, why = null, random = Random(7))
            val n = Counting.NUMERALS.getValue(f.gender)
            ex.steps.forEachIndexed { i, s ->
                assertEquals(f.lemma, n[i], s.numeral)
                assertTrue("${f.lemma} ${s.line}", s.line.startsWith("${n[i]} "))
                assertTrue("${f.lemma} ${s.options}", s.answer in s.options)
            }
        }
        assertEquals(listOf("en", "dva", "trije", "pet"), Counting.NUMERALS["m"])
        assertEquals(listOf("ena", "dve", "tri", "pet"), Counting.NUMERALS["f"])
        assertEquals(listOf("eno", "dve", "tri", "pet"), Counting.NUMERALS["n"])
    }

    @Test fun `the wrong choices - the other number's ending and the other numeral, never one the dictionary accepts`() {
        assertEquals(listOf("dve kave", "dva kava"), Counting.distractors(kava, 1))
        assertEquals(listOf("tri kavi", "trije kave"), Counting.distractors(kava, 2))
        assertEquals(listOf("pet kave", "pet kava"), Counting.distractors(kava, 3))
        assertEquals(listOf("dva sinovi", "dve sin"), Counting.distractors(sin, 1))
        assertEquals(listOf("trije sinova", "tri sinovi"), Counting.distractors(sin, 2))
        // ovce is a dual of ovca too (Wiktionary's): dve ovce is no wrong choice
        assertEquals(listOf("dva ovca", "dva ovci"), Counting.distractors(ovca, 1))
        for (f in all) for (i in 1..3) {
            val wrong = Counting.distractors(f, i)
            val right = Counting.accept(f, i).map(::norm).toSet()
            assertTrue("${f.lemma} $i: $wrong", wrong.isNotEmpty() && wrong.none { norm(it) in right })
            assertEquals("${f.lemma} $i distinct", wrong.size, wrong.map(::norm).toSet().size)
            assertTrue("${f.lemma} $i a phrase", wrong.all { it.trim().split(' ').size == 2 })
        }
        // pesem is pesmi in the dual, the plural and the genitive plural: the other cases make the wrong choices
        assertEquals(listOf("pet pesem", "pet pesmijo"), Counting.distractors(pesem, 3))
        assertEquals(listOf("trije pesmi", "tri pesem"), Counting.distractors(pesem, 2))
    }

    @Test fun `step 1 offers en, ena, eno in that order, the others three phrases shuffled the same way for the same seed`() {
        val a = Counting.exercise(jajce, typed = false, why = null, random = Random(3))
        val b = Counting.exercise(jajce, typed = false, why = null, random = Random(3))
        assertEquals(listOf("en", "ena", "eno"), a.steps[0].options)
        assertEquals(a.steps.map { it.options }, b.steps.map { it.options })
        assertTrue(a.steps.drop(1).all { it.options.size == 3 })
        // a typed card keeps its options for "Let me choose"
        assertEquals(3, Counting.exercise(jajce, typed = true, why = null, random = Random(3)).steps[2].options.size)
    }

    // --- an answer -------------------------------------------------------------------------------------------------

    @Test fun `a step graded - chosen right or not, typed as a cloze (č forgotten almost, never the ending), step 1 by its numeral`() {
        val k = Counting.exercise(kava, typed = true, why = null, random = Random(1)).steps
        assertEquals(Verdict.CORRECT, Counting.grade(k[1], "dve kavi", typed = false).verdict)
        assertEquals(Verdict.WRONG, Counting.grade(k[1], "dve kave", typed = false).verdict)
        assertEquals(Verdict.CORRECT, Counting.grade(k[1], " Dve kavi ", typed = true).verdict)
        assertEquals(Verdict.WRONG, Counting.grade(k[1], "dve kave", typed = true).verdict)
        assertEquals(Verdict.WRONG, Counting.grade(k[1], "dva kavi", typed = true).verdict)
        assertEquals(Verdict.CORRECT, Counting.grade(k[0], "ena", typed = true).verdict)
        assertEquals(Verdict.CORRECT, Counting.grade(k[0], "ena kava", typed = true).verdict)
        assertEquals(Verdict.WRONG, Counting.grade(k[0], "eno", typed = true).verdict)
        val n = Counting.exercise(noc, typed = true, why = null, random = Random(1)).steps
        assertEquals(Verdict.ALMOST, Counting.grade(n[1], "dve noci", typed = true).verdict)
        val o = Counting.exercise(oce, typed = true, why = null, random = Random(1)).steps
        assertEquals(Verdict.CORRECT, Counting.grade(o[2], "trije očetje", typed = true).verdict)
        // the mark keeps what was answered, and the right line
        assertEquals("dve kave" to "dve kavi", Counting.grade(k[1], "dve kave", typed = false).let { it.answer to it.expected })
    }

    @Test fun `a wrong step's kind - the gender for 1 and for a wrong numeral alone, else the dual, the plural, the genitive plural`() {
        val s = Counting.exercise(kava, typed = false, why = null, random = Random(1)).steps
        assertEquals(CountKind.GENDER, Counting.kind(s[0], "eno"))
        assertEquals(CountKind.GENDER, Counting.kind(s[1], "dva kavi"))
        assertEquals(CountKind.DUAL, Counting.kind(s[1], "dve kave"))
        assertEquals(CountKind.DUAL, Counting.kind(s[1], "dva kava"))
        assertEquals(CountKind.GENDER, Counting.kind(s[2], "trije kave"))
        assertEquals(CountKind.PLURAL, Counting.kind(s[2], "tri kavi"))
        assertEquals(CountKind.GENITIVE_PLURAL, Counting.kind(s[3], "pet kave"))
        // a č forgotten isn't what it was wrong about: dva noci is the numeral's
        val n = Counting.exercise(noc, typed = true, why = null, random = Random(1)).steps
        assertEquals(CountKind.GENDER, Counting.kind(n[1], "dva noci"))
        assertEquals(CountKind.DUAL, Counting.kind(n[1], "dve noc"))
    }

    @Test fun `the card's quality from its steps - all right 4 (typed 5), one wrong 3, two 2, more 1`() {
        val c = Verdict.CORRECT
        val w = Verdict.WRONG
        assertEquals(4, Counting.quality(listOf(c, c, c, c), typed = false))
        assertEquals(5, Counting.quality(listOf(c, c, c, c), typed = true))
        assertEquals(4, Counting.quality(listOf(c, Verdict.ALMOST, c, c), typed = true))
        assertEquals(3, Counting.quality(listOf(c, w, c, c), typed = false))
        assertEquals(2, Counting.quality(listOf(c, w, w, c), typed = true))
        assertEquals(1, Counting.quality(listOf(w, w, w, c), typed = false))
        // one wrong and a č forgotten: not quite, as its verdict says
        assertEquals(2, Counting.quality(listOf(c, w, Verdict.ALMOST, c), typed = true))
        assertEquals(Verdict.WRONG, Counting.verdict(listOf(c, w, Verdict.ALMOST, c).map { ItemMark(it, "", "") }))
        assertEquals(Verdict.CORRECT, Counting.verdict(listOf(c, c, c, c).map { ItemMark(it, "", "") }))
        assertEquals(Verdict.ALMOST, Counting.verdict(listOf(c, w, c, c).map { ItemMark(it, "", "") }))
        assertEquals(Verdict.WRONG, Counting.verdict(listOf(c, w, w, c).map { ItemMark(it, "", "") }))
    }

    @Test fun `what the steps count on the grammar book - the counting rule, the gender, the dual, the plural`() {
        val ex = Counting.exercise(kava, typed = false, why = null, random = Random(1))
        fun marks(vararg a: String) = ex.steps.zip(a.toList()).map { (s, x) -> Counting.grade(s, x, typed = false) }
        val right = Counting.ruleAnswers(ex, marks("ena", "dve kavi", "tri kave", "pet kav")).associateBy { it.rule }
        assertEquals(setOf(Counting.PAGE, Counting.GENDER_PAGE, Counting.DUAL_PAGE, Counting.PLURAL_PAGE), right.keys)
        assertTrue(right.values.all { it.verdict == Verdict.CORRECT })
        assertEquals("ena kava, dve kavi, tri kave, pet kav", right.getValue(Counting.PAGE).said)
        // dva kavi: the numeral's agreement, the gender's slip; the dual's form was right, so no answer on the dual
        val agreement = Counting.ruleAnswers(ex, marks("ena", "dva kavi", "tri kave", "pet kav")).associateBy { it.rule }
        assertEquals(Verdict.WRONG, agreement.getValue(Counting.PAGE).verdict)
        assertEquals("dva kavi" to "dve kavi", agreement.getValue(Counting.GENDER_PAGE).let { it.answer to it.correct })
        assertNull(agreement[Counting.DUAL_PAGE])
        assertEquals(Verdict.CORRECT, agreement.getValue(Counting.PLURAL_PAGE).verdict)
        // dve kave: the dual's slip; the gender right
        val dual = Counting.ruleAnswers(ex, marks("ena", "dve kave", "tri kave", "pet kav")).associateBy { it.rule }
        assertEquals(Verdict.WRONG, dual.getValue(Counting.DUAL_PAGE).verdict)
        assertEquals(Verdict.CORRECT, dual.getValue(Counting.GENDER_PAGE).verdict)
    }

    @Test fun `how a card went, for the node - each step right or not, a wrong one's kind, typed only when every step was`() {
        val ex = Counting.exercise(sin, typed = true, why = null, random = Random(1))
        val marks = listOf(
            Counting.grade(ex.steps[0], "en", typed = true), Counting.grade(ex.steps[1], "dve sinova", typed = true),
            Counting.grade(ex.steps[2], "trije sinovi", typed = true), Counting.grade(ex.steps[3], "pet sinov", typed = true),
        )
        val w = Counting.wire("counting_sin", ex, marks)
        val steps = w["steps"]!!.jsonArray.map { it.jsonObject }
        assertEquals("counting_sin", w["item_id"]!!.jsonPrimitive.content)
        assertEquals("true", w["typed"]!!.jsonPrimitive.content)
        assertEquals(listOf("true", "false", "true", "true"), steps.map { it["right"]!!.jsonPrimitive.content })
        assertEquals("gender", steps[1]["kind"]!!.jsonPrimitive.content)
        assertEquals("dva sinova", steps[1]["expected"]!!.jsonPrimitive.content)
        assertFalse((steps[0] as JsonObject).containsKey("kind"))
        // a step chosen after "Let me choose": the card counts as chosen
        val chosen = marks.mapIndexed { i, m -> if (i == 3) m.copy(hinted = true) else m }
        assertFalse(Counting.typed(ex, chosen))
        assertEquals("false", Counting.wire("counting_sin", ex, chosen)["typed"]!!.jsonPrimitive.content)
    }

    @Test fun `step 1's why - the noun's end and its real gender, the exception said`() {
        assertEquals("counting.whyVowel", Counting.why("kava", "f", "ena kava").first)
        assertEquals("counting.whyConsonant", Counting.why("kruh", "m", "en kruh").first)
        assertEquals("counting.whyVowel", Counting.why("jajce", "n", "eno jajce").first)
        assertEquals("counting.whyVowelOdd", Counting.why("oče", "m", "en oče").first)
        assertEquals("counting.whyConsonantOdd", Counting.why("vas", "f", "ena vas").first)
        assertEquals("counting.whyConsonantOdd", Counting.why("noč", "f", "ena noč").first)
        assertEquals("counting.whyPlain", Counting.why("hči", "f", "ena hči").first)
        val (key, args) = Counting.why("kava", "f", "ena kava")
        assertEquals("kava ends in -a: feminine (ženski spol) → ena kava", L10n.text(Lang.EN, key, args))
        assertEquals("kava se konča na -a: ženski spol → ena kava", L10n.text(Lang.SL, key, args))
        val (odd, oArgs) = Counting.why("oče", "m", "en oče")
        assertEquals("oče ends in -e, but it is masculine (moški spol): an exception → en oče", L10n.text(Lang.EN, odd, oArgs))
        assertEquals("oče se konča na -e, a je moškega spola: izjema → en oče", L10n.text(Lang.SL, odd, oArgs))
        // the planner gives it in the learner's base
        val vas = forms("vas", "f", listOf("ena vas", "dve vasi", "tri vasi", "pet vasi"),
            mapOf("nom.sg" to listOf("vas"), "nom.du" to listOf("vasi"), "nom.pl" to listOf("vasi"), "gen.pl" to listOf("vasi")))
        val task = ReviewPlanner.countingTask(card("counting_vas", vas), vas, typed = false)
        assertEquals("vas ends in a consonant, but it is feminine (ženski spol): an exception → ena vas", (task.exercise as Exercise.Counting).why)
    }

    // --- which cards, when --------------------------------------------------------------------------------------------

    @Test fun `on once the intro of the unit stevila is done, finished or skipped, off before and without a village`() {
        val begun = GameState(seed = 1, course = CourseProgress(units = mapOf("sl/stevila" to UnitProgress(intro = 3))))
        val done = GameState(seed = 1, course = CourseProgress(units = mapOf("sl/stevila" to UnitProgress(intro = 6, introDone = true))))
        val other = GameState(seed = 1, course = CourseProgress(units = mapOf("sl/biti" to UnitProgress(introDone = true))))
        assertFalse(Counting.active(null))
        assertFalse(Counting.active(GameState(seed = 1)))
        assertFalse(Counting.active(begun))
        assertFalse(Counting.active(other))
        assertTrue(Counting.active(done))
    }

    private fun noun(lemma: String, gender: String?, vararg missing: String) = LemmaForms(
        lemma, "noun", gender = gender,
        slots = Counting.SLOTS.filter { it !in missing }.map { FormSlot(it, listOf("$lemma-$it"), page = "p") },
    )

    @Test fun `the nouns asked for - learned one-word words the dictionary counts, without a card, not refused, best known first`() {
        val pool = listOf(
            ReviewCard("vocab_kava", "kava", "coffee", repetitions = 2), ReviewCard("vocab_sin", "sin", "son", repetitions = 5),
            ReviewCard("vocab_mleko", "mleko", "milk", repetitions = 3), ReviewCard("vocab_hisa", "hiša", "house", repetitions = 1),
            ReviewCard("vocab_dan", "dober dan", "good day", repetitions = 4), ReviewCard("vocab_pes", "pes", "dog", repetitions = 4),
            ReviewCard("vocab_okno", "okno", "window", repetitions = 4), ReviewCard("vocab_vas", "vas", "village", repetitions = 4, lookup = true),
            ReviewCard("vocab_jesti", "jesti", "to eat", repetitions = 4),
        )
        val entries = mapOf(
            "kava" to listOf(noun("kava", "f")), "sin" to listOf(noun("sin", "m")), "mleko" to listOf(noun("mleko", "n", "nom.du", "nom.pl", "gen.pl")),
            "hiša" to listOf(noun("hiša", null)), "pes" to listOf(noun("pes", "m")), "okno" to listOf(noun("okno", "n")), "vas" to listOf(noun("vas", "f")),
            "jesti" to listOf(LemmaForms("jesti", "verb", slots = listOf(FormSlot("pres.1sg", listOf("jem"), page = "p")))),
        )
        val learned = pool.map { it.id }.toSet() - "vocab_okno"
        val have = listOf(card("counting_pes", forms("pes", "m", listOf("en pes", "dva psa", "trije psi", "pet psov"), emptyMap())))
        val want = Counting.candidates(pool, learned, have, { entries[it] }, refused = setOf("nobody"))
        // sin (5 good reviews) before kava (2); not mleko (no plural), hiša (no gender), a phrase, pes (its card), okno (not
        // learned), vas (a lookup), jesti (a verb)
        assertEquals(listOf("sin" to "vocab_sin", "kava" to "vocab_kava"), want)
        assertEquals(listOf("kava" to "vocab_kava"), Counting.candidates(pool, learned, have, { entries[it] }, refused = setOf("sin")))
        // the phone hasn't the forms yet: not asked
        assertEquals(emptyList<Pair<String, String>>(), Counting.candidates(pool, learned, have, { null }))
        val many = (1..30).map { ReviewCard("vocab_w$it", "beseda$it".filter(Char::isLetter) + "x".repeat(it), "w", repetitions = it) }
        assertEquals(Counting.ASK_MAX, Counting.candidates(many, many.map { it.id }.toSet(), emptyList(), { listOf(noun(it, "f")) }).size)
    }

    @Test fun `a few new a day - three, less those made today`() {
        val today = LocalDate.of(2026, 10, 15)
        val made = listOf(card("a", kava, created = today), card("b", sin, created = today), card("c", jajce, created = today.minusDays(1)))
        assertEquals(1, Counting.room(made, today))
        assertEquals(Counting.NEW_PER_DAY, Counting.room(made, today.plusDays(1)))
        assertEquals(0, Counting.room(made + card("d", oce, created = today), today))
        assertEquals(0, Counting.room(made + card("d", oce, created = today) + card("e", dan, created = today), today))
    }

    @Test fun `in the review - a few counting cards spread among the others, twenty in all`() {
        val words = (1..30).map { ReviewCard("w$it", "beseda$it", "word $it") }
        val counting = (1..6).map { card("counting_$it", kava) }
        val mixed = Counting.interleave(words, counting)
        assertEquals(20, mixed.size)
        assertEquals(Counting.PER_REVIEW, mixed.count { it.kind == Counting.KIND })
        val at = mixed.withIndex().filter { it.value.kind == Counting.KIND }.map { it.index }
        assertEquals(listOf(3, 7, 12, 16), at) // spread, never all at the end
        assertEquals(words.take(16), mixed.filter { it.kind != Counting.KIND })
        assertEquals(counting.take(4), Counting.interleave(emptyList(), counting))
        assertEquals(words.take(20), Counting.interleave(words, emptyList()))
        assertEquals(listOf("w1", "counting_1", "w2"), Counting.interleave(words.take(2), counting.take(1)).map { it.id })
    }

    // --- the node's item, and the planner ----------------------------------------------------------------------------

    @Test fun `the dashboard - a counting card with its forms, out of the pool, among the due cards, one without its forms none`() {
        val d = Dashboard.parse("""
            {"databases": {"spaced_repetition": {"items": {
              "vocab_kava": {"type": "vocabulary", "content": "kava", "answer": "coffee", "repetitions": 2, "last_quality": 4},
              "counting_kava": {"type": "counting", "content": "kava", "answer": "ena kava · dve kavi · tri kave · pet kav", "created_date": "2026-10-15",
                "counting": {"v": 1, "lemma": "kava", "gender": "f", "lines": ["ena kava", "dve kavi", "tri kave", "pet kav"], "emoji": "☕",
                  "forms": {"nom.sg": ["kava"], "nom.du": ["kavi"], "nom.pl": ["kave"], "gen.pl": ["kav"]}, "others": ["kavo"], "table": "wiktionary"}},
              "counting_bare": {"type": "counting", "content": "pes", "answer": "en pes"},
              "counting_half": {"type": "counting", "content": "sin", "answer": "en sin", "counting": {"lemma": "sin", "gender": "m", "lines": ["en sin"], "forms": {}}}
            }}},
             "computed": {"today": "2026-10-15", "due_review_items": ["vocab_kava", "counting_kava", "counting_bare", "counting_half"]}}
        """.trimIndent())
        assertEquals(listOf("vocab_kava"), d.pool.map { it.id })
        assertEquals(listOf("counting_kava"), d.counting.map { it.id })
        assertEquals(listOf("vocab_kava", "counting_kava"), d.dueCards.map { it.id })
        val c = d.counting.single()
        assertEquals(LocalDate.of(2026, 10, 15), c.created)
        assertEquals("☕", c.counting?.emoji)
        assertEquals(listOf("kavi"), c.counting?.formsOf(1))
        assertEquals(1, d.wordsTracked) // a counting card is no word
    }

    @Test fun `the planner - a counting card's exercise, chosen while new, typed once secure, its choices never other cards`() {
        val fresh = card("counting_kava", kava)
        val secure = card("counting_sin", sin, reps = 2, q = 4)
        val slipped = card("counting_oce", oce, reps = 3, q = 3)
        val words = listOf(ReviewCard("a", "hvala", "thanks"), ReviewCard("b", "prosim", "please"))
        val tasks = ReviewPlanner.plan(listOf(fresh, secure, slipped), words + fresh, canSpeak = true, random = Random(2))
        assertEquals(listOf(Variant.COUNTING, Variant.COUNTING_TYPE, Variant.COUNTING), tasks.map { it.variant })
        assertTrue(tasks.all { it.exercise is Exercise.Counting })
        assertTrue((tasks[1].exercise as Exercise.Counting).typed)
        assertFalse((tasks[0].exercise as Exercise.Counting).typed)
        // QA's hook: all typed
        assertTrue(ReviewPlanner.plan(listOf(fresh), words, canSpeak = true, typed = { true }).single().let { it.variant == Variant.COUNTING_TYPE })
        // a word's card never gets a counting card's back as its choice
        repeat(20) {
            val ex = ReviewPlanner.plan(listOf(ReviewCard("c", "dober dan", "good day")), words + fresh, canSpeak = true).single().exercise
            assertFalse(ex.toString(), "dve kavi" in ex.toString())
        }
    }

    @Test fun `the review's write carries how the counting cards went`() {
        val e = Writes.reviews(listOf("counting_kava" to 4), 2, counting = kotlinx.serialization.json.JsonArray(listOf(
            Counting.wire("counting_kava", Counting.exercise(kava, false, null, Random(1)), listOf(ItemMark(Verdict.CORRECT, "ena", "ena kava"))),
        )))
        val body = json.parseToJsonElement(e.body).jsonObject
        assertEquals("counting_kava", body["counting"]!!.jsonArray.single().jsonObject["item_id"]!!.jsonPrimitive.content)
        assertFalse(json.parseToJsonElement(Writes.reviews(listOf("a" to 4), 2).body).jsonObject.containsKey("counting"))
    }
}
