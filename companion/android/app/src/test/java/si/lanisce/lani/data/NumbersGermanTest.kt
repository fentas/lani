package si.lanisce.lani.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import si.lanisce.lani.data.Grading.Verdict
import si.lanisce.lani.game.Age
import si.lanisce.lani.game.CourseProgress
import si.lanisce.lani.game.GameState
import si.lanisce.lani.game.UnitProgress
import si.lanisce.lani.l10n.L10n
import si.lanisce.lani.l10n.Lang
import si.lanisce.lani.l10n.LangPair

/**
 * The numbers module in German (data/NumberTongue.kt, SpokenNumbers.german), for a learner from English: the words against a
 * hand-checked table (the course's page zahlen: hundert, tausend without ein at the start), every number read back with its other
 * forms, when it opens (the unit zahlen), the ways of asking (uhrzeit brings the clock, am-ersten-mai's ordinalzahlen the dates),
 * and the grading: one word, the ones first against English's and Italian's tens first, eins and sechs kept whole as the
 * learner's own words, a date's ordinal (-te, -ste, the four special ones, Jänner and Januar), halb towards the next hour.
 */
class NumbersGermanTest {
    private val pair = L10n.pair
    private val de = NumberTongue.DE

    @Before fun pair() {
        L10n.pair = LangPair(Lang.DE, Lang.EN)
    }

    @After fun back() {
        L10n.pair = pair
    }

    private val table = mapOf(
        0 to "null", 1 to "eins", 2 to "zwei", 6 to "sechs", 7 to "sieben", 10 to "zehn", 11 to "elf", 12 to "zwölf", 13 to "dreizehn", 16 to "sechzehn",
        17 to "siebzehn", 19 to "neunzehn", 20 to "zwanzig", 21 to "einundzwanzig", 27 to "siebenundzwanzig", 30 to "dreißig", 31 to "einunddreißig",
        60 to "sechzig", 66 to "sechsundsechzig", 70 to "siebzig", 77 to "siebenundsiebzig", 99 to "neunundneunzig", 100 to "hundert", 101 to "hunderteins",
        120 to "hundertzwanzig", 121 to "hunderteinundzwanzig", 200 to "zweihundert", 245 to "zweihundertfünfundvierzig", 999 to "neunhundertneunundneunzig",
        1000 to "tausend", 1001 to "tausendeins", 1100 to "tausendeinhundert", 1989 to "tausendneunhundertneunundachtzig", 2000 to "zweitausend",
        2026 to "zweitausendsechsundzwanzig", 2150 to "zweitausendeinhundertfünfzig", 9999 to "neuntausendneunhundertneunundneunzig",
    )

    @Test fun `the words of the hand-checked table`() {
        for ((n, w) in table) assertEquals("$n", w, de.words(n))
        assertEquals("einundzwanzigtausend", SpokenNumbers.german(21_000))
        assertEquals("hunderttausend", SpokenNumbers.german(100_000))
    }

    @Test fun `speech's forms up to 999 are the same numbers, hundert as well as einhundert`() {
        for ((n, w) in table) if (n in 0..99) assertEquals("$n", w, SpokenNumbers.forms(n, Lang.DE).first())
        assertEquals("einhundertzwanzig", SpokenNumbers.forms(120, Lang.DE).first())
        assertTrue("hundertzwanzig" in SpokenNumbers.forms(120, Lang.DE))
        assertEquals("hundertzwanzig", SpokenNumbers.inWords("120", Lang.DE, expected = "hundertzwanzig"))
    }

    @Test fun `every number 0 to 9999 reads back and its other forms are no other number's`() {
        for (n in 0..9999) {
            assertEquals("$n", n, de.parse(de.words(n)))
            for (w in de.also(n)) assertEquals(w, n, de.parse(w))
        }
        assertTrue("einhundert" in de.also(100))
        assertTrue("hundertundeins" in de.also(101))
        assertTrue("eintausendeinhundert" in de.also(1100))
        assertTrue("neunzehnhundertneunundachtzig" in de.also(1989))
        assertTrue("zwo" in de.also(2))
        assertNull(de.parse("zwanzigsieben"))
        assertEquals(27, de.parseSwapped("zwanzig sieben"))
        assertEquals(127, de.parseSwapped("hundertzwanzigundsieben"))
    }

    private fun state(unit: String? = "de/zahlen") = GameState(
        age = Age.ZASELEK,
        course = CourseProgress(units = if (unit != null) mapOf(unit to UnitProgress(intro = 6, introDone = true)) else emptyMap()),
    )

    @Test fun `the unit zahlen opens it, Italian's numeri doesn't`() {
        assertTrue(NumbersPractice.open(state(), de))
        assertNull(NumbersPractice.lock(state(), de))
        assertFalse(NumbersPractice.open(state(null), de))
        assertFalse(NumbersPractice.open(state("it/numeri"), de))
        assertEquals(NumbersPractice.Lock.INTRO, NumbersPractice.lock(state(null), de))
        assertFalse(NumbersPractice.open(state(), NumberTongue.IT))
        assertNotNull(NumberTongue.of("de"))
        assertEquals("zahlen", de.unit)
    }

    @Test fun `the clock waits for uhrzeit, the dates for am-ersten-mai's ordinals`() {
        val first = NumbersPractice.modes(canSay = true, canListen = true, introduced = setOf("zahlen"), tongue = de)
        assertFalse(NumberMode.DATE in first || NumberMode.TIME_WRITE in first || NumberMode.TIME_HEAR in first)
        val clock = NumbersPractice.modes(canSay = true, canListen = false, introduced = setOf("zahlen", "uhrzeit"), tongue = de)
        assertTrue(NumberMode.TIME_WRITE in clock && NumberMode.TIME_HEAR in clock)
        assertFalse(NumberMode.DATE in clock)
        val dates = NumbersPractice.modes(canSay = true, canListen = false, introduced = setOf("zahlen", "uhrzeit", "ordinalzahlen"), tongue = de)
        assertTrue(NumberMode.DATE in dates)
        assertEquals("am-ersten-mai", de.dateUnit)
    }

    private fun graded(mode: NumberMode, n: Int, answer: String, month: Int? = null): NumberGraded =
        NumbersPractice.grade(NumbersPractice.exercise(NumberTask(mode, n, month), de) as Exercise.Numeral, answer)

    @Test fun `a number heard or read, English's order against German's`() {
        assertEquals(Verdict.CORRECT, graded(NumberMode.HEAR, 27, "27").verdict)
        val swapped = graded(NumberMode.HEAR, 27, "72")
        assertEquals(Verdict.WRONG, swapped.verdict)
        assertEquals(NumberKind.ORDER, swapped.kind)
        assertTrue(swapped.why!!, swapped.why!!.contains("siebenundzwanzig") && swapped.why!!.contains("ones first"))
        assertEquals(NumberKind.LISTENING, graded(NumberMode.HEAR, 27, "26").kind)
        assertEquals(NumberKind.READING, graded(NumberMode.READ, 27, "26").kind)
    }

    @Test fun `a number's word in one word, the umlaut, the tens first, the learner's own words, another number`() {
        assertEquals(Verdict.CORRECT, graded(NumberMode.WRITE, 27, "siebenundzwanzig").verdict)
        assertEquals(Verdict.CORRECT, graded(NumberMode.WRITE, 120, "hundertzwanzig").verdict)
        assertEquals(Verdict.CORRECT, graded(NumberMode.WRITE, 120, "einhundertzwanzig").verdict)
        assertEquals(Verdict.CORRECT, graded(NumberMode.WRITE, 1989, "neunzehnhundertneunundachtzig").verdict)
        assertEquals(Verdict.ALMOST, graded(NumberMode.WRITE, 27, "sieben und zwanzig").verdict)
        assertEquals(Verdict.ALMOST, graded(NumberMode.WRITE, 25, "fuenfundzwanzig").verdict)
        assertEquals(Verdict.ALMOST, graded(NumberMode.WRITE, 30, "dreissig").verdict)
        assertEquals(Verdict.ALMOST, graded(NumberMode.WRITE, 19, "neunzen").verdict)
        val tensFirst = graded(NumberMode.WRITE, 27, "zwanzigsieben")
        assertEquals(Verdict.WRONG, tensFirst.verdict)
        assertEquals(NumberKind.ORDER, tensFirst.kind)
        assertEquals(NumberKind.ORDER, graded(NumberMode.WRITE, 27, "zwanzig und sieben").kind)
        // eins, sechs and sieben kept whole are the learner's own words, not slips
        for ((n, w) in listOf(21 to "einsundzwanzig", 16 to "sechszehn", 70 to "siebenzig", 17 to "siebenzehn")) {
            assertEquals(w, Verdict.WRONG, graded(NumberMode.WRITE, n, w).verdict)
            assertEquals(w, NumberKind.SPELLING, graded(NumberMode.WRITE, n, w).kind)
        }
        // another number's word is never a slip
        assertEquals(Verdict.WRONG, graded(NumberMode.WRITE, 27, "achtundzwanzig").verdict)
        assertEquals(Verdict.WRONG, graded(NumberMode.WRITE, 12, "elf").verdict)
    }

    @Test fun `a date's day is the ordinal after der, the special four, -ste from 20, Jänner or Januar`() {
        assertEquals("Heute ist der ___ Mai. (15.)", de.dateFrame(15, 5))
        assertEquals("Heute ist der erste Jänner.", de.dateSaid(1, 1))
        assertEquals("15. Mai", NumbersPractice.shownOf(NumberTask(NumberMode.DATE, 15, 5), de))
        for (a in listOf("fünfzehnte", "der fünfzehnte", "fünfzehnte Mai", "der fünfzehnte Mai", "Heute ist der fünfzehnte Mai.")) {
            assertEquals(a, Verdict.CORRECT, graded(NumberMode.DATE, 15, a, 5).verdict)
        }
        for ((day, a) in listOf(1 to "erste", 3 to "dritte", 7 to "siebte", 7 to "siebente", 8 to "achte", 20 to "zwanzigste", 21 to "einundzwanzigste", 30 to "dreißigste")) {
            assertEquals(a, Verdict.CORRECT, graded(NumberMode.DATE, day, a, 3).verdict)
        }
        assertEquals(Verdict.CORRECT, graded(NumberMode.DATE, 1, "der erste Januar", 1).verdict)
        assertEquals(Verdict.CORRECT, graded(NumberMode.DATE, 2, "der zweite Feber", 2).verdict)
        // the rule applied where it doesn't hold: wrong, of the date's kind
        for ((day, a) in listOf(1 to "einste", 3 to "dreite", 8 to "achtte", 20 to "zwanzigte", 10 to "zehnste")) {
            val g = graded(NumberMode.DATE, day, a, 5)
            assertEquals(a, Verdict.WRONG, g.verdict)
            assertEquals(a, NumberKind.DATE, g.kind)
        }
        assertEquals(Verdict.WRONG, graded(NumberMode.DATE, 13, "zwölfte", 5).verdict)
    }

    @Test fun `the clock with um, halb towards the next hour`() {
        assertEquals("um halb sechs", de.clockWords(530))
        assertEquals("um eins", de.clockWords(100))
        assertEquals("um halb eins", de.clockWords(1230))
        assertEquals("um zwölf", de.clockWords(1200))
        assertEquals(Verdict.CORRECT, graded(NumberMode.TIME_HEAR, 530, "5:30").verdict)
        assertEquals(Verdict.CORRECT, graded(NumberMode.TIME_HEAR, 530, "17:30").verdict)
        val english = graded(NumberMode.TIME_HEAR, 530, "6:30")
        assertEquals(Verdict.WRONG, english.verdict)
        assertEquals(NumberKind.TIME_HALF, english.kind)
        assertEquals(NumberKind.TIME, graded(NumberMode.TIME_HEAR, 530, "8:30").kind)
        for (a in listOf("um halb sechs", "halb sechs", "um fünf Uhr dreißig", "fünf Uhr dreißig", "um siebzehn Uhr dreißig")) {
            assertEquals(a, Verdict.CORRECT, graded(NumberMode.TIME_WRITE, 530, a).verdict)
        }
        for (a in listOf("um eins", "um ein Uhr", "ein Uhr", "um dreizehn Uhr")) assertEquals(a, Verdict.CORRECT, graded(NumberMode.TIME_WRITE, 100, a).verdict)
        assertEquals(Verdict.CORRECT, graded(NumberMode.TIME_WRITE, 1200, "zu Mittag").verdict)
        val halfFive = graded(NumberMode.TIME_WRITE, 530, "um halb fünf")
        assertEquals(Verdict.WRONG, halfFive.verdict)
        assertEquals(NumberKind.TIME_HALF, halfFive.kind)
        // another hour is never a slip
        assertEquals(Verdict.WRONG, graded(NumberMode.TIME_WRITE, 700, "um sechs").verdict)
        assertEquals(Verdict.WRONG, graded(NumberMode.TIME_WRITE, 1200, "um elf").verdict)
    }

    @Test fun `each why says what is German's own, in the base`() {
        assertTrue(de.numberWhy(27), de.numberWhy(27).contains("ones first") && de.numberWhy(27).contains("und"))
        assertTrue(de.numberWhy(21), de.numberWhy(21).contains("not eins"))
        assertTrue(de.numberWhy(16), de.numberWhy(16).contains("-zehn"))
        assertTrue(de.numberWhy(30), de.numberWhy(30).contains("dreißig"))
        assertTrue(de.numberWhy(245), de.numberWhy(245).contains("one word"))
        assertTrue(de.clockWhy(530), de.clockWhy(530).contains("next hour") && de.clockWhy(530).contains("6"))
        assertTrue(de.dateWhy(15, 5), de.dateWhy(15, 5).contains("der fünfzehnte"))
        for (k in NumberKind.entries) assertFalse(de.describe(k), de.describe(k).contains("Slovene") || de.describe(k).contains("Italian number"))
    }

    @Test fun `a learner from Slovene reads the whys in Slovene`() {
        L10n.pair = LangPair(Lang.DE, Lang.SL)
        assertTrue(de.numberWhy(27), de.numberWhy(27).contains("najprej enice"))
        assertTrue(de.clockWhy(530), de.clockWhy(530).contains("naslednji uri"))
    }

    @Test fun `what the tutor hears says its language`() {
        val task = NumberTask(NumberMode.WRITE, 27)
        val ex = NumbersPractice.exercise(task, de)
        val rec = NumbersPractice.record(null, true, listOf(task), listOf(Verdict.WRONG), java.time.LocalDate.of(2026, 11, 3))
        val wire = NumbersPractice.wire(rec, true, listOf(task), listOf(ex), listOf("zwanzigsieben" to NumberKind.ORDER), listOf(Verdict.WRONG), setOf(NumberMode.WRITE), java.time.LocalDate.of(2026, 11, 3), de)
        assertTrue(wire.toString(), wire.toString().contains("\"language\":\"de\"") && wire.toString().contains("numbers_order") && wire.toString().contains("German says the ones first"))
    }
}
